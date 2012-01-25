package org.eclipse.maven.mojo.updatesite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.regex.Matcher;

import org.junit.Test;

public class SftpPatternTestCase {

	@Test
	public void test() {
		{
			Matcher matcher = DeployMojo.SFTP_PATTERN
					.matcher("sftp://myhost/my/path/to");
			assertTrue("Should match!", matcher.matches());
			assertEquals("Should match!", "myhost", matcher.group(1));
			assertEquals("Should match!", null, matcher.group(2));
			assertEquals("Should match!", "/my/path/to", matcher.group(3));
		}
		{
			Matcher matcherWithPort = DeployMojo.SFTP_PATTERN
					.matcher("sftp://myhost:56/my/path/to");
			assertTrue("Should match!", matcherWithPort.matches());
			assertEquals("Should match!", "myhost", matcherWithPort.group(1));
			assertEquals("Should match!", "56", matcherWithPort.group(2));
			assertEquals("Should match!", "/my/path/to", matcherWithPort.group(3));
		}

	}

}
