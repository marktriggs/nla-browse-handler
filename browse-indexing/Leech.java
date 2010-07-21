import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import java.io.*;

public class Leech
{
    protected IndexReader reader;
    protected IndexSearcher searcher;

    private String field;
    private TermEnum tenum;


    public Leech (String indexPath,
                  String field) throws IOException
    {
        reader = IndexReader.open (indexPath);
        searcher = new IndexSearcher (reader);
        this.field = field;
        tenum = reader.terms (new Term (field, ""));
    }


    public void dropOff () throws IOException
    {
        searcher.close ();
        reader.close ();
    }


    private boolean termExists (Term t)
    {
        try {
            return (this.searcher.search (new TermQuery (t)).length () > 0);
        } catch (IOException e) {
            return false;
        }
    }


    public String next () throws Exception
    {
        if (tenum.next () &&
            tenum.term () != null &&
            tenum.term ().field ().equals (this.field)) {
            if (termExists (tenum.term ())) {
            return tenum.term ().text ();
        } else {
                return this.next ();
            }
        } else {
            return null;
        }
    }
}
