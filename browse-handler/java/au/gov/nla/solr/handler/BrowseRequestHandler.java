//
// Author: Mark Triggs <mtriggs@nla.gov.au>
//


package au.gov.nla.solr.handler;


import org.apache.lucene.index.*;
import org.apache.solr.handler.*;
import org.apache.solr.request.*;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import java.io.*;
import java.util.*;
import java.net.URL;
import java.sql.*;

import au.gov.nla.util.*;
import org.apache.lucene.search.*;
import org.apache.lucene.document.*;
import java.util.logging.Logger;


class Log
{
    private static Logger log ()
    {
        // Caller's class
        return Logger.getLogger
            (new Throwable ().getStackTrace ()[2].getClassName ());
    }


    public static void info (String s) { log ().info (s); }
}



class HeadingSlice
{
    public List<String> headings = new LinkedList<String> ();
    public int total;
}



class HeadingsDB
{
    Connection db;
    String path;
    long dbVersion;
    int totalCount;


    public HeadingsDB (String path) throws Exception
    {
        this.path = path;

        openDB ();
    }


    private void openDB () throws Exception
    {
        Class.forName ("org.sqlite.JDBC");

        db = DriverManager.getConnection ("jdbc:sqlite:" + path);
        dbVersion = currentVersion ();

        PreparedStatement countStmnt = db.prepareStatement (
            "select count(1) as count from headings");

        ResultSet rs = countStmnt.executeQuery ();
        rs.next ();

        totalCount = rs.getInt ("count");

        rs.close ();
        countStmnt.close ();
    }


    private long currentVersion ()
    {
        return new File (path).lastModified ();
    }


    public void reopenIfUpdated () throws Exception
    {
        if (dbVersion != currentVersion ()) {
            Log.info ("Reopening HeadingsDB");
            openDB ();
        }
    }


    public int getHeadingStart (String from) throws Exception
    {
        PreparedStatement rowStmnt = db.prepareStatement (
            "select rowid from headings " +
            "where key >= ? " +
            "order by key " +
            "limit 1");

        rowStmnt.setString (1, from);

        ResultSet rs = rowStmnt.executeQuery ();

        if (rs.next ()) {
        return rs.getInt ("rowid");
        } else {
            return totalCount + 1;   // past the end
        }
    }


    public HeadingSlice getHeadings (int rowid,
                                     int rows)
        throws Exception
    {
        HeadingSlice result = new HeadingSlice ();

        PreparedStatement rowStmnt = db.prepareStatement (
            String.format ("select * from headings " +
                           "where rowid >= ? " +
                           "order by rowid " +
                           "limit %d ",
                           rows)
            );

        rowStmnt.setInt (1, rowid);

        ResultSet rs = null;

        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                rs = rowStmnt.executeQuery ();
                break;
            } catch (SQLException e) {
                Log.info ("Retry number " + attempt + "...");
                Thread.sleep (50);
            }
        }

        if (rs == null) {
            return result;
        }

        while (rs.next ()) {
            result.headings.add (rs.getString ("heading"));
        }

        rs.close ();
        rowStmnt.close ();

        result.total = (totalCount - rowid) + 1;

        return result;
    }
}



class LuceneDB
{
    static Map<String,LuceneDB> dbs = new HashMap<String,LuceneDB> ();

    IndexSearcher searcher;
    String dbpath;
    long currentVersion = -1;


    public synchronized static LuceneDB getOrCreate (String path)
        throws Exception
    {
        if (!dbs.containsKey (path)) {
            LuceneDB db = new LuceneDB (path);
            dbs.put (path, db);
        }

        return dbs.get (path);
    }


    public synchronized static void reopenAllIfUpdated ()
        throws Exception
    {
        for (LuceneDB db : dbs.values ()) {
            db.reopenIfUpdated ();
        }
    }


    public LuceneDB (String path) throws Exception
    {
        this.dbpath = path;
    }


