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

        rs.next ();

        return rs.getInt ("rowid");
    }


    public HeadingSlice getHeadings (int rowid, int rows)
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

        ResultSet rs = rowStmnt.executeQuery ();

        while (rs.next ()) {
            result.headings.add (rs.getString ("heading"));
        }


        PreparedStatement countStmnt = db.prepareStatement (
            "select count(1) as count from headings where rowid >= ?");

        countStmnt.setInt (1, rowid);

        rs = countStmnt.executeQuery ();
        rs.next ();
        result.total = rs.getInt ("count");


        return result;
    }
}


abstract class LuceneDB
{
    IndexSearcher searcher;
    String dbpath;
    long currentVersion = -1;

    public LuceneDB (String path) throws Exception
    {
        dbpath = path;
        openSearcher ();
    }


    private void openSearcher () throws Exception
    {
        if (searcher != null) {
            searcher.close ();
        }

        searcher = new IndexSearcher (dbpath);
        currentVersion = indexVersion ();
    }


    private long indexVersion ()
    {
        return new File (dbpath + "/segments.gen").lastModified ();
    }


    private boolean isDBUpdated ()
    {
        return (currentVersion != indexVersion ());
    }


    public void reopenIfUpdated () throws Exception
    {
        if (isDBUpdated ()) {
            openSearcher ();
            Log.info ("Reopened " + searcher + " (" + dbpath + ")");
        }
    }
}



class AuthDB extends LuceneDB
{
    public AuthDB (String path) throws Exception
    {
        super (path);
    }

    public Document getAuthorityRecord (String heading)
        throws Exception
    {
        Hits results = (searcher.search
                        (new TermQuery (new Term ("preferred", heading))));

        if (results.length () > 0) {
            return results.doc (0);
        } else {
            return null;
        }
    }

    public List<Document> getPreferredHeadings (String heading)
        throws Exception
    {
        Hits results = (searcher.search
                        (new TermQuery (new Term ("insteadOf", heading))));

        List<Document> result = new Vector<Document> ();

        Iterator it = (Iterator)results.iterator ();

        while (it.hasNext ()) {
            Hit hit = (Hit)it.next ();
            result.add (hit.getDocument ());
        }

        return result;
    }
}


class BibDB extends LuceneDB
{
    private String field;

    public BibDB (String path, String field) throws Exception
    {
        super (path);
        this.field = field;
    }

    public int recordCount (String heading)
        throws Exception
    {
        TermQuery q = new TermQuery (new Term (field, heading));

        Hits results = searcher.search (q);

        return results.length ();
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

        return result;
    }
}




class Browse
{
    private HeadingsDB headingsDB;
    private AuthDB authDB;
    private BibDB bibDB;

    public Browse (HeadingsDB headings, AuthDB auth, BibDB bibs)
    {
        headingsDB = headings;
        authDB = auth;
        bibDB = bibs;
    }


    public synchronized void reopenDatabasesIfUpdated () throws Exception
    {
        headingsDB.reopenIfUpdated ();
        authDB.reopenIfUpdated ();
        bibDB.reopenIfUpdated ();
    }


    private List<String> docValues (Document doc, String field)
    {
        String values[] = doc.getValues (field);

        if (values == null) {
            values = new String[] {};
        }

        return Arrays.asList (values);
    }


    private void populateItem (BrowseItem item) throws Exception
    {
        Log.info ("Populating: " + item.heading);

        item.count = bibDB.recordCount (item.heading);

        Document authInfo = authDB.getAuthorityRecord (item.heading);

        if (authInfo != null) {
            for (String value : docValues (authInfo, "seeAlso")) {
                if (bibDB.recordCount (value) > 0) {
                    Log.info ("Adding: " + value);
                    item.seeAlso.add (value);
                } else {
                    Log.info ("Not adding: " + value);
                }
            }

            for (String value : docValues (authInfo, "scopenote")) {
                item.note = value;
            }
        } else {
            List<Document> preferredHeadings =
                authDB.getPreferredHeadings (item.heading);

            for (Document doc : preferredHeadings) {
                for (String value : docValues (doc, "preferred")) {
                    if (bibDB.recordCount (value) > 0) {
                        item.useInstead.add (value);
                    }
                }
            }
        }
    }


    public int getId (String from) throws Exception
    {
        return headingsDB.getHeadingStart (from);
    }


    public BrowseList getList (int rowid,
                               int offset,
                               int rows)
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

    public Browse browse;


    public BrowseSource (String DBpath, String field)
    {
        this.DBpath = DBpath;
        this.field = field;
    }
}


public class BrowseRequestHandler extends RequestHandlerBase
{
    private String authPath = null;
    private String bibPath = null;

    private HashMap<String,BrowseSource> sources = new HashMap<String,BrowseSource> ();

    private Browse browse = null;


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

        SolrParams p = SolrParams.toSolrParams (args);

        authPath = asAbsFile (p.get ("authIndexPath"));
        bibPath = asAbsFile (p.get ("bibIndexPath"));

        sources = new HashMap<String, BrowseSource> ();

        for (String source : Arrays.asList (p.get ("sources").split (","))) {
            NamedList<String> entry = (NamedList<String>)args.get (source);

            sources.put (source,
                         new BrowseSource (entry.get ("DBpath"),
                                           entry.get ("field")));
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


    private String clean (String s)
    {
        String cleaned = s.replaceAll ("[\\[\\]\\(\\)',\\.]", "");
        cleaned = cleaned.replaceAll ("-", " ");
        cleaned = cleaned.replaceAll (" +", " ");

        cleaned = handle_diacritics (cleaned);

        return cleaned.toLowerCase ();
    }



    public void handleRequestBody (SolrQueryRequest req,
                                   SolrQueryResponse rsp)
        throws Exception
    {
        SolrParams p = req.getParams ();
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
            source.browse = new Browse (new HeadingsDB (source.DBpath),
                                        new AuthDB (authPath),
                                        new BibDB (bibPath, source.field));
        }

        source.browse.reopenDatabasesIfUpdated ();

        if (from != null) {
            rowid = source.browse.getId (clean (from));
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
