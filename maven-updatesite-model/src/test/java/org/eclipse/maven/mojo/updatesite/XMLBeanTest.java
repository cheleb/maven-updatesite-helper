package org.eclipse.maven.mojo.updatesite;

import java.io.IOException;

import junit.framework.Assert;
import noNamespace.RepositoryDocument;

import org.junit.Test;

public class XMLBeanTest {

	private ModelHelper modelHelper = new ModelHelper();

	@Test
	public void testBuildCompositeContent() throws IOException {

		RepositoryDocument document = modelHelper
				.newRepositoryDocument("My Repo", ModelHelper.TYPE.ARTIFACT);

		

		
		modelHelper.save(document, ModelHelper.TYPE.ARTIFACT,
				System.out);
	}

	@Test
	public void testAddChild() {
		RepositoryDocument repositoryDocument = modelHelper
				.parseCompositeContent(XMLBeanTest.class
						.getResourceAsStream("/compositeContent.xml"));
		Assert.assertFalse("Child should exist!",
				modelHelper.appendChild(repositoryDocument, "0.0.2"));
		Assert.assertTrue("Child should by created!",
				modelHelper.appendChild(repositoryDocument, "0.0.3"));

		modelHelper.save(repositoryDocument, ModelHelper.TYPE.ARTIFACT,
				System.out);

	}

}