    private void openSearcher () throws Exception
    {
        if (searcher != null) {
            searcher.close ();
        }

        searcher = new IndexSearcher (dbpath);
        currentVersion = indexVersion ();
    }


    public Hits search (Query q) throws Exception
    {
        return searcher.search (q);
    }

    private long indexVersion ()
    {
        return new File (dbpath + "/segments.gen").lastModified ();
    }


    private boolean isDBUpdated ()
    {
        return (currentVersion != indexVersion ());
    }


    public synchronized void reopenIfUpdated () throws Exception
    {
        if (isDBUpdated ()) {
            openSearcher ();
            Log.info ("Reopened " + searcher + " (" + dbpath + ")");
        }
    }
}



class AuthDB
{
    private LuceneDB db;
    private String preferredHeadingField;
    private String useInsteadHeadingField;
    private String seeAlsoHeadingField;
    private String scopeNoteField;

    public AuthDB (String path,
                   String preferredField,
                   String useInsteadField,
                   String seeAlsoField,
                   String noteField)
        throws Exception
    {
        db = LuceneDB.getOrCreate (path);
        preferredHeadingField = preferredField;
        useInsteadHeadingField = useInsteadField;
        seeAlsoHeadingField = seeAlsoField;
        scopeNoteField = noteField;
    }


    private List<String> docValues (Document doc, String field)
    {
        String values[] = doc.getValues (field);

        if (values == null) {
            values = new String[] {};
        }

        return Arrays.asList (values);
    }


    public void reopenIfUpdated () throws Exception
    {
        db.reopenIfUpdated ();
    }


    public Document getAuthorityRecord (String heading)
        throws Exception
    {
        Hits results = (db.search
                        (new TermQuery (new Term (preferredHeadingField,
                                                  heading))));

        if (results.length () > 0) {
            return results.doc (0);
        } else {
            return null;
        }
    }


    public List<Document> getPreferredHeadings (String heading)
        throws Exception
    {
        Hits results = (db.search
                        (new TermQuery (new Term (useInsteadHeadingField,
                                                  heading))));

        List<Document> result = new Vector<Document> ();

        Iterator it = (Iterator)results.iterator ();

        while (it.hasNext ()) {
            Hit hit = (Hit)it.next ();
            result.add (hit.getDocument ());
        }

        return result;
    }


    public Map<String, List<String>> getFields (String heading)
        throws Exception
    {
        Document authInfo = getAuthorityRecord (heading);

        Map<String, List<String>> itemValues =
            new HashMap<String,List<String>> ();

        itemValues.put ("seeAlso", new ArrayList<String>());
        itemValues.put ("useInstead", new ArrayList<String>());
        itemValues.put ("note", new ArrayList<String>());

        if (authInfo != null) {
            for (String value : docValues (authInfo, seeAlsoHeadingField)) {
                itemValues.get ("seeAlso").add (value);
            }

            for (String value : docValues (authInfo, scopeNoteField)) {
                itemValues.get ("note").add (value);
            }
        } else {
            List<Document> preferredHeadings =
                getPreferredHeadings (heading);

            for (Document doc : preferredHeadings) {
                for (String value : docValues (doc, preferredHeadingField)) {
                    itemValues.get ("useInstead").add (value);
                }
            }
        }

        return itemValues;
    }
}



class BibDB
{
    private IndexSearcher db;
    private String field;


    public BibDB (IndexSearcher searcher, String field) throws Exception
    {
        db = searcher;
        this.field = field;
    }


    public int recordCount (String heading)
        throws Exception
    {
        TermQuery q = new TermQuery (new Term (field, heading));

        Hits results = db.search (q);

        return results.length ();
    }


    public List<String> matchingIDs (String heading)
        throws Exception
    {
        TermQuery q = new TermQuery (new Term (field, heading));

        Hits results = db.search (q);

        List<String> ids = new Vector<String> ();

        for (int i = 0; i < results.length (); i++) {
            Document doc = results.doc (i);
            String[] vals = doc.getValues ("id");

            ids.add (vals[0]);
        }

        return ids;
    }
}



