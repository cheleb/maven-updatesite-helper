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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import noNamespace.RepositoryDocument;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
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
	 * @parameter
	 * @required
	 */
	private String repositoryName;

	/**
	 * @parameter
	 * @required
	 */
	private String parentURL;
	
	
	 /** The current user system settings for use in Maven.
     *
     * @parameter expression="${settings}"
     * @required
     * @readonly
     */
    private Settings settings;


    /**
     * @parameter
     */
    private String serverId;

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

		Matcher matcher = DeployMojo.SFTP_PATTERN.matcher(parentURL);
		if (!matcher.matches()) {
			throw new MojoExecutionException(parentURL
					+ " is not a valid sftp url");
		}
		String host = matcher.group(1);
		String portAsString = matcher.group(2);
		int port;
		if(portAsString==null) {
			port=22;
		}else {
			port = Integer.parseInt(portAsString);
		}
		String parentPath = matcher.group(3);

		Sftp sftp = new Sftp(knownHost, identity);

		Server server = settings.getServer(serverId);
		if(server==null) {
			throw new MojoExecutionException("Could not find serverId: \"" + serverId + "\"");
		}
		
		String pass = server.getPassphrase();
		
		if(pass ==null) {
			throw new MojoExecutionException("Passphrase could not be null");
		}
		
		String user = server.getUsername();
		
		
		
		try {

			sftp.openSession(user, pass, host, port);

			boolean newRepo = initRepository(parentPath, sftp);

			String childLocation = initChildLocation(sftp);

			updloadFile(siteDirectory, childLocation, sftp);

			RepositoryDocument repositoryDocument;

			if (newRepo || sftp.fileDoesNotExist("compositeContent.xml")) {
				repositoryDocument = modelHelper
						.newRepositoryDocument(repositoryName, ModelHelper.TYPE.ARTIFACT);
			} else {

				InputStream inputStream = sftp.get("compositeContent.xml");

				repositoryDocument = modelHelper
						.parseCompositeContent(inputStream);
			}

			modelHelper.updateChild(repositoryDocument,
					mavenProject.getVersion());

			sftp.put(modelHelper.getInputStream(repositoryDocument,
					ModelHelper.TYPE.ARTIFACT), "compositeArtifacts.xml");
			repositoryDocument.getRepository().setType(ModelHelper.TYPE.METADATA.asString);
			sftp.put(modelHelper.getInputStream(repositoryDocument,
					ModelHelper.TYPE.METADATA), "compositeContent.xml");

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

	protected boolean initRepository(String parentPath, Sftp sftp)
			throws SftpException {
		sftp.cd(parentPath);
		boolean newRepo = false;
		if (sftp.fileDoesNotExist(repositoryName)) {
			sftp.mkdir(repositoryName);
			getLog().info("Create new repo: " + repositoryName);
			newRepo = true;
		}

		sftp.cd(repositoryName);
		return newRepo;
	}

}
