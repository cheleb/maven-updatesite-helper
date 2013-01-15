package org.eclipse.maven.mojo.updatesite;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import noNamespace.RepositoryDocument;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.eclipse.maven.mojo.updatesite.ModelHelper.TYPE;
import org.eclipse.maven.mojo.updatesite.configuration.Site;
import org.eclipse.maven.mojo.updatesite.logger.Logger;
import org.eclipse.maven.mojo.updatesite.sftp.Sftp;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

/**
 * Goal which deploy update site to remote using sftp.
 * 
 * @goal deploy
 * 
 * @phase install
 */
public class DeployMojo extends AbstractMojo {

	/**
	 * SSH port: 22.
	 */
	private static final int SSH_PORT = 22;

	/**
	 * {@link MavenProject} injected by plexus.
	 * 
	 * @parameter default-value="${project}"
	 */
	private MavenProject mavenProject;

	/**
	 * The current user system settings for use in Maven.
	 * 
	 * @parameter expression="${settings}"
	 * @required
	 * @readonly
	 */
	private Settings settings;

	/**
	 * List of site to deploy to.
	 * 
	 * @parameter
	 */
	private List<Site> sites;
	/**
	 * SSH knowHost file.
	 * 
	 * @parameter default-value="${user.home}/.ssh/known_hosts"
	 * @required
	 */
	private String knownHost;

	/**
	 * SSH Identity file.
	 * 
	 * @parameter default-value="${user.home}/.ssh/id_rsa"
	 * @required
	 */
	private String identity;

	/**
	 * Location of the site.
	 * 
	 * @parameter expression="${project.build.directory}/repository"
	 * @required
	 */
	private File siteDirectory;

	/**
	 * {@link ModelHelper}.
	 */
	private ModelHelper modelHelper = new ModelHelper();

	/**
	 * Group number host for {@link DeployMojo#SFTP_PATTERN}.
	 */

	static final int SFTP_PATTERN_HOST = 1;

	/**
	 * Group number port for {@link DeployMojo#SFTP_PATTERN}.
	 */
	static final int SFTP_PATTERN_PORT = 2;
	/**
	 * Group number path for {@link DeployMojo#SFTP_PATTERN}.
	 */

	static final int SFTP_PATTERN_PATH = 3;

	/**
	 * STFP URL {@link Pattern}.
	 */
	static final Pattern SFTP_PATTERN = Pattern
			.compile("^sftp://([^/^:)]+)\\:?(\\d+)?(.*)$");

	/**
	 * {@inheritDoc}.
	 */
	public void execute() throws MojoExecutionException {

		for (Site site : sites) {
			deploy(site);
		}

	}

