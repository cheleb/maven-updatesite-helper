package org.eclipse.maven.mojo.updatesite;

import java.io.IOException;
import java.io.OutputStream;

import junit.framework.Assert;

import noNamespace.Repository;
import noNamespace.Repository.Children;
import noNamespace.Repository.Children.Child;
import noNamespace.RepositoryDocument;

import org.apache.xmlbeans.XmlOptions;
import org.junit.Test;

public class XMLBeanTest {
	
	
	private XmlOptions xmlOptions;

	

	@Test
	public void testBuildCompositeContent() throws IOException {

		RepositoryDocument document = ModelHelper.newRepositoryDocument("My Repo");

		Repository repository = document.getRepository();

		Children children = repository.addNewChildren();
		Child child1 = children.addNewChild();
		child1.setLocation("0.0.1");
		Child child2 = children.addNewChild();
		child2.setLocation("0.0.2");

		
		

	}

	@Test
	public void testAddChild() {
		RepositoryDocument repositoryDocument = ModelHelper
				.parseCompositeContent(XMLBeanTest.class
						.getResourceAsStream("/compositeContent.xml"));
		Assert.assertFalse("Child should exist!", ModelHelper.updateChild(repositoryDocument, "0.0.2"));
		Assert.assertTrue("Child should by created!", ModelHelper.updateChild(repositoryDocument, "0.0.3"));
		
		
		ModelHelper.save(repositoryDocument, System.out);
		
	}

}
