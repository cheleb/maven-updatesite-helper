package org.eclipse.maven.mojo.updatesite;

import java.io.InputStream;
import java.util.Vector;

import noNamespace.RepositoryDocument;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.ChannelSftp.LsEntry;

public class JschTest {
	public static void main(String[] args) throws Throwable {

		JSch jsch = new JSch();
		
    	String host="hortus";
       
        String user="administrator";

        host=host.substring(host.indexOf('@')+1);
        Session session=jsch.getSession(user, host, 22);
        
        String userhome = System.getProperty("user.home");

        jsch.setKnownHosts(userhome+"/.ssh/known_hosts");
        jsch.addIdentity(userhome+"/.ssh/id_rsa", "che1e8AtGridDynamics");

        
        
//        UserInfo ui=new MyUserInfo();
//        session.setUserInfo(ui);
        session.connect();
        
        Channel channel=session.openChannel("sftp");
        channel.connect();
        ChannelSftp c=(ChannelSftp)channel;
        
        String path = "/var/www/updates/agilent/m2e-alfresco";
        Vector ls = c.ls(path);
        for (Object object : ls) {
			if (object instanceof LsEntry) {
				LsEntry lsEntry = (LsEntry) object;
				System.out.println(lsEntry.getFilename());
				
			}
		}
        
		c.cd(path);
        
        InputStream inputStream = c.get("compositeContent.xml");
        if(inputStream!=null) {
        	RepositoryDocument parseCompositeContent = ModelHelper.parseCompositeContent(inputStream);
        	String name = parseCompositeContent.getRepository().getName();
        	System.out.println("name: " + name);
        	System.out.println(ModelHelper.updateChild(parseCompositeContent, "0.0.1"));
        }
		
	}
}
