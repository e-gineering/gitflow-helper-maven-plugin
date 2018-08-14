package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.it.Verifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

@RunWith(BlockJUnit4ClassRunner.class)
public class OtherBranchIT extends AbstractIntegrationTest {
	@Test
	public void featureSnapshotSemVer() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/feature/my-feature-branch", "5.0.0-SNAPSHOT");
		try {
			verifier.getCliOptions().add("-Dgitflow.force.other.deploy=semver");
			verifier.executeGoal("deploy");

			verifier.verifyTextInLog("Artifact versions updated with semVer build metadata: +origin-feature-my-feature-branch");
			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}

	@Test
	public void featureSnapshotOverwrite() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/feature/my-feature-branch", "5.0.0-SNAPSHOT");
		try {
			verifier.getCliOptions().add("-Dgitflow.force.other.deploy=overwrite");
			verifier.executeGoal("deploy");

			verifier.verifyTextInLog("DANGER! DANGER, WILL ROBINSON!");
			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}

	@Test
	public void featureSemVer() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/feature/my-feature-branch.with.other.identifiers", "5.0.1");
		try {
			verifier.getCliOptions().add("-Dgitflow.force.other.deploy=semver");
			verifier.executeGoal("deploy");

			verifier.verifyTextInLog("Artifact versions updated with semVer build metadata: +origin-feature-my-feature-branch.with.other.identifiers");
			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}

	@Test
	public void featureOverwrite() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/feature/my-feature-branch", "5.0.1");
		try {
			verifier.getCliOptions().add("-Dgitflow.force.other.deploy=overwrite");
			verifier.executeGoal("deploy");

			verifier.verifyTextInLog("DANGER! DANGER, WILL ROBINSON!");
			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}

}
