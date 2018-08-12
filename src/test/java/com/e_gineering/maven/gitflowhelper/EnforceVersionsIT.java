package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

@RunWith(BlockJUnit4ClassRunner.class)
public class EnforceVersionsIT extends AbstractIntegrationTest {

	@Test(expected = VerificationException.class)
	public void versionEqualsMismatch() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/release/1.0.0", "1.2.0");

		try {
			verifier.executeGoal("gitflow-helper:enforce-versions");
		} finally {
			verifier.resetStreams();
		}
	}

	@Test(expected = VerificationException.class)
	public void versionStartswithMismatch() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/release/1.2", "1.3.0");
		verifier.getCliOptions().add("-DreleaseBranchMatchType=startsWith");

		try {
			verifier.executeGoal("gitflow-helper:enforce-versions");
		} finally {
			verifier.resetStreams();
		}
	}

	@Test
	public void versionStartswithMatch() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/release/1.2", "1.2.4");
		verifier.getCliOptions().add("-DreleaseBranchMatchType=startsWith");

		try {
			verifier.executeGoal("gitflow-helper:enforce-versions");
		} finally {
			verifier.resetStreams();
		}
	}

	@Test
	public void supportsStartswithMatch() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/support/1.2", "1.2.4");

		try {
			verifier.executeGoal("gitflow-helper:enforce-versions");
		} finally {
			verifier.resetStreams();
		}
	}

	@Test(expected = VerificationException.class)
	public void supportsStartswithMismatch() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/support/1.2", "1.3.2");

		try {
			verifier.executeGoal("gitflow-helper:enforce-versions");
		} finally {
			verifier.resetStreams();
		}
	}

	@Test
	public void featureAllowsSnapshot() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/feature/aFeatureBranch", "1.0.0-SNAPSHOT");

		try {
			verifier.executeGoal("gitflow-helper:enforce-versions");
			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}

	@Test
	public void featureAllowsNonSnapshot() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/feature/aFeatureBranch", "1.0.0");

		try {
			verifier.executeGoal("gitflow-helper:enforce-versions");
			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}

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
