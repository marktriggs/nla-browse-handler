// Stolen verbatim from Simon Jacob.
package au.gov.nla.util;


import java.util.*;
import java.text.*;

public class DiacriticStripper
{
    public String fix (String s)
    {
        String termText = Normalizer.normalize(s, Normalizer.Form.NFKD);

        StringBuilder sb = new StringBuilder(termText.length() + 10);
        char[] chars = termText.toCharArray();
        for (char ch : chars) {
            if (Character.getType(ch) == Character.NON_SPACING_MARK) {
                // skip this character
                continue;
            }

            switch(ch) {
            case '[': break;				   // delete characters
            case ']': break;
            case '\'': break;
            case '.': break;
            case ',': break;
            case '/': break;
            case '-': break;
            case '\u00C6':	sb.append("AE"); break; // AE digraph
            case '\u00E6':	sb.append("ae"); break;
            case '\u0152':	sb.append("OE"); break; // OE digraph
            case '\u0153':	sb.append("oe"); break;
            case '\u00DE':	sb.append("TH"); break; // Icelandic Thorn
            case '\u00FE':	sb.append("th"); break;
            case '\u0110':	sb.append("D"); break;  // Crossed D
            case '\u0111':	sb.append("d"); break;
            case '\u00F0':	sb.append("D"); break;  // Eth
            case '\u00D0':	sb.append("d"); break;
            case '\u0130':	sb.append("I"); break;  // Turkish I
            case '\u0131':	sb.append("i"); break;
            case '\u0141':	sb.append("L"); break;  // Polish L
            case '\u0142':	sb.append("l"); break;
                /* these are performed by the comp decomp above
                   case '\u2112':	sb.append("L"); break;  // Script L
                   case '\u2113':	sb.append("l"); break;
                   case '\u01A0':	sb.append("O"); break;  // Hooked O
                   case '\u01A1':	sb.append("o"); break;
                   case '\u01AF':	sb.append("U"); break;  // Hooked U
                   case '\u01B0':	sb.append("u"); break;
                */
            case '\u00D8':	sb.append("O"); break;  // Slashed O
            case '\u00F8':	sb.append("o"); break;
            case '\u0391':	sb.append("A"); break;  // Alpha
            case '\u03B1':	sb.append("a"); break;
            case '\u0392':	sb.append("B"); break;  // Beta
            case '\u03B2':	sb.append("b"); break;
            case '\u0393':	sb.append("G"); break;  // Gamma
            case '\u03B3':	sb.append("g"); break;
            default: sb.append(ch);
            }
        }

        return sb.toString();
    }
}

