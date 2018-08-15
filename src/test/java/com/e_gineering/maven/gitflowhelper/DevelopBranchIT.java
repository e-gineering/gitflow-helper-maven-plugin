package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

@RunWith(BlockJUnit4ClassRunner.class)
public class DevelopBranchIT extends AbstractIntegrationTest {
	/**
	 * Non-snapshot versions on the develop branch should fail.
	 */
	@Test(expected = VerificationException.class)
	public void nonSnapshotDeployFails() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/develop", "1.0.0");
		try {
			verifier.executeGoal("deploy");
		} catch (Exception ex) {
			verifier.verifyTextInLog("The current git branch: [origin/develop] is detected as a SNAPSHOT-type branch, and expects a maven project version ending with -SNAPSHOT. The maven project version found was: [1.0.0]");
			throw ex;
		} finally {
			verifier.resetStreams();
		}
	}

	/**.
	 * Snapshot versions on the develop branch should pass
	 * @throws Exception
	 */
	@Test
	public void snapshotDeploySuccess() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/develop", "1.0.0-SNAPSHOT");

		try {
			verifier.executeGoal("deploy");

			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}

	/**
	 * Attaching existing artifacts from the develop branch should pass.
	 *
	 * @throws Exception
	 */
	@Test
	public void attachExistingArtifacts() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/develop", "1.0.0-SNAPSHOT");

		try {
			verifier.executeGoal("deploy");

			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}


		// New verifier to attach existing artifacts
		verifier = createVerifier("/project-stub", "origin/develop", "1.0.0-SNAPSHOT");

		try {
			verifier.executeGoal("gitflow-helper:attach-deployed");

			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}
}