	/**
	 * Deploy update site.
	 * 
	 * @param site
	 *            to deploy to.
	 * @throws MojoExecutionException
	 *             on error.
	 */
	private void deploy(Site site) throws MojoExecutionException {

		Matcher matcher = SFTP_PATTERN.matcher(site.getBaseURL());
		if (!matcher.matches()) {
			throw new MojoExecutionException(site.getBaseURL()
					+ " is not a valid sftp url");
		}
		String host = matcher.group(SFTP_PATTERN_HOST);
		String portAsString = matcher.group(SFTP_PATTERN_PORT);
		int port;
		if (portAsString == null) {
			port = SSH_PORT;
		} else {
			port = Integer.parseInt(portAsString);
		}
		String basePath = matcher.group(SFTP_PATTERN_PATH);

		Sftp sftp = new Sftp(new Logger() {
			@Override
			public void info(String message) {
				getLog().info(message);

			}
		}, knownHost, identity);

		Server server = settings.getServer(site.getServerId());
		if (server == null) {
			throw new MojoExecutionException("Could not find serverId: \""
					+ site.getServerId() + "\"");
		}

		String pass = server.getPassphrase();

		if (pass == null) {
			throw new MojoExecutionException("Passphrase could not be null");
		}

		String user = server.getUsername();

		try {

			sftp.openSession(user, pass, host, port);

			boolean newRepo = initRepository(site, basePath, sftp);

			String childLocation = initChildLocation(sftp);

			updloadFiles(siteDirectory, childLocation, sftp);

			RepositoryDocument repositoryDocument;

			if (newRepo || sftp.fileDoesNotExist("compositeContent.xml")) {
				repositoryDocument = modelHelper.newRepositoryDocument(
						site.getName(), ModelHelper.TYPE.ARTIFACT);
			} else {

				InputStream inputStream = sftp.get("compositeContent.xml");

				repositoryDocument = modelHelper
						.parseCompositeContent(inputStream);
			}

			modelHelper.appendChild(repositoryDocument,
					mavenProject.getVersion());

			updateCompositeMetafiles(sftp, repositoryDocument);

			updateParentRepo(site, sftp);

		} catch (JSchException e) {
			throw new MojoExecutionException(e.getLocalizedMessage(), e);
		} catch (SftpException e) {
			throw new MojoExecutionException(e.getLocalizedMessage(), e);
		} catch (IOException e) {
			throw new MojoExecutionException(e.getLocalizedMessage(), e);
		} finally {
			sftp.disconnect();
		}

	}

	/**
	 * Push Composite file to site.
	 * 
	 * @param sftp
	 *            connection.
	 * @param repositoryDocument
	 *            to publish
	 * @throws SftpException
	 *             on error
	 */
	private void updateCompositeMetafiles(Sftp sftp,
			RepositoryDocument repositoryDocument) throws SftpException {
		putRepositoryFile(sftp, repositoryDocument, ModelHelper.TYPE.ARTIFACT);
		putRepositoryFile(sftp, repositoryDocument, ModelHelper.TYPE.METADATA);

	}

	/**
	 * Put a repository file.
	 * 
	 * @param sftp
	 *            connection
	 * @param repositoryDocument
	 *            to publish
	 * @param type
	 *            {@link TYPE} of the document.
	 * @throws SftpException
	 *             on error
	 */
	private void putRepositoryFile(Sftp sftp,
			RepositoryDocument repositoryDocument, TYPE type)
			throws SftpException {
		repositoryDocument.getRepository().setType(type.getClassName());
		sftp.put(modelHelper.getInputStream(repositoryDocument, type),
				type.getFilename());
	}

	/**
	 * Recursively update parent composite descriptor. Stops when descriptor is
	 * absent of parent directory.
	 * 
	 * @param site
	 *            to publish to
	 * @param sftp
	 *            connection
	 * @throws SftpException
	 *             on error
	 */
	private void updateParentRepo(Site site, Sftp sftp) throws SftpException {
		String currentRepoName = sftp.getCurrentFolderName();
		sftp.cd("..");
		String pwd = sftp.pwd();
		getLog().info("Updating " + currentRepoName + " in " + pwd);
		if (sftp.fileExists("compositeArtifacts.xml")) {
			InputStream inputStream = sftp.get("compositeContent.xml");

			RepositoryDocument repositoryDocument = modelHelper
					.parseCompositeContent(inputStream);
			boolean b = modelHelper.appendChild(repositoryDocument,
					site.getName());
			if (b) {
				getLog().info("Added");
			} else {
				getLog().info("Updated");
			}

			updateCompositeMetafiles(sftp, repositoryDocument);

			updateParentRepo(site, sftp);

		} else {
			getLog().debug("Base repo: " + pwd);
		}

	}