class BrowseList
{
    public int totalCount;
    public List<BrowseItem> items = new LinkedList<BrowseItem> ();


    public List<Map<String, Object>> asMap ()
    {
        List<Map<String, Object>> result = new LinkedList<Map<String, Object>> ();

        for (BrowseItem item : items) {
            result.add (item.asMap ());
        }

        return result;
    }
}



class BrowseItem
{
    public List<String> seeAlso = new LinkedList<String> ();
    public List<String> useInstead = new LinkedList<String> ();
    public String note = "";
    public String heading;
    public List<String> ids;
    int count;


    public BrowseItem (String heading)
    {
        this.heading = heading;
    }


    public Map<String, Object> asMap ()
    {
        Map<String, Object> result = new HashMap<String, Object> ();

        result.put ("heading", heading);
        result.put ("seeAlso", seeAlso);
        result.put ("useInstead", useInstead);
        result.put ("note", note);
        result.put ("count", new Integer (count));
        result.put ("ids", ids);

        return result;
    }
}




class Browse
{
    private HeadingsDB headingsDB;
    private AuthDB authDB;
    private BibDB bibDB;


    public Browse (HeadingsDB headings, AuthDB auth)
    {
        headingsDB = headings;
        authDB = auth;
    }


    public void setBibDB (BibDB b)
    {
        this.bibDB = b;
    }


    public synchronized void reopenDatabasesIfUpdated () throws Exception
    {
        headingsDB.reopenIfUpdated ();
        authDB.reopenIfUpdated ();
    }


    private void populateItem (BrowseItem item) throws Exception
    {
        Log.info ("Populating: " + item.heading);

        List<String> ids = bibDB.matchingIDs (item.heading);
        item.ids = ids;
        item.count = ids.size ();

        Map<String, List<String>> fields = authDB.getFields (item.heading);

        for (String value : fields.get ("seeAlso")) {
            if (bibDB.recordCount (value) > 0) {
                item.seeAlso.add (value);
            }
        }

        for (String value : fields.get ("useInstead")) {
            if (bibDB.recordCount (value) > 0) {
                item.useInstead.add (value);
            }
        }

        for (String value : fields.get ("note")) {
            item.note = value;
        }
    }


    public int getId (String from) throws Exception
    {
        return headingsDB.getHeadingStart (from);
    }


    public BrowseList getList (int rowid, int offset, int rows)
        throws Exception
    {
        BrowseList result = new BrowseList ();

        HeadingSlice h = headingsDB.getHeadings (Math.max (0, rowid + offset),
                                                 rows);

        result.totalCount = h.total;

        for (String heading : h.headings) {
            BrowseItem item = new BrowseItem (heading);

            populateItem (item);

            result.items.add (item);
        }

        return result;
    }
}



class BrowseSource
{
    public String DBpath;
    public String field;
    public String ignoreDiacritics;
    public String dropChars;

    public Browse browse;


    public BrowseSource (String DBpath,
                         String field,
                         String ignoreDiacritics,
                         String dropChars)
    {
        this.DBpath = DBpath;
        this.field = field;
        this.ignoreDiacritics = ignoreDiacritics;
        this.dropChars = dropChars;
    }
}



public class BrowseRequestHandler extends RequestHandlerBase
{
    private String authPath = null;
    private String bibPath = null;

    private Map<String,BrowseSource> sources = new HashMap<String,BrowseSource> ();

    private SolrParams solrParams;


    private String asAbsFile (String s)
    {
        File f = new File (s);

        if (!f.isAbsolute ()) {
            return (new File (new File (System.getenv ("BROWSE_HOME")),
                             f.getPath ()).getPath ());
        } else {
            return f.getPath ();
        }
    }


