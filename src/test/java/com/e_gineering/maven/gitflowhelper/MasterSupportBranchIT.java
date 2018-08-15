package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

import java.util.Arrays;

@RunWith(BlockJUnit4ClassRunner.class)
public class MasterSupportBranchIT extends AbstractIntegrationTest {
	private static final String PROMOTION_FAILED_MESSAGE = "Promotion Deploy from origin/master allowed something to Compile.";

	@Test
	public void releaseVersionSuccess() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/master", "1.0.0");

		verifier.executeGoal("gitflow-helper:enforce-versions");

		verifier.verifyErrorFreeLog();
		verifier.verifyTextInLog("GitBranchInfo:");

		verifier.resetStreams();
	}

	@Test(expected = VerificationException.class)
	public void snapshotVersionFailure() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/master", "1.0.0-SNAPSHOT");

		try {
			verifier.executeGoal("gitflow-helper:enforce-versions");
		} finally {
			verifier.verifyTextInLog("GitBranchInfo:");
			verifier.verifyTextInLog("The maven project or one of its parents is currently a snapshot version.");

			verifier.resetStreams();
		}
	}

	@Test
	public void promotionOfRelease() throws Exception {
		// Create a release version and get it deployed.
		Verifier verifier = createVerifier("/project-stub", "origin/release/1.0.0", "1.0.0");

		verifier.executeGoal("deploy");

		verifier.verifyErrorFreeLog();
		verifier.resetStreams();

		// Promote (deploy) from /origin/master
		verifier = createVerifier("/project-stub", "origin/master", "1.0.0");

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
		verifier.verifyTextInLog("[INFO] Resolving & Reattaching existing artifacts from stageDeploymentRepository [test-releases");
		verifier.verifyErrorFreeLog();
		verifier.resetStreams();
	}

	@Test
	public void dontPruneExplicitlyInvokedPlugins() throws Exception {
		// Create a release version and get it deployed.
		Verifier verifier = createVerifier("/project-stub", "origin/release/1.1.0", "1.1.0");

		verifier.executeGoal("deploy");

		verifier.verifyErrorFreeLog();
		verifier.resetStreams();

		// Promote (deploy) from /origin/master
		verifier = createVerifier("/project-stub", "origin/master", "1.1.0");

		verifier.executeGoals(Arrays.asList("jar:jar", "deploy"));
		try {
			verifier.verifyTextInLog("Compiling");
			throw new VerificationException(PROMOTION_FAILED_MESSAGE);
		} catch (VerificationException ve) {
			if (ve.getMessage().equals(PROMOTION_FAILED_MESSAGE)) {
				throw ve;
			}
			// Otherwise, it's the VerificationException from looking for "Compiling", and that's expected to fail.
		}

		verifier.verifyTextInLog("Building jar:"); // This should still be there.
		verifier.verifyTextInLog("gitflow-helper-maven-plugin: Enabling MasterPromoteExtension. GIT_BRANCH: [origin/master] matches masterBranchPattern");
		verifier.verifyTextInLog("[INFO] Setting release artifact repository to: [releases]");
		verifier.verifyTextInLog("[INFO] Resolving & Reattaching existing artifacts from stageDeploymentRepository [test-releases");
		verifier.verifyErrorFreeLog();
		verifier.resetStreams();
	}
}
