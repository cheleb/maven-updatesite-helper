package org.eclipse.maven.mojo.updatesite;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Date;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;

import noNamespace.Repository;
import noNamespace.RepositoryDocument;
import noNamespace.Repository.Children;
import noNamespace.Repository.Properties;
import noNamespace.Repository.Children.Child;
import noNamespace.Repository.Properties.Property;

public class ModelHelper {

	public static RepositoryDocument newRepositoryDocument(String name) {
		RepositoryDocument document = RepositoryDocument.Factory.newInstance();
		Repository repository = document.addNewRepository();
		repository
				.setType("org.eclipse.equinox.internal.p2.metadata.repository.CompositeMetadataRepository");
		repository.setVersion("1.0.0");
		repository.setName(name);
		Properties properties = repository.addNewProperties();
		Property p2CompressedProperty = properties.addNewProperty();
		p2CompressedProperty.setName("p2.compressed");
		p2CompressedProperty.setValue("true");

		Property p2TimeStampProperty = properties.addNewProperty();
		p2TimeStampProperty.setName("p2.timestamp");
		p2TimeStampProperty.setValue(String.valueOf(new Date().getTime()));

		properties.setSize(2);
		return document;
	}

	public static RepositoryDocument parseCompositeContent(
			InputStream resourceAsStream) {
		try {
			XmlOptions xmlOptions = new XmlOptions();
			xmlOptions.setLoadStripProcinsts();
			return RepositoryDocument.Factory.parse(resourceAsStream,
					xmlOptions);
		} catch (XmlException e) {
			throw new ModelException(e.getLocalizedMessage(), e);
		} catch (IOException e) {
			throw new ModelException(e.getLocalizedMessage(), e);
		}

	}

	public static boolean updateChild(RepositoryDocument repositoryDocument,
			String location) {
		Repository repository = repositoryDocument.getRepository();

		for (Property property : repository.getProperties().getPropertyArray()) {
			if ("p2.timestamp".equals(property.getName())) {
				property.setValue(String.valueOf(new Date().getTime()));
			}
		}

		Child child = createChild(repository.getChildren(), location);

		return child != null;

	}

	private static Child createChild(Children children, String location) {

		for (Child child : children.getChildArray()) {
			if (child.getLocation().equals(location)) {
				return null;
			}
		}

		Child child = children.addNewChild();
		child.setLocation(location);
		return child;
	}

	public static void save(RepositoryDocument document, PrintStream out) {

		out.println("<?xml version='1.0' encoding='UTF-8'?>");
		out.println("<?compositeMetadataRepository version='1.0.0'?>");
		XmlOptions xmlOptions = new XmlOptions();
		xmlOptions.setSavePrettyPrint();
		xmlOptions.setSaveNoXmlDecl();
		try {
			document.save(out, xmlOptions);
		} catch (IOException e) {
			throw new ModelException(e.getLocalizedMessage(), e);
		}
	}

}
