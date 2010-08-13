//
// Author: Mark Triggs <mark@dishevelled.net>
//

import java.io.*;
import java.util.*;
import java.util.regex.*;

import org.apache.lucene.search.*;
import org.apache.lucene.index.*;
import org.apache.lucene.document.*;

import java.sql.*;



class PrintBrowseHeadings
{
    private Leech bibLeech;
    private Leech authLeech;
    private Leech nonprefAuthLeech;

    IndexSearcher bibSearcher;
    IndexSearcher authSearcher;

    private String luceneField;


    private void loadHeadings (Leech leech,
                               PrintWriter out,
                               Predicate predicate)
        throws Exception
    {
        String heading;

        Normaliser normaliser;

        if (System.getenv ("NORMALISER") != null) {
            String normaliserClass = System.getenv ("NORMALISER");

            normaliser = (Normaliser) (Class.forName (normaliserClass)
                        .getConstructor ()
                        .newInstance ());
        } else {
            normaliser = new Normaliser ();
        }

        while ((heading = leech.next ()) != null) {
            if (predicate != null &&
                !predicate.isSatisfiedBy (heading)) {
                continue;
            }

            String norm = normaliser.normalise (heading);
            if (norm != null) {
                out.println (normaliser.normalise (heading) + "\1" + heading);
            }
        }
    }


    private int bibCount (String heading) throws IOException
    {
        Hits hits = bibSearcher.search
            (new TermQuery (new Term (luceneField, heading)));


        return hits.length ();
    }


    private boolean isLinkedFromBibData (String heading)
        throws IOException
    {
        Hits hits = authSearcher.search
            (new TermQuery (new Term (System.getProperty ("field.insteadof", "insteadOf"), heading)));

        Iterator it = hits.iterator ();

        while (it.hasNext ()) {
            Hit hit = (Hit) it.next ();

            Document doc = hit.getDocument ();

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


    private Leech getBibLeech (String bibPath, String luceneField)
        throws Exception
    {
        String leechClass = "Leech";

        if (System.getenv ("BIBLEECH") != null) {
            leechClass = System.getenv ("BIBLEECH");
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

        bibSearcher = new IndexSearcher (bibPath);

        PrintWriter out = new PrintWriter (new FileWriter (outFile));

        if (authPath != null) {
            nonprefAuthLeech = new Leech (authPath, System.getProperty ("field.insteadof", "insteadOf"));

            authSearcher = new IndexSearcher (authPath);

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
