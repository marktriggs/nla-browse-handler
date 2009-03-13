import org.apache.lucene.index.*;
import java.io.*;

public class Leech
{
    protected IndexReader reader;

    private String field;
    private TermEnum tenum;


    public Leech (String indexPath,
                  String field) throws IOException
    {
        reader = IndexReader.open (indexPath);
        this.field = field;
        tenum = reader.terms (new Term (field, ""));
    }


    public void dropOff () throws IOException
    {
        reader.close ();
    }


    public String next () throws Exception
    {
        if (tenum.next () &&
            tenum.term () != null &&
            tenum.term ().field ().equals (this.field)) {
            return tenum.term ().text ();
        } else {
            return null;
        }
    }
}
