package org.eclipse.maven.mojo.updatesite;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import org.eclipse.maven.mojo.updatesite.sftp.Sftp;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

/**
 * Goal which touches a timestamp file.
 * 
 * @goal deploy
 * 
 * @phase install
 */
public class DeployMojo extends AbstractMojo {

	/**
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
	 * @parameter
	 */
	private List<Site> sites;
	/**
	 * @parameter default-value="${user.home}/.ssh/known_hosts"
	 * @required
	 */
	private String knownHost;

	/**
	 * @parameter default-value="${user.home}/.ssh/id_rsa"
	 * @required
	 */
	private String identity;

	/**
	 * Location of the file.
	 * 
	 * @parameter expression="${project.build.directory}/site"
	 * @required
	 */
	private File siteDirectory;

	private ModelHelper modelHelper = new ModelHelper();

	static final Pattern SFTP_PATTERN = Pattern
			.compile("^sftp://([^/^:)]+)\\:?(\\d+)?(.*)$");

	public void execute() throws MojoExecutionException {

		for (Site site : sites) {
			deploy(site);
		}

	}

	private void deploy(Site site) throws MojoExecutionException {

		Matcher matcher = DeployMojo.SFTP_PATTERN.matcher(site.getBaseURL());
		if (!matcher.matches()) {
			throw new MojoExecutionException(site.getBaseURL()
					+ " is not a valid sftp url");
		}
		String host = matcher.group(1);
		String portAsString = matcher.group(2);
		int port;
		if (portAsString == null) {
			port = 22;
		} else {
			port = Integer.parseInt(portAsString);
		}
		String basePath = matcher.group(3);

		Sftp sftp = new Sftp(knownHost, identity);

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

			updloadFile(siteDirectory, childLocation, sftp);

			RepositoryDocument repositoryDocument;

			if (newRepo || sftp.fileDoesNotExist("compositeContent.xml")) {
				repositoryDocument = modelHelper.newRepositoryDocument(
						site.getName(), ModelHelper.TYPE.ARTIFACT);
			} else {

				InputStream inputStream = sftp.get("compositeContent.xml");

				repositoryDocument = modelHelper
						.parseCompositeContent(inputStream);
			}

			modelHelper.updateChild(repositoryDocument,
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

	protected void updateCompositeMetafiles(Sftp sftp,
			RepositoryDocument repositoryDocument) throws SftpException {
		putRepositoryFile(sftp, repositoryDocument, ModelHelper.TYPE.ARTIFACT);
		putRepositoryFile(sftp, repositoryDocument, ModelHelper.TYPE.METADATA);

	}

	private void putRepositoryFile(Sftp sftp,
			RepositoryDocument repositoryDocument, TYPE type)
			throws SftpException {
		repositoryDocument.getRepository().setType(type.asString);
		sftp.put(modelHelper.getInputStream(repositoryDocument, type),
				type.filename);
	}

	private void updateParentRepo(Site site, Sftp sftp) throws SftpException {
		String currentRepoName = sftp.getCurrentFolderName();
		sftp.cd("..");
		String pwd = sftp.pwd();
		getLog().info("Updating " + currentRepoName + " in " + pwd);
		if (sftp.fileExists("compositeArtifacts.xml")) {
			InputStream inputStream = sftp.get("compositeContent.xml");

			RepositoryDocument repositoryDocument = modelHelper
					.parseCompositeContent(inputStream);
			boolean b = modelHelper.updateChild(repositoryDocument,
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

	private void updloadFile(File folder, String dst, Sftp sftp)
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
				updloadFile(file, path, sftp);
			} else {
				InputStream inputStream = new FileInputStream(file);
				sftp.put(inputStream, path);
				inputStream.close();
			}

		}

	}

	protected String initChildLocation(Sftp sftp) throws SftpException {
		String childLocation = mavenProject.getVersion();

		if (sftp.fileDoesNotExist(childLocation)) {
			sftp.mkdir(childLocation);
			getLog().info("Create new child localtion: " + childLocation);
		} else {
			sftp.rmtree(childLocation, false);
		}
		return childLocation;
	}

	protected boolean initRepository(Site site, String basePath, Sftp sftp)
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

	protected void createRemotePath(String path, Sftp sftp)
			throws SftpException {
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
				if (sftp.fileDoesNotExist(ModelHelper.TYPE.ARTIFACT.filename)) {
					RepositoryDocument repositoryDocument = modelHelper
							.newRepositoryDocument(folder,
									ModelHelper.TYPE.METADATA);
					updateCompositeMetafiles(sftp, repositoryDocument);
				}
			}
		}
	}

}
