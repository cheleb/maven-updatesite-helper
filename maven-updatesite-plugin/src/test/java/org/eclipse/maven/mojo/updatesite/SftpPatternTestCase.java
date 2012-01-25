package org.eclipse.maven.mojo.updatesite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.regex.Matcher;

import org.junit.Test;

public class SftpPatternTestCase {

	@Test
	public void test() {
		Matcher matcher = DeployMojo.SFTP_PATTERN.matcher("sftp://olivier@myhost/my/path/to");
		assertTrue("Should match!", matcher.matches());
		assertEquals("Should match!", "olivier", matcher.group(1));
		assertEquals("Should match!", "myhost", matcher.group(2));
		assertEquals("Should match!", "/my/path/to", matcher.group(3));
		
	}

}
