package au.gov.nla.util;

//
// Author: Mark Triggs <mark@dishevelled.net>
//

import java.io.*;
import java.util.*;
import java.util.regex.*;
import au.gov.nla.util.*;

import com.ibm.icu.text.Collator;

public class Normaliser
{
    protected Collator collator = Collator.getInstance();

    protected Pattern junkregexp =
        Pattern.compile ("\\([^a-z0-9\\p{L} ]\\)");


    public static Normaliser getInstance () throws Exception
    {
        Normaliser normaliser;

        if (Utils.getEnvironment ("NORMALISER") != null) {
            String normaliserClass = Utils.getEnvironment ("NORMALISER");

            normaliser = (Normaliser) (Class.forName (normaliserClass)
                        .getConstructor ()
                        .newInstance ());
        } else {
            normaliser = new Normaliser ();
        }

        return normaliser;
    }


    public byte[] normalise (String s)
    {
        s = s.replace ("(", "")
            .replace (")", "")
            .replace ("-", "")
            .replaceAll (" +", " ");

        s = junkregexp.matcher (s) .replaceAll ("");

        return collator.getCollationKey (s).toByteArray ();
    }
}