	/**
	 * Recursively upload files.
	 * 
	 * @param folder
	 *            to upload
	 * @param dst
	 *            remote destination
	 * @param sftp
	 *            connection
	 * @throws SftpException
	 *             on error
	 * @throws IOException
	 *             on error
	 */
	private void updloadFiles(File folder, String dst, Sftp sftp)
			throws SftpException, IOException {
		File[] listFiles = folder.listFiles();
		for (int i = 0; i < listFiles.length; i++) {
			File file = listFiles[i];
			if (".".equals(file.getName()) || "..".equals(file.getName())) {
				continue;
			}
			String path = dst + "/" + file.getName();
			if (file.isDirectory()) {
				if (sftp.fileDoesNotExist(path)) {
					sftp.mkdir(path);
				}
				updloadFiles(file, path, sftp);
			} else {
				InputStream inputStream = new FileInputStream(file);
				sftp.put(inputStream, path);
				inputStream.close();
			}

		}

	}

	/**
	 * Init/reset child repository remote location.
	 * 
	 * @param sftp
	 *            connection
	 * @return child location
	 * @throws SftpException
	 *             on error
	 */
	private String initChildLocation(Sftp sftp) throws SftpException {
		String childLocation = mavenProject.getVersion();

		if (sftp.fileDoesNotExist(childLocation)) {
			sftp.mkdir(childLocation);
			getLog().info("Create new child localtion: " + childLocation);
		} else {
			sftp.rmtree(childLocation, false);
		}
		return childLocation;
	}

	/**
	 * Init repository:
	 * <ul>
	 * <li>create directory to base update site</li>
	 * <li>create parent(s) repository(ies)
	 * 
	 * @param site
	 *            to deploy to
	 * @param basePath
	 *            remote update site base
	 * @param sftp
	 *            connection
	 * @return true in new creation
	 * @throws SftpException
	 *             on error
	 */
	private boolean initRepository(Site site, String basePath, Sftp sftp)
			throws SftpException {

		createRemotePath(basePath, sftp);

		if (site.getParent() != null) {
			String parentPath = site.getParent();
			createRemoteRepo(parentPath, sftp);
		}

		boolean newRepo = false;
		if (sftp.fileDoesNotExist(site.getName())) {
			sftp.mkdir(site.getName());
			getLog().info("Create new repo: " + site.getName());
			newRepo = true;
		}

		sftp.cd(site.getName());
		return newRepo;
	}

	/**
	 * Create remote tree.
	 * 
	 * @param path
	 *            to create
	 * @param sftp
	 *            connection
	 * @throws SftpException
	 *             on error
	 */
	private void createRemotePath(String path, Sftp sftp) throws SftpException {
		try {
			sftp.cd(path);
		} catch (SftpException e) {
			StringTokenizer tokenizer = new StringTokenizer(path, "/");
			sftp.cd("/");
			while (tokenizer.hasMoreTokens()) {
				String folder = tokenizer.nextToken();
				if (sftp.fileDoesNotExist(folder)) {
					sftp.mkdir(folder);
				}
				sftp.cd(folder);
			}
		}
	}

	/**
	 * Create remote repo
	 * <ul>
	 * <li>Create folder</li>
	 * <li>Put repository metadata</li>
	 * </ul>
	 * 
	 * @param path
	 *            to create
	 * @param sftp
	 *            connection
	 * @throws SftpException
	 *             on error
	 */
	protected void createRemoteRepo(String path, Sftp sftp)
			throws SftpException {
		try {
			sftp.cd(path);
		} catch (SftpException e) {
			StringTokenizer tokenizer = new StringTokenizer(path, "/");
			while (tokenizer.hasMoreTokens()) {
				String folder = tokenizer.nextToken();
				if (sftp.fileDoesNotExist(folder)) {
					sftp.mkdir(folder);
				}
				sftp.cd(folder);
				if (sftp.fileDoesNotExist(ModelHelper.TYPE.ARTIFACT
						.getFilename())) {
					RepositoryDocument repositoryDocument = modelHelper
							.newRepositoryDocument(folder,
									ModelHelper.TYPE.METADATA);
					updateCompositeMetafiles(sftp, repositoryDocument);
				}
			}
		}
	}

}
