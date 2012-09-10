import org.apache.lucene.store.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import java.io.*;
import java.util.*;

import au.gov.nla.util.Normaliser;
import au.gov.nla.util.BrowseEntry;


public class Leech
{
    protected IndexReader reader;
    protected IndexSearcher searcher;

    private String field;
    private Normaliser normaliser;

    private LinkedList<AtomicReader> subreaders;
    TermsEnum tenum = null;


    public Leech (String indexPath,
                  String field) throws Exception
    {
        reader = DirectoryReader.open (FSDirectory.open (new File (indexPath)));
        searcher = new IndexSearcher (reader);
        this.field = field;

        normaliser = Normaliser.getInstance ();

        subreaders = new LinkedList<AtomicReader>();

        findAtomicReaders(reader, subreaders);
    }


    private void findAtomicReaders(IndexReader reader,
                                   LinkedList<AtomicReader> atomicReaders)
    {
        if (reader instanceof AtomicReader) {
            atomicReaders.add((AtomicReader) reader);
        } else {
            for (IndexReader ir : ((CompositeReader) reader).getSequentialSubReaders ()) {
                findAtomicReaders(ir, atomicReaders);
            }
        }
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
    // If there's no currently selected TermEnum, create one from the next subreader in the list.
    //
    // If the currently selected TermEnum has no remaining terms, move on to the next subreader.
    //
    public BrowseEntry next () throws Exception
    {
        if (tenum == null) {
            if (!subreaders.isEmpty()) {
                AtomicReader ir = subreaders.pop();

                Terms terms = ir.terms(this.field);
                if (terms != null) {
                		tenum = terms.iterator(null);
                } else {
                		return null;
                }
            } else {
                return null;
            }
        }


        if (tenum.next() != null) {
            String termText = tenum.term().utf8ToString();

            if (termExists(termText)) {
                return new BrowseEntry (buildSortKey (termText), termText) ;
            } else {
                return this.next();
            }
        } else {
            tenum = null;
            return this.next();
        }
    }
}
