package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.it.Verifier;
import org.junit.Assume;
import org.junit.internal.AssumptionViolatedException;

public class DevelopBranchIT extends AbstractIntegrationTest {
	/**
	 * Non-snapshot versions on the develop branch should fail.
	 */
	public void testNonSnapshotDeployFails() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/develop", "1.0.0");
		try {
			verifier.executeGoal("deploy");
		} catch (Exception ex) {
			verifier.verifyTextInLog("The current git branch: [origin/develop] is detected as a SNAPSHOT-type branch, and expects a maven project version ending with -SNAPSHOT. The maven project version found was: [1.0.0]");
		}
		verifier.resetStreams();
	}

	/**.
	 * Snapshot versions on the develop branch should pass
	 * @throws Exception
	 */
	public void testSnapshotDeploySuccess() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/develop", "1.0.0-SNAPSHOT");

		verifier.executeGoal("deploy");

		verifier.verifyErrorFreeLog();

		verifier.resetStreams();
	}

	/**
	 * Attaching existing artifacts from the develop branch should pass.
	 *
	 * @throws Exception
	 */
	public void testAttachExistingArtifacts() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/develop", "1.0.0-SNAPSHOT");

		verifier.executeGoal("deploy");

		verifier.verifyErrorFreeLog();

		verifier.resetStreams();


		// New verifier to attach existing artifacts
		verifier = createVerifier("/project-stub", "origin/develop", "1.0.0-SNAPSHOT");

		verifier.executeGoal("gitflow-helper:attach-deployed");

		verifier.verifyErrorFreeLog();
	}
}
