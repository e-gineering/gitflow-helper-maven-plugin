package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

@RunWith(BlockJUnit4ClassRunner.class)
public class EnforceVersionsIT extends AbstractIntegrationTest {

	@Test
	public void dependencySuccesses() throws Exception {
		// Stage the repository with version 1.0.0 of the stub.

		// Create a release version and get it deployed.
		Verifier verifier = createVerifier("/project-stub", "origin/release/1.0.0", "1.0.0");

		verifier.executeGoal("deploy");

		verifier.verifyErrorFreeLog();
		verifier.resetStreams();

		// Promote (deploy) from /origin/master
		verifier = createVerifier("/project-stub", "origin/master", "1.0.0");
		verifier.executeGoal("deploy");

		verifier.verifyErrorFreeLog();
		verifier.resetStreams();

		// Build a project that depends upon the upstream project.
		verifier = createVerifier("/project-alt1-stub", "origin/release/1.0.0", "1.0.0");
		verifier.getCliOptions().add("-Ddependency.stub.version=1.0.0");
		verifier.getCliOptions().add("-Dplugin.stub.version=1.0.0");

		verifier.executeGoal("deploy");

		verifier.verifyErrorFreeLog();
		verifier.resetStreams();

		verifier = createVerifier("/project-alt1-stub", "origin/master", "1.0.0");
		verifier.getCliOptions().add("-Ddependency.stub.version=1.0.0");
		verifier.getCliOptions().add("-Dplugin.stub.version=1.0.0");

		verifier.executeGoal("deploy");

		verifier.verifyTextInLog("gitflow-helper-maven-plugin: Enabling MasterPromoteExtension. GIT_BRANCH: [origin/master] matches masterBranchPattern");
		verifier.verifyTextInLog("[INFO] Setting release artifact repository to: [releases]");
		verifier.verifyTextInLog("[INFO] Resolving & Reattaching existing artifacts from stageDeploymentRepository [test-releases]");
		verifier.verifyErrorFreeLog();
		verifier.resetStreams();
	}

	@Test(expected= VerificationException.class)
	public void dependencySnapshotFail() throws Exception {
		// Stage the repository with version 1.0.0 of the stub.

		// Create a release version and get it deployed.
		Verifier verifier = createVerifier("/project-stub", "origin/release/1.0.0", "1.0.0");

		verifier.executeGoal("deploy");

		verifier.verifyErrorFreeLog();
		verifier.resetStreams();

		// Promote (deploy) from /origin/master
		verifier = createVerifier("/project-stub", "origin/master", "1.0.0");
		verifier.executeGoal("deploy");

		verifier.verifyErrorFreeLog();
		verifier.resetStreams();

		// Build a project that depends upon the upstream project.
		verifier = createVerifier("/project-alt1-stub", "origin/release/1.0.0", "1.0.0");
		verifier.getCliOptions().add("-Ddependency.stub.version=1.0.0-SNAPSHOT");
		verifier.getCliOptions().add("-Dplugin.stub.version=1.0.0");

		try {
			verifier.executeGoal("deploy");
		} finally {
			verifier.resetStreams();
		}
	}

	@Test(expected=VerificationException.class)
	public void pluginSnapshotFail() throws Exception {
		// Stage the repository with version 1.0.0 of the stub.

		// Create a release version and get it deployed.
		Verifier verifier = createVerifier("/project-stub", "origin/release/1.0.0", "1.0.0");

		verifier.executeGoal("deploy");

		verifier.verifyErrorFreeLog();
		verifier.resetStreams();

		// Promote (deploy) from /origin/master
		verifier = createVerifier("/project-stub", "origin/master", "1.0.0");
		verifier.executeGoal("deploy");

		verifier.verifyErrorFreeLog();
		verifier.resetStreams();

		// Build a project that depends upon the upstream project.
		verifier = createVerifier("/project-alt1-stub", "origin/release/1.0.0", "1.0.0");
		verifier.getCliOptions().add("-Ddependency.stub.version=1.0.0");
		verifier.getCliOptions().add("-Dplugin.stub.version=1.0.0-SNAPSHOT");

		try {
			verifier.executeGoal("deploy");
		} finally {
			verifier.resetStreams();
		}
	}
}
