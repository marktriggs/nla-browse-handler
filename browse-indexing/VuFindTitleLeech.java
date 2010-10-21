import java.io.*;
import java.util.*;
import org.apache.lucene.index.*;
import org.apache.lucene.document.*;

public class VuFindTitleLeech extends Leech
{
    int currentDoc = 0;
    LinkedList<String[]> buffer;

    private FieldSelector titleSelector = new FieldSelector () {
            public FieldSelectorResult accept (String fieldName) {
                if (fieldName.equals ("title_sort") ||
                    fieldName.equals ("title_fullStr")) {
                    return FieldSelectorResult.LOAD;
                } else {
                    return FieldSelectorResult.NO_LOAD;
                }
            }
        };


    public VuFindTitleLeech (String indexPath, String field) throws Exception
    {
        super (indexPath, field);

        reader = IndexReader.open (indexPath);
        buffer = new LinkedList<String[]> ();
    }


    private void loadDocument (IndexReader reader, int docid)
        throws Exception
    {
        Document doc = reader.document (currentDoc, titleSelector);

        String[] sort_key = doc.getValues ("title_sort");
        String[] title = doc.getValues ("title_fullStr");

        if (sort_key.length == 1 && title.length == 1) {
            buffer.add (new String[] {sort_key[0], title[0]});
        }
    }



    public String[] next () throws Exception
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

