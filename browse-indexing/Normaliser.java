//
// Author: Mark Triggs <mark@dishevelled.net>
//

import java.io.*;
import java.util.*;
import java.util.regex.*;
import au.gov.nla.util.*;

import com.ibm.icu.text.Collator;

// Note that this version is coming from Solr!
import org.apache.commons.codec.binary.Base64;

class Normaliser
{
    protected Collator collator = Collator.getInstance();

    protected Pattern junkregexp =
        Pattern.compile ("\\([^a-z0-9\\p{L} ]\\)");

    public String normalise (String s)
    {
        s = s.replace ("(", "")
            .replace (")", "")
            .replace ("-", "")
            .replaceAll (" +", " ");

        s = junkregexp.matcher (s) .replaceAll ("");

        byte[] key = Base64.encodeBase64 (collator.getCollationKey (s).toByteArray ());
        return new String(key);
    }
}
