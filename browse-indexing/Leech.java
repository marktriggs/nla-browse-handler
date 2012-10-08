import org.apache.lucene.store.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import java.io.*;
import java.util.*;

import au.gov.nla.util.Normaliser;
import au.gov.nla.util.BrowseEntry;


public class Leech
{
    protected CompositeReader reader;
    protected IndexSearcher searcher;

    private String field;
    private Normaliser normaliser;

    TermsEnum tenum = null;


    public Leech (String indexPath,
                  String field) throws Exception
    {
        reader = DirectoryReader.open (FSDirectory.open (new File (indexPath)));
        searcher = new IndexSearcher (reader);
        this.field = field;

        normaliser = Normaliser.getInstance ();
    }


    public byte[] buildSortKey (String heading)
    {
        return normaliser.normalise (heading);
    }


    public void dropOff () throws IOException
    {
        reader.close ();
    }


    private boolean termExists (String t)
    {
        try {
            return (this.searcher.search (new ConstantScoreQuery(new TermQuery (new Term(this.field, t))),
                                          1).totalHits > 0);
        } catch (IOException e) {
            return false;
        }
    }


    // Return the next term from the currently selected TermEnum, if there is one.  Null otherwise.
    //
    // If there's no currently selected TermEnum, create one from the reader.
    //
    public BrowseEntry next () throws Exception
    {
        if (tenum == null) {
            AtomicReader ir = new SlowCompositeReaderWrapper(reader);
            Terms terms = ir.terms(this.field);
            if (terms == null) {
            	  return null;
            }
            tenum = terms.iterator(null);
        }

        if (tenum.next() != null) {
            String termText = tenum.term().utf8ToString();

            if (termExists(termText)) {
                return new BrowseEntry (buildSortKey (termText), termText) ;
            } else {
                return this.next();
            }
        } else {
            return null;
        }
    }
}
