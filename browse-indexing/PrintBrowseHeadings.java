//
// Author: Mark Triggs <mark@dishevelled.net>
//

import java.io.*;
import java.util.*;
import java.util.regex.*;

import org.apache.lucene.store.*;
import org.apache.lucene.search.*;
import org.apache.lucene.index.*;
import org.apache.lucene.document.*;

import java.sql.*;



class PrintBrowseHeadings
{
    static int MAX_PREFERRED_HEADINGS = 100000;

    private Leech bibLeech;
    private Leech authLeech;
    private Leech nonprefAuthLeech;

    IndexSearcher bibSearcher;
    IndexSearcher authSearcher;

    private String luceneField;

    private String KEY_SEPARATOR = "\1";
    private String RECORD_SEPARATOR = "\r\n";

    private void loadHeadings (Leech leech,
                               PrintWriter out,
                               Predicate predicate)
        throws Exception
    {
        String[] h;
        while ((h = leech.next ()) != null) {
            String sort_key = h[0];
            String heading = h[1];

            if (predicate != null &&
                !predicate.isSatisfiedBy (heading)) {
                continue;
            }

            if (sort_key != null) {
                out.print (sort_key + KEY_SEPARATOR + heading + RECORD_SEPARATOR);
            }
        }
    }


    private int bibCount (String heading) throws IOException
    {
        TotalHitCountCollector counter = new TotalHitCountCollector();

        bibSearcher.search (new TermQuery (new Term (luceneField, heading)),
                            counter);

        return counter.getTotalHits ();
    }


    private boolean isLinkedFromBibData (String heading)
        throws IOException
    {
        TopDocs hits = authSearcher.search
            (new TermQuery (new Term (System.getProperty ("field.insteadof", "insteadOf"), heading)),
             MAX_PREFERRED_HEADINGS);

        for (int i = 0; i < hits.scoreDocs.length; i++) {
            Document doc = authSearcher.getIndexReader ().document (hits.scoreDocs[i].doc);

            String[] preferred = doc.getValues (System.getProperty ("field.preferred", "preferred"));
            if (preferred.length > 0) {
                String preferredHeading = preferred[0];

                if (bibCount (preferredHeading) > 0) {
                    return true;
                }
            } else {
                return false;
            }
        }

        return false;
    }


    private String getEnvironment (String var)
    {
        return (System.getenv (var) != null) ?
            System.getenv (var) : System.getProperty (var.toLowerCase ());
    }


    private Leech getBibLeech (String bibPath, String luceneField)
        throws Exception
    {
        String leechClass = "Leech";

        if (getEnvironment ("BIBLEECH") != null) {
            leechClass = getEnvironment ("BIBLEECH");
        }

        return (Leech) (Class.forName (leechClass)
                        .getConstructor (String.class, String.class)
                        .newInstance (bibPath, luceneField ));
    }


    public void create (String bibPath,
                        String luceneField,
                        String authPath,
                        String outFile)
        throws Exception
    {
        bibLeech = getBibLeech (bibPath, luceneField);
        this.luceneField = luceneField;

        bibSearcher = new IndexSearcher (FSDirectory.open (new File (bibPath)));

        PrintWriter out = new PrintWriter (new FileWriter (outFile));

        if (authPath != null) {
            nonprefAuthLeech = new Leech (authPath,
                                          System.getProperty ("field.insteadof",
                                                              "insteadOf"));

            authSearcher = new IndexSearcher (FSDirectory.open (new File (authPath)));

            loadHeadings (nonprefAuthLeech, out,
                          new Predicate () {
                              public boolean isSatisfiedBy (Object obj)
                              {
                                  String heading = (String) obj;

                                  try {
                                      return isLinkedFromBibData (heading);
                                  } catch (IOException e) {
                                      return true;
                                  }
                              }}
                );

            nonprefAuthLeech.dropOff ();
        }

        loadHeadings (bibLeech, out, null);

        bibLeech.dropOff ();

        out.close ();
    }


    public static void main (String args[])
        throws Exception
    {
        if (args.length != 3 && args.length != 4) {
            System.err.println
                ("Usage: PrintBrowseHeadings <bib index> <bib field> "
                 + "<auth index> <out file>");
            System.err.println ("\nor:\n");
            System.err.println
                ("Usage: PrintBrowseHeadings <bib index> <bib field>"
                 + " <out file>");

            System.exit (0);
        }

        PrintBrowseHeadings self = new PrintBrowseHeadings ();

        if (args.length == 4) {
            self.create (args[0], args[1], args[2], args[3]);
        } else {
            self.create (args[0], args[1], null, args[2]);
        }
    }
}
