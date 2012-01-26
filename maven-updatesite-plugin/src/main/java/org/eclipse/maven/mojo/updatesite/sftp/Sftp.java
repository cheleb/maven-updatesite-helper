package org.eclipse.maven.mojo.updatesite.sftp;

import java.io.InputStream;
import java.util.List;

import org.eclipse.maven.mojo.updatesite.logger.Logger;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

public class Sftp {

	private final String knownHost;
	private final String identity;
	private ChannelSftp sftpChannel;
	private Session session;
	private Logger logger;

	public Sftp(Logger logger, String knownHost, String identity) {
		this.logger = logger;
		this.knownHost = knownHost;
		this.identity = identity;
	}

	public void openSession(String user, String pass, String host, int port)
			throws JSchException {
		JSch jsch = new JSch();

		session = jsch.getSession(user, host, port);

		jsch.setKnownHosts(knownHost);
		jsch.addIdentity(identity, pass);

		session.connect();

		Channel channel = session.openChannel("sftp");
		channel.connect();

		sftpChannel = (ChannelSftp) channel;

	}

	public void cd(String path) throws SftpException {
		sftpChannel.cd(path);

	}

	@SuppressWarnings("unchecked")
	public List<LsEntry> ls(String path) throws SftpException {
		return sftpChannel.ls(path);
	}

	public void rm(String path) throws SftpException {
		sftpChannel.rm(path);
	}

	public void rmdir(String path) throws SftpException {
		sftpChannel.rmdir(path);
	}

	public boolean fileExists(String path) throws SftpException {
		String dir;
		String filename;
		int i = path.lastIndexOf('/');
		if (i > 0) {
			dir = path.substring(0, i);
			filename = path.substring(i + 1);
		} else {
			dir = ".";
			filename = path;
		}
		List<LsEntry> ls = ls(dir);

		for (Object object : ls) {
			if (object instanceof LsEntry) {
				LsEntry lsEntry = (LsEntry) object;
				if (lsEntry.getFilename().equals(filename)) {
					return true;
				}

			}
		}
		return false;
	}

	public boolean fileDoesNotExist(String filename) throws SftpException {
		return !fileExists(filename);
	}

	public void mkdir(String path) throws SftpException {
		sftpChannel.mkdir(path);

	}

	public InputStream get(String path) throws SftpException {
		return sftpChannel.get(path);
	}

	public void put(InputStream inputStream, String dst) throws SftpException {
		sftpChannel.put(inputStream, dst);

	}

	public void disconnect() {
		if (sftpChannel != null) {
			sftpChannel.disconnect();
		}
		if (session != null) {
			session.disconnect();
		}
	}

	public void rmtree(String folder, boolean deleteFolder)
			throws SftpException {
		logger.info("Deleting content of: " + folder);
		List<LsEntry> ls = ls(folder);
		for (LsEntry lsEntry : ls) {
			if (".".equals(lsEntry.getFilename())
					|| "..".equals(lsEntry.getFilename())) {
				continue;
			}
			String filename = folder + "/" + lsEntry.getFilename();
			if (lsEntry.getAttrs().isDir()) {
				rmtree(filename, deleteFolder);
				if (deleteFolder) {
					sftpChannel.rmdir(filename);
				}
			} else {
				logger.info("Deleting: " + filename);
				sftpChannel.rm(filename);
			}
		}

	}

	public String getCurrentFolderName() throws SftpException {
		String path = sftpChannel.pwd();
		int i = path.lastIndexOf('/');
		if (i > 0) {
			return path.substring(i + 1);
		}
		return path;

	}

	public String pwd() throws SftpException {
		return sftpChannel.pwd();
	}

}
