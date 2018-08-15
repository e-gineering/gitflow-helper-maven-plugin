package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.it.Verifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

@RunWith(BlockJUnit4ClassRunner.class)
public class OtherBranchIT extends AbstractIntegrationTest {
	@Test
	public void featureSnapshotSemVer() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/feature/poc/my-feature-branch", "5.0.0-SNAPSHOT");
		try {
			verifier.executeGoal("deploy");

			verifier.verifyTextInLog("Artifact versions updated with semVer build metadata: +origin-feature-poc-my-feature-branch-SNAPSHOT");
			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}

	@Test
	public void featureSemVer() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/feature/poc/my-feature-branch.with.other.identifiers", "5.0.1");
		try {
			verifier.executeGoal("deploy");

			verifier.verifyTextInLog("Artifact versions updated with semVer build metadata: +origin-feature-poc-my-feature-branch.with.other.identifiers-SNAPSHOT");
			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}

	@Test
	public void noOtherDeployMatch() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/feature/my-feature-branch", "5.0.1-SNAPSHOT");
		try {
			verifier.executeGoal("deploy");

			verifier.verifyTextInLog("Un-Setting artifact repositories.");
			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}

	@Test
	public void automagicVersionDependenciesResolve() throws Exception {
		// Create a -SNAPSHOT of the project-stub.
		Verifier verifier = createVerifier("/project-stub", "origin/feature/poc/long-running", "2.0.0");
		try {
			verifier.executeGoal("deploy");

			verifier.verifyTextInLog("Artifact versions updated with semVer build metadata: +origin-feature-poc-long-running-SNAPSHOT");
			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}

		// Create a -SNAPSHOT of the project-alt1-stub that depends upon the other project's automagic version.
		verifier = createVerifier("/project-alt1-stub", "origin/feature/poc/long-running", "2.0.0");
		try {
			verifier.getCliOptions().add("-Ddependency.stub.version=2.0.0+origin-feature-poc-long-running-SNAPSHOT");
			verifier.getCliOptions().add("-Dplugin.stub.version=2.0.0+origin-feature-poc-long-running-SNAPSHOT");

			verifier.executeGoal("deploy");
			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}
}
