// Build a browse list by walking the docs in an index and extracting sort key
// and values from a pair of stored fields.

import java.io.*;
import java.util.*;
import org.apache.lucene.store.*;
import org.apache.lucene.index.*;
import org.apache.lucene.document.*;

import au.gov.nla.util.Utils;
import au.gov.nla.util.BrowseEntry;

public class StoredFieldLeech extends Leech
{
    int currentDoc = 0;
    LinkedList<BrowseEntry> buffer;

    String sortField;
    String valueField;

    private FieldSelector fieldSelector;


    public StoredFieldLeech (String indexPath, String field) throws Exception
    {
        super (indexPath, field);

        sortField = Utils.getEnvironment ("SORTFIELD");
        valueField = Utils.getEnvironment ("VALUEFIELD");

        if (sortField == null || valueField == null) {
            throw new IllegalArgumentException ("Both SORTFIELD and " +
                                                "VALUEFIELD environment " +
                                                "variables must be set.");
        }

        fieldSelector = new FieldSelector () {
                public FieldSelectorResult accept (String fieldName) {
                    if (fieldName.equals (sortField) ||
                        fieldName.equals (valueField)) {
                        return FieldSelectorResult.LOAD;
                    } else {
                        return FieldSelectorResult.NO_LOAD;
                    }
                }
            };


        reader = IndexReader.open (FSDirectory.open (new File (indexPath)));
        buffer = new LinkedList<BrowseEntry> ();
    }


    private void loadDocument (IndexReader reader, int docid)
        throws Exception
    {
        Document doc = reader.document (currentDoc, fieldSelector);

        String[] sort_key = doc.getValues (sortField);
        String[] value = doc.getValues (valueField);

        if (sort_key.length == 1 && value.length == 1) {
            buffer.add (new BrowseEntry(buildSortKey(sort_key[0]), value[0]));
        }
    }


    public BrowseEntry next () throws Exception
    {
        while (buffer.isEmpty ()) {
            if (currentDoc < reader.maxDoc ()) {
                loadDocument (reader, currentDoc);
                currentDoc++;
            } else {
                return null;
            }
        }

        return buffer.remove ();
    }
}