    public void init (NamedList args)
    {
        super.init (args);

        solrParams = SolrParams.toSolrParams (args);

        authPath = asAbsFile (solrParams.get ("authIndexPath"));
        bibPath = asAbsFile (solrParams.get ("bibIndexPath"));

        sources = new HashMap<String, BrowseSource> ();

        for (String source : Arrays.asList (solrParams.get
                                            ("sources").split (","))) {
            NamedList<String> entry = (NamedList<String>)args.get (source);

            sources.put (source,
                         new BrowseSource (entry.get ("DBpath"),
                                           entry.get ("field"),
                                           entry.get ("ignoreDiacritics"),
                                           entry.get ("dropChars")));
        }
    }


    private int asInt (String s)
    {
        int value;
        try {
            return new Integer (s).intValue ();
        } catch (NumberFormatException e) {
            return 0;
        }
    }


    private String handle_diacritics (String s)
    {
        DiacriticStripper ds = new DiacriticStripper ();

        return ds.fix (s);
    }


    private String clean (String s, boolean ignoreDiacritics, String dropChars)
    {
        String cleaned = s;

        if (dropChars != null) {
            for (int i = 0; i < dropChars.length (); i++) {
                cleaned = cleaned.replace (String.valueOf
                                           (dropChars.charAt (i)),
                                           "");
            }
        }

        cleaned = cleaned.replaceAll ("[\\(\\)]", "");
        cleaned = cleaned.replaceAll ("-$", "");
        cleaned = cleaned.replaceAll ("-", " ");
        cleaned = cleaned.replaceAll (" +", " ");

        if (!ignoreDiacritics) {
            cleaned = handle_diacritics (cleaned);
        }

        return cleaned.toLowerCase ();
    }



    public void handleRequestBody (SolrQueryRequest req,
                                   SolrQueryResponse rsp)
        throws Exception
    {
        SolrParams p = req.getParams ();

        if (p.get ("reopen") != null) {
            LuceneDB.reopenAllIfUpdated ();
            return;
        }


        String sourceName = p.get ("source");
        String from = p.get ("from");

        int rowid = 1;
        if (p.get ("rowid") != null) {
            rowid = asInt (p.get ("rowid"));
        }

        int rows = asInt (p.get ("rows"));

        int offset = (p.get ("offset") != null) ? asInt (p.get ("offset")) : 0;

        if (rows < 0) {
            throw new Exception ("Invalid value for parameter: rows");
        }

        if (sourceName == null || !sources.containsKey (sourceName)) {
            throw new Exception ("Need a (valid) source parameter.");
        }


        BrowseSource source = sources.get (sourceName);

        if (source.browse == null) {
            source.browse = (new Browse
                             (new HeadingsDB (source.DBpath),
                              new AuthDB
                              (authPath,
                               solrParams.get ("preferredHeadingField"),
                               solrParams.get ("useInsteadHeadingField"),
                               solrParams.get ("seeAlsoHeadingField"),
                               solrParams.get ("scopeNoteField"))));
        }

        source.browse.setBibDB (new BibDB (req.getSearcher (),
                                           source.field));
        source.browse.reopenDatabasesIfUpdated ();

        if (from != null) {
            rowid = (source.browse.getId
                     (clean (from,
                             (source.ignoreDiacritics != null &&
                              source.ignoreDiacritics.equals ("yes")),
                             source.dropChars)));
        }


        Log.info ("Browsing from: " + rowid);

        BrowseList list = source.browse.getList (rowid, offset, rows);

        Map<String,Object> result = new HashMap<String, Object> ();

        result.put ("totalCount", list.totalCount);
        result.put ("items", list.asMap ());
        result.put ("startRow", rowid);
        result.put ("offset", offset);

        rsp.add ("Browse", result);
    }


    //////////////////////// SolrInfoMBeans methods //////////////////////

    public String getVersion () {
        return "$Revision: 0.1 $";
    }

    public String getDescription () {
        return "NLA browse handler";
    }

    public String getSourceId () {
        return "";
    }

    public String getSource () {
        return "";
    }

    public URL[] getDocs () {
        return null;
    }
}