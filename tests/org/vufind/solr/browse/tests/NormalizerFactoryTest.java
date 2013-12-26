package org.vufind.solr.browse.tests;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.vufind.util.Normalizer;
import org.vufind.util.NormalizerFactory;

public class NormalizerFactoryTest {
	
//	private NormalizerFactory factory;

	@Before
	public void setUp() throws Exception {
//		factory = new NormalizerFactory();
	}

	@Test
	public void testGetICUCollatorNormalizer() {
		String normalizerClass = "org.vufind.util.ICUCollatorNormalizer";
		try {
			//Normalizer normalizer = factory.getNormalizer(normalizerClass);
			Normalizer normalizer = NormalizerFactory.getNormalizer(normalizerClass);
			assertEquals (normalizerClass, normalizer.getClass().getName());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail("Exception creating class " + normalizerClass);
		}
	}

}
