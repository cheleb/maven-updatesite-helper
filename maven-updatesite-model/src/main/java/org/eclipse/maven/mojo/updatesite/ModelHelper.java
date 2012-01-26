package org.eclipse.maven.mojo.updatesite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Date;

import noNamespace.Repository;
import noNamespace.Repository.Children;
import noNamespace.Repository.Children.Child;
import noNamespace.Repository.Properties;
import noNamespace.Repository.Properties.Property;
import noNamespace.RepositoryDocument;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.impl.common.IOUtil;

public class ModelHelper {

	/**
     * 
     */
	private static final String XML_VERSION_1_0_ENCODING_UTF_8 = "<?xml version='1.0' encoding='UTF-8'?>\n";
	/**
	 * 
	 */
	private static final String COMPOSITE_METADATA_REPOSITORY_VERSION_1_0_0 = "<?compositeMetadataRepository version='1.0.0'?>\n";
	/**
	 * 
	 */
	private static final String COMPOSITE_ARTIFACT_REPOSITORY_VERSION_1_0_0 = "<?compositeArtifactRepository version='1.0.0'?>\n";

	public enum TYPE {
		METADATA(
				"org.eclipse.equinox.internal.p2.metadata.repository.CompositeMetadataRepository",
				"compositeContent.xml"), ARTIFACT(
				"org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository",
				"compositeArtifacts.xml");

		String asString;

		String filename;

		TYPE(String type, String filename) {
			this.asString = type;
			this.filename = filename;
		}

		public String filename() {
			return filename;
		}

	}

	private XmlOptions xmlOptions;

	public ModelHelper() {
		xmlOptions = new XmlOptions();
		xmlOptions.setSavePrettyPrint();
		xmlOptions.setSaveNoXmlDecl();
	}

	public RepositoryDocument newRepositoryDocument(String name, TYPE type) {
		RepositoryDocument document = RepositoryDocument.Factory.newInstance();
		Repository repository = document.addNewRepository();

		repository.setType(type.asString);

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

		repository.addNewChildren().setSize(0);

		return document;
	}

	public RepositoryDocument parseCompositeContent(InputStream resourceAsStream) {
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

	public boolean updateChild(RepositoryDocument repositoryDocument,
			String location) {
		Repository repository = repositoryDocument.getRepository();

		for (Property property : repository.getProperties().getPropertyList()) {
			if ("p2.timestamp".equals(property.getName())) {
				property.setValue(String.valueOf(new Date().getTime()));
			}
		}

		Child child = createChild(repository.getChildren(), location);

		return child != null;

	}

	private Child createChild(Children children, String location) {

		for (Child child : children.getChildList()) {
			if (child.getLocation().equals(location)) {
				return null;
			}
		}
		Child child = children.addNewChild();
		children.setSize(children.getSize() + 1);
		child.setLocation(location);
		return child;
	}

	public void save(RepositoryDocument document, ModelHelper.TYPE type,
			PrintStream out) {

		out.print(new String(XML_VERSION_1_0_ENCODING_UTF_8));
		switch (type) {
		case METADATA:
			out.print(COMPOSITE_METADATA_REPOSITORY_VERSION_1_0_0);
			break;
		case ARTIFACT:
			out.print(COMPOSITE_ARTIFACT_REPOSITORY_VERSION_1_0_0);
			break;
		default:
			break;
		}

		try {
			document.save(out, xmlOptions);
		} catch (IOException e) {
			throw new ModelException(e.getLocalizedMessage(), e);
		}
	}

	public InputStream getInputStream(RepositoryDocument repositoryDocument,
			ModelHelper.TYPE type) {

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try {
			outputStream.write(XML_VERSION_1_0_ENCODING_UTF_8.getBytes(Charset
					.forName("UTF-8")));
			switch (type) {
			case METADATA:
				outputStream.write(COMPOSITE_METADATA_REPOSITORY_VERSION_1_0_0
						.getBytes(Charset.forName("UTF-8")));
				break;
			case ARTIFACT:
				outputStream.write(COMPOSITE_ARTIFACT_REPOSITORY_VERSION_1_0_0
						.getBytes(Charset.forName("UTF-8")));
				break;
			default:
				break;
			}

			IOUtil.copyCompletely(
					repositoryDocument.newInputStream(xmlOptions), outputStream);
			outputStream.write("\n".getBytes(Charset.forName("UTF-8")));
		} catch (IOException e) {
			throw new ModelException(e.getLocalizedMessage(), e);
		}

		return new ByteArrayInputStream(outputStream.toByteArray());
	}

}
