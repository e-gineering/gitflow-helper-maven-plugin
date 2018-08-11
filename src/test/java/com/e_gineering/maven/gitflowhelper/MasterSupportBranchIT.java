package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;

public class MasterSupportBranchIT extends AbstractIntegrationTest {
	private static final String PROMOTION_FAILED_MESSAGE = "Promotion Deploy from origin/master allowed something to Compile.";

	public void testReleaseVersionSuccess() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/master", "1.0.0");

		// Allow -SNAPSHOT builds of the plugin to succeed while still asserting the version match.
		verifier.getCliOptions().add("-DenforceNonSnapshots=false");
		verifier.executeGoal("gitflow-helper:enforce-versions");

		verifier.verifyErrorFreeLog();
		verifier.verifyTextInLog("GitBranchInfo:");

		verifier.resetStreams();
	}

	public void testSnapshotVersionFailure() throws Exception {
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

	public void testSnapshotPluginFailure() throws Exception {
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

	public void testPromotionOfRelease() throws Exception {
		// Create a release version and get it deployed.
		Verifier verifier = createVerifier("/project-stub", "origin/release/1.0.0", "1.0.0");

		// Allow -SNAPSHOT builds of the plugin to succeed while still asserting the version match.
		verifier.getCliOptions().add("-DenforceNonSnapshots=false");

		verifier.executeGoal("deploy");

		verifier.verifyErrorFreeLog();

		verifier.resetStreams();

		// Promote (deploy) from /origin/master
		verifier = createVerifier("/project-stub", "origin/master", "1.0.0");

		// Allow -SNAPSHOT builds of the plugin to succeed while still asserting the version match.
		verifier.getCliOptions().add("-DenforceNonSnapshots=false");


		verifier.executeGoal("deploy");

		try {
			verifier.verifyTextInLog("Compiling");
			throw new VerificationException(PROMOTION_FAILED_MESSAGE);
		} catch (VerificationException ve) {
			if (ve.getMessage().equals(PROMOTION_FAILED_MESSAGE)) {
				throw ve;
			}
			// Otherwise, it's the VerificationException from looking for "Compiling", and that's expected to fail.
		}

		verifier.verifyTextInLog("gitflow-helper-maven-plugin: Enabling MasterPromoteExtension. GIT_BRANCH: [origin/master] matches masterBranchPattern");
		verifier.verifyTextInLog("[INFO] Setting release artifact repository to: [releases]");
		verifier.verifyTextInLog("[INFO] Resolving & Reattaching existing artifacts from stageDeploymentRepository [test-releases]");
		verifier.verifyErrorFreeLog();
	}
}
