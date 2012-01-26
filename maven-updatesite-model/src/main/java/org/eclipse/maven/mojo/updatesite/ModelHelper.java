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

/**
 * Helper class to Manipulate the Composite repository metadata files.
 * 
 * @author chelebithil
 * 
 */
public class ModelHelper {

	/**
	 * UTF-8.
	 */
	private static final String UTF_8 = "UTF-8";
	/**
	 * XML Prolog.
	 */
	private static final String XML_VERSION_1_0_ENCODING_UTF_8 = "<?xml version='1.0' encoding='UTF-8'?>\n";
	/**
	 * "compositeMetadataRepository" PI.
	 */
	private static final String COMPOSITE_METADATA_REPOSITORY_VERSION_1_0_0 = "<?compositeMetadataRepository version='1.0.0'?>\n";
	/**
	 * "compositeArtifactRepository" PI.
	 */
	private static final String COMPOSITE_ARTIFACT_REPOSITORY_VERSION_1_0_0 = "<?compositeArtifactRepository version='1.0.0'?>\n";

	/**
	 * Enum to represent the 2 kinds of metadata files.
	 * 
	 * @author chelebithil
	 * 
	 */
	public enum TYPE {
		METADATA(
				"org.eclipse.equinox.internal.p2.metadata.repository.CompositeMetadataRepository",
				"compositeContent.xml"), ARTIFACT(
				"org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository",
				"compositeArtifacts.xml");

		/**
		 * Class name used in the file.
		 */
		private String className;

		/**
		 * Filename
		 */
		private String filename;

		/**
		 * Constructor.
		 * 
		 * @param className
		 * @param filename
		 */
		private TYPE(String className, String filename) {
			this.className = className;
			this.filename = filename;
		}

		/**
		 * Getter to {@link TYPE#className}.
		 * 
		 * @return qualified className
		 */
		public String getClassName() {
			return className;
		}

		/**
		 * Getter for {@link TYPE#filename}.
		 * 
		 * @return filename.
		 */
		public String getFilename() {
			return filename;
		}

	}

	/**
	 * {@link XmlOptions} to customize the serialization.
	 */
	private XmlOptions xmlOptions;

	/**
	 * Constructor.
	 */
	public ModelHelper() {
		xmlOptions = new XmlOptions();
		xmlOptions.setSavePrettyPrint();
		xmlOptions.setSaveNoXmlDecl();
	}

	/**
	 * Build a {@link RepositoryDocument} with given properties (p2.compressed,
	 * p2.timestamp) and children.
	 * 
	 * @param name
	 *            of the repository
	 * @param type
	 *            Metadata or Artifact
	 * @return inited repository
	 */
	public RepositoryDocument newRepositoryDocument(String name, TYPE type) {
		RepositoryDocument document = RepositoryDocument.Factory.newInstance();
		Repository repository = document.addNewRepository();

		repository.setType(type.getClassName());

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

	/**
	 * Parse repository.
	 * 
	 * @param resourceAsStream
	 *            to parse
	 * @return repository.
	 */
	public RepositoryDocument parseCompositeContent(InputStream resourceAsStream) {
		try {
			XmlOptions xmlOptionsNoPI = new XmlOptions();
			xmlOptionsNoPI.setLoadStripProcinsts();
			return RepositoryDocument.Factory.parse(resourceAsStream,
					xmlOptionsNoPI);
		} catch (XmlException e) {
			throw new ModelException(e.getLocalizedMessage(), e);
		} catch (IOException e) {
			throw new ModelException(e.getLocalizedMessage(), e);
		}

	}

	/**
	 * Append a child location. Update the p2.timestamp.
	 * 
	 * @param repositoryDocument
	 *            to update.
	 * @param location
	 *            of the child
	 * @return true if the child is newly created
	 */
	public boolean appendChild(RepositoryDocument repositoryDocument,
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

	/**
	 * Create a child if it doesn't already exist.
	 * 
	 * @param children
	 *            children node to support addition
	 * @param location
	 *            of the new child node
	 * @return child node if newly created null otherwise
	 */
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

	/**
	 * Save document as given type.
	 * 
	 * @param document
	 *            to save
	 * @param type
	 *            to apply
	 * @param out
	 *            to output
	 */
	public void save(RepositoryDocument document, ModelHelper.TYPE type,
			PrintStream out) {

		out.print(XML_VERSION_1_0_ENCODING_UTF_8);
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
					.forName(UTF_8)));
			switch (type) {
			case METADATA:
				outputStream.write(COMPOSITE_METADATA_REPOSITORY_VERSION_1_0_0
						.getBytes(Charset.forName(UTF_8)));
				break;
			case ARTIFACT:
				outputStream.write(COMPOSITE_ARTIFACT_REPOSITORY_VERSION_1_0_0
						.getBytes(Charset.forName(UTF_8)));
				break;
			default:
				break;
			}

			IOUtil.copyCompletely(
					repositoryDocument.newInputStream(xmlOptions), outputStream);
			outputStream.write("\n".getBytes(Charset.forName(UTF_8)));
		} catch (IOException e) {
			throw new ModelException(e.getLocalizedMessage(), e);
		}

		return new ByteArrayInputStream(outputStream.toByteArray());
	}

}
