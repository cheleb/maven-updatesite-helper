package org.eclipse.maven.mojo.updatesite;

import com.jcraft.jsch.UserInfo;

public class MyUserInfo implements UserInfo {

	public String getPassphrase() {
		// TODO Auto-generated method stub
		return "11Agilent!";
	}

	public String getPassword() {
		// TODO Auto-generated method stub
		return "11Agilent!";
	}

	public boolean promptPassphrase(String arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean promptPassword(String arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean promptYesNo(String arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	public void showMessage(String arg0) {
		// TODO Auto-generated method stub

	}

}
