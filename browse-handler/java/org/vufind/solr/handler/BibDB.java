package org.vufind.solr.handler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CollectionTerminatedException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TotalHitCountCollector;

/**
 *
 * Interface to the Solr biblio db
 *
 */
public class BibDB
{
    private IndexSearcher db;
    private String field;

    public BibDB(IndexSearcher searcher, String field) throws Exception
    {
        db = searcher;
        this.field = field;
    }


    public int recordCount(String heading)
    throws Exception
    {
        TermQuery q = new TermQuery(new Term(field, heading));

        Log.info("Searching '" + field + "' for '" + "'" + heading + "'");

        TotalHitCountCollector counter = new TotalHitCountCollector();
        db.search(q, counter);

        Log.info("Hits: " + counter.getTotalHits());

        return counter.getTotalHits();
    }


    /**
     *
     * Function to retireve the doc ids when there is a building limit
     * This retrieves the doc ids for an individual heading
     *
     * Need to add a filter query to limit the results from Solr
     *
     * Includes functionality to retrieve additional info
     * like titles for call numbers, possibly ISBNs
     *
     * @param heading  string of the heading to use for finding matching docs
     * @param extras   colon-separated string of extra Solr fields to return
     *                 for use in the browse display
     * @return         return a map of Solr ids and extra bib info
     */
    public Map<String, List<Collection<String>>> matchingIDs(String heading, String extras)
    throws Exception
    {
        TermQuery q = new TermQuery(new Term(field, heading));

        // bibinfo values are List<Collection> because some extra fields
        // may be multi-valued.
        // Note: it may be time for bibinfo to become a class...
        final Map<String, List<Collection<String>>> bibinfo = new HashMap<> ();
        bibinfo.put("ids", new ArrayList<Collection<String>> ());
        final String[] bibExtras = extras.split(":");
        for (String bibField : bibExtras) {
            bibinfo.put(bibField, new ArrayList<Collection<String>> ());
        }

        db.search(q, new SimpleCollector() {
            private LeafReaderContext context;

            public void setScorer(Scorer scorer) {
            }

            public boolean acceptsDocsOutOfOrder() {
                return true;
            }

            public boolean needsScores() {
                return false;
            }

            public void doSetNextReader(LeafReaderContext context) {
                this.context = context;
            }


            public void collect(int docnum) {
                int docid = docnum + context.docBase;
                try {
                    Document doc = db.getIndexReader().document(docid);

                    String[] vals = doc.getValues("id");
                    Collection<String> id = new HashSet<> ();
                    id.add(vals[0]);
                    bibinfo.get("ids").add(id);
                    for (String bibField : bibExtras) {
                        vals = doc.getValues(bibField);
                        if (vals.length > 0) {
                            Collection<String> valSet = new LinkedHashSet<> ();
                            for (String val : vals) {
                                valSet.add(val);
                            }
                            bibinfo.get(bibField).add(valSet);
                        }
                    }
                } catch (org.apache.lucene.index.CorruptIndexException e) {
                    Log.info("CORRUPT INDEX EXCEPTION.  EEK! - " + e);
                } catch (Exception e) {
                    Log.info("Exception thrown: " + e);
                }

            }
        });

        return bibinfo;
    }
}
