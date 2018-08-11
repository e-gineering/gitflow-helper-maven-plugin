package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Test;

public class EnforceVersionsIT extends AbstractIntegrationTest {

	@Test()
	public void testMasterReleaseVersion() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/master", "1.0.0");

		// Allow -SNAPSHOT builds of the plugin to succeed while still asserting the version match.
		verifier.getCliOptions().add("-DenforceNonSnapshots=false");
		verifier.executeGoal("gitflow-helper:enforce-versions");

		verifier.verifyErrorFreeLog();
		verifier.verifyTextInLog("GitBranchInfo:");

		verifier.resetStreams();
	}

	public void testMasterReleaseSnapshotFailure() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/master", "1.0.0-SNAPSHOT");

		try {
			verifier.executeGoal("gitflow-helper:enforce-versions");
			assertTrue(false); // Should never get here.
		} catch (VerificationException ve) {
			// Expected outcome
			assertTrue(true);
		} finally {
			verifier.verifyTextInLog("GitBranchInfo:");
			verifier.verifyTextInLog("The maven project or one of its parents is currently a snapshot version.");

			verifier.resetStreams();
		}
	}

	@Test()
	public void testMasterReleaseSnapshotPluginFailure() throws Exception {
		if (System.getProperty("project.version", "").endsWith("-SNAPSHOT")) {
			Verifier verifier = createVerifier("/project-stub", "origin/master", "1.0.0");

			try {
				verifier.executeGoal("gitflow-helper:enforce-versions");
				assertTrue(false);// This should fail the test.
			} catch (VerificationException ve) {
				assertTrue(true);
			} finally {
				verifier.verifyTextInLog("GitBranchInfo:");
				verifier.verifyTextInLog("The maven project has the following SNAPSHOT plugin dependencies:");

				verifier.resetStreams();
			}
		} else {
			assertTrue(true);
		}
	}

	@Test
	public void testSupportReleaseVersion() {

	}
}
