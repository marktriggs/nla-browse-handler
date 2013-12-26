package org.vufind.util;

/**
 * An interface for alphabetical browse normalizers.
 * 
 * @author Tod Olson <tod@uchicago.edu>
 *
 */

public interface Normalizer {
	
	/**
	 * Normalizes the input string to a collation key, which will be used for the 
	 * sort order of the browse index.
	 * 
	 * @param s	the string to be normalized, e.g. a term for the browse index
	 * @return	collation key for determining browse order
	 */
	public byte[] normalize (String s);

}