package org.vufind.util;

/**
 * Simple class to instantiate and return a Normalizer object.
 * 
 * @author tod
 *
 */

public class NormalizerFactory {
	
	/**
	 * Create an instance of a class which implements the <code>Normalizer</code> interface
	 * 
	 * @param normalizerClass name of a <code>Normalizer</code> class
	 * @return instance of the named <code>Normalizer</code> class
	 * @throws Exception if anything goes wrong with creating the class
	 */
	public static Normalizer getNormalizer (String normalizerClass) throws Exception {
        return (Normalizer) (Class.forName (normalizerClass)
                .getConstructor ()
                .newInstance ());        
	}

}
