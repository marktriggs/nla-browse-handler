//
// Author: Mark Triggs <mtriggs@nla.gov.au>
//

import java.io.*;
import java.util.*;

import java.sql.*;


class CreateBrowseSQLite
{
    private Connection outputDB;


    private void loadHeadings (BufferedReader br)
        throws Exception
    {
        int count = 0;
        String heading;

        outputDB.setAutoCommit (false);

        PreparedStatement prep = outputDB.prepareStatement (
            "insert or ignore into all_headings (key, heading) values (?, ?)");

        while ((heading = br.readLine ()) != null) {
            int sep = heading.indexOf ('\1');
            if (sep >= 0) {
                prep.setString (1, heading.substring (0, sep));
                prep.setString (2, heading.substring (sep + 1));

                prep.addBatch ();
            }

            if ((count % 500000) == 0) {
                prep.executeBatch ();
                prep.clearBatch ();
            }

            count++;
       }

        prep.executeBatch ();
        prep.close ();

        outputDB.commit ();
        outputDB.setAutoCommit (true);
    }


    private void setupDatabase ()
        throws Exception
    {
        Statement stat = outputDB.createStatement ();

        stat.executeUpdate ("drop table if exists all_headings;");
        stat.executeUpdate ("create table all_headings (key, heading);");
        stat.executeUpdate ("PRAGMA synchronous = OFF;");
        stat.execute ("PRAGMA journal_mode = OFF;");

        stat.close ();
    }


    private void buildOrderedTables ()
        throws Exception
    {
        Statement stat = outputDB.createStatement ();

        stat.executeUpdate ("drop table if exists headings;");
        stat.executeUpdate ("create table headings " +
                            "as select * from all_headings order by key;");

        stat.executeUpdate ("create index keyindex on headings (key);");

        stat.close ();
    }


    public void create (String headingsFile, String outputPath)
        throws Exception
    {
        Class.forName ("org.sqlite.JDBC");
        outputDB = DriverManager.getConnection ("jdbc:sqlite:" + outputPath);

        setupDatabase ();

        BufferedReader br = new BufferedReader
            (new FileReader (headingsFile));

        loadHeadings (br);

        br.close ();

        buildOrderedTables ();
    }


    public static void main (String args[])
        throws Exception
    {
        if (args.length != 2) {
            System.err.println
                ("Usage: CreateBrowseSQLite <headings file> <db file>");
            System.exit (0);
        }

        CreateBrowseSQLite self = new CreateBrowseSQLite ();

        self.create (args[0], args[1]);
    }
}
