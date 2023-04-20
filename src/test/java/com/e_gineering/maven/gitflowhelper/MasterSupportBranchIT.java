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
	public static final String FILTERED_EXECUTION = "Filtered execution should not be executing";

	@Test
	public void releaseVersionSuccess() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/master", "1.0.0");

		try {
			verifier.executeGoal("gitflow-helper:enforce-versions");

			verifier.verifyErrorFreeLog();
			verifier.verifyTextInLog("GitBranchInfo:");
		} finally {
			verifier.resetStreams();
		}
	}

	@Test(expected = VerificationException.class)
	public void snapshotVersionFailure() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/master", "1.0.0-SNAPSHOT");

		try {
			verifier.executeGoal("gitflow-helper:enforce-versions");
		} finally {
		    try {
				verifier.verifyTextInLog("GitBranchInfo:");
				verifier.verifyTextInLog("The maven project or one of its parents is currently a snapshot version.");
			} finally {
				verifier.resetStreams();
			}
		}
	}

	@Test
	public void promotionOfRelease() throws Exception {
		// Create a release version and get it deployed.
		Verifier verifier = createVerifier("/project-stub", "origin/release/1.0.0", "1.0.0");

		try {
			verifier.executeGoal("deploy");

			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}

		// Promote (deploy) from /origin/master
		verifier = createVerifier("/project-stub", "origin/master", "1.0.0");

		try {
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

			try {
				// The flatten-maven-plugin should not activate, fail if it does.
				verifier.verifyTextInLog("Generating flattened POM of project");
				throw new VerificationException(PROMOTION_FAILED_MESSAGE);
			} catch (VerificationException ve) {
				if (ve.getMessage().equals(PROMOTION_FAILED_MESSAGE)) {
					throw ve;
				}
			}

			verifier.verifyTextInLog(
				"gitflow-helper-maven-plugin: Enabling MasterPromoteExtension. GIT_BRANCH: [origin/master] matches masterBranchPattern");
			verifier.verifyTextInLog("[INFO] Setting release artifact repository to: [releases]");
			verifier.verifyTextInLog(
				"[INFO] Resolving & Reattaching existing artifacts from stageDeploymentRepository [test-releases");
			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}

	@Test
	public void dontPruneExplicitlyInvokedPlugins() throws Exception {
		// Create a release version and get it deployed.
		Verifier verifier = createVerifier("/project-stub", "origin/release/1.1.0", "1.1.0");

		try {
			verifier.executeGoal("deploy");

			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}

		// Promote (deploy) from /origin/master
		verifier = createVerifier("/project-stub", "origin/master", "1.1.0");

		try {
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
			verifier.verifyTextInLog(
				"gitflow-helper-maven-plugin: Enabling MasterPromoteExtension. GIT_BRANCH: [origin/master] matches masterBranchPattern");
			verifier.verifyTextInLog("[INFO] Setting release artifact repository to: [releases]");
			verifier.verifyTextInLog(
				"[INFO] Resolving & Reattaching existing artifacts from stageDeploymentRepository [test-releases");
			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}

	@Test
	public void dontPruneExplicitlyConfiguredPlugins() throws Exception {
		// Create a release version and get it deployed.
		Verifier verifier = createVerifier("/project-stub", "origin/release/1.2.0", "1.2.0");

		try {
			verifier.executeGoal("deploy");

			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}

		// Promote (deploy) from /origin/master
		verifier = createVerifier("/project-stub", "origin/master", "1.2.0");
		// Activate a profile to enable the retainPlugins configuration
		verifier.addCliOption("-Pretain-configuration");
		try {
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

			verifier.verifyTextInLog("Generating flattened POM of project"); // This should still be there.
			verifier.verifyTextInLog("This execution must be retained"); // This should also still be there
			try {
				verifier.verifyTextInLog("This execution must be filtered"); // This should not be here
				throw new VerificationException(FILTERED_EXECUTION);
			} catch (VerificationException ve) {
				if (ve.getMessage().equals(FILTERED_EXECUTION)) {
					throw ve;
				}
			}
			verifier.verifyTextInLog(
					"gitflow-helper-maven-plugin: Enabling MasterPromoteExtension. GIT_BRANCH: [origin/master] matches masterBranchPattern");
			verifier.verifyTextInLog("[INFO] Setting release artifact repository to: [releases]");
			verifier.verifyTextInLog(
					"[INFO] Resolving & Reattaching existing artifacts from stageDeploymentRepository [test-releases");
			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}
}
