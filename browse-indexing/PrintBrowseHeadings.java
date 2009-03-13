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
import au.gov.nla.solr.handler.DiacriticStripper;


class PrintBrowseHeadings
{
    IndexSearcher bibSearcher;
    IndexSearcher authSearcher;

    private String luceneField;



    private DiacriticStripper ds = new DiacriticStripper ();

    private Pattern junkregexp =
        Pattern.compile ("\\([^a-z0-9\\p{L} ]\\)");


    private String normalise (String text)
    {
        return junkregexp.matcher (ds.fix (text
                                           .toLowerCase ()
                                           .replace ("(", "")
                                           .replace (")", "")
                                           .replace ("-", " ")))
            .replaceAll ("")
            .replaceAll (" +", " ");
    }


    private void loadHeadings (Leech leech,
                               PrintWriter out,
                               Predicate predicate)
        throws Exception
    {
        String heading;

        while ((heading = leech.next ()) != null) {
            if (predicate != null &&
                !predicate.isSatisfiedBy (heading)) {
                continue;
            }

            out.println (normalise (heading) + "\1" + heading);
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
            (new TermQuery (new Term ("insteadOf", heading)));

        Iterator it = hits.iterator ();

        while (it.hasNext ()) {
            Hit hit = (Hit) it.next ();

            Document doc = hit.getDocument ();

            String preferredHeading = doc.getValues ("preferred")[0];

            if (bibCount (preferredHeading) > 0) {
                return true;
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
        Leech bibLeech;
        Leech nonprefAuthLeech;

        bibLeech = getBibLeech (bibPath, luceneField);
        this.luceneField = luceneField;

        bibSearcher = new IndexSearcher (bibPath);

        PrintWriter out = new PrintWriter (new FileWriter (outFile));

        if (authPath != null) {
            nonprefAuthLeech = new Leech (authPath, "insteadOf");

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
                ("Usage: PrintBrowseHeadings <bib index> <bib field>"
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
