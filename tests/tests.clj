;; java -Dfile.encoding=UTF-8 -cp 'build/common:build/browse-indexing:build/browse-handler:libs/*:tmplibs/WEB-INF/lib/*' clojure.main tests/tests.clj 2>/dev/null

(ns tests
  (:use [clojure.java.io :as jio]
        [clojure.test :as test])
  (:import (org.apache.lucene.analysis.standard StandardAnalyzer)
           (org.apache.lucene.document Document Field Field$Store Field$Index)
           (org.apache.lucene.store FSDirectory)
           (org.apache.lucene.index IndexWriter IndexWriter$MaxFieldLength)
           (org.apache.lucene.util Version)
           (java.io File)
           (com.google.common.io Files)

           (org.apache.solr.core CoreContainer CoreContainer$Initializer)
           (org.apache.solr.client.solrj.embedded EmbeddedSolrServer)
           (org.apache.solr.client.solrj SolrQuery)))



(defn heading-document [field-name heading]
  (let [doc (Document.)]
    (.add doc (Field. field-name (:heading heading)
                      org.apache.lucene.document.Field$Store/YES
                      org.apache.lucene.document.Field$Index/NOT_ANALYZED))
    (when (:sort-key heading)
      (.add doc (Field. (str "sort-" field-name) (:sort-key heading)
                        org.apache.lucene.document.Field$Store/YES
                        org.apache.lucene.document.Field$Index/NOT_ANALYZED)))
    doc))


(defn populate-index [headings field-name index-file]
  (with-open [iw (IndexWriter.
                  (FSDirectory/open (jio/file index-file))
                  (StandardAnalyzer. Version/LUCENE_31)
                  IndexWriter$MaxFieldLength/UNLIMITED)]
    (doseq [heading headings]
      (.addDocument iw (heading-document field-name heading)))))




(defn do-browse [server browse-type & [from]]
  (mapv #(.get % "heading")
        (-> (.query server
                    (doto (SolrQuery.)
                      (.setQueryType "/browse")
                      (.setParam "source" (into-array [browse-type]))
                      (.setParam "rows" (into-array ["100"]))))
            .getResponse
            (.get "Browse")
            (.get "items"))))


(def test-browses [{:name "author"
                    :headings  [{:heading "AAA"}
                                {:heading "Äardvark"}
                                {:heading "Apple"}
                                {:heading "Orange"}
                                {:heading "Banana"}
                                {:heading "grapefruit"}]}

                   {:name "title"
                    :headings  [{:heading "AAA"
                                 :sort-key "AAA"}
                                {:heading "Äardvark"
                                 :sort-key "Äardvark"}
                                {:heading "Apple"
                                 :sort-key "Apple"}
                                {:heading "Orange"
                                 :sort-key "Orange"}
                                {:heading "Banana"
                                 :sort-key "Banana"}
                                {:heading "grapefruit"
                                 :sort-key "grapefruit"}]}])


(defn main []
  (let [tmpdir (Files/createTempDir)
        authority-index (file (doto (file tmpdir "authority")
                                .mkdirs)
                              "index")
        bib-index (file tmpdir "index")]
    (try

      (populate-index [] "authority" authority-index)

      (doseq [browse test-browses]
        (println "Loading browse headings:" (:name browse))
        (let [tmp-headings (File/createTempFile "nla-browse-handler-tests" "")
              tmpdb (file tmpdir (str (:name browse) "_browse.db"))]
          (try
            (populate-index (:headings browse) (:name browse) bib-index)

            (doseq [prop ["bibleech" "sortfield" "valuefield"]]
              (System/clearProperty prop))

            (when (:sort-key (first (:headings browse)))
              (System/setProperty "bibleech" "StoredFieldLeech")
              (System/setProperty "sortfield" (str "sort-" (:name browse)))
              (System/setProperty "valuefield" (:name browse))
              )

            (PrintBrowseHeadings/main (into-array [(str bib-index)
                                                   (:name browse)
                                                   (str tmp-headings)]) )

            (CreateBrowseSQLite/main (into-array [(str tmp-headings)
                                                  (str tmpdb)]))
            (finally
             (.delete tmp-headings)))))


      (System/setProperty "solr.solr.home" (str tmpdir))

      (.mkdir (file tmpdir "conf"))

      (Files/copy (file "tests/solr/solrconfig.xml")
                  (file tmpdir "conf" "solrconfig.xml"))

      (Files/copy (file "tests/solr/schema.xml")
                  (file tmpdir "conf" "schema.xml"))


      (let [core (.initialize (new CoreContainer$Initializer))
            server (EmbeddedSolrServer. core "")]

        (println "\n====== Running tests ======\n")
        ;; Browse request!  finally...
        (is (=
             (do-browse server "author")
             ["AAA" "Äardvark" "Apple" "Banana" "grapefruit" "Orange"]))

        (is (=
             (do-browse server "title")
             ["AAA" "Äardvark" "Apple" "Banana" "grapefruit" "Orange"]))

        (println "\n====== Tests complete ======\n")
        (.shutdown core)
        )



      (finally
       (Files/deleteRecursively tmpdir)
       (shutdown-agents)))))


(main)
