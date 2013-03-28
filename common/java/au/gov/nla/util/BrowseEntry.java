package au.gov.nla.util;

public class BrowseEntry
{
    public byte[] key;
    public String value;
    public String build;

    public BrowseEntry (byte[] key, String value, String build)
    {
        this.key = key;
        this.value = value;
        this.build = build;
    }
}
