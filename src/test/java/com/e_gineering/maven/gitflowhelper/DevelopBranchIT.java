package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.it.Verifier;
import org.junit.Assume;
import org.junit.internal.AssumptionViolatedException;

public class DevelopBranchIT extends AbstractIntegrationTest {
	private static boolean deployPassed = false;

	/**
	 * Non-snapshot versions on the develop branch should fail.
	 */
	public void testNonSnapshotDeployFails() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/develop", "1.0.0");
		verifier.setAutoclean(true);
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

		verifier.setAutoclean(true);
		verifier.executeGoal("deploy");

		verifier.verifyErrorFreeLog();

		verifier.resetStreams();
		deployPassed = true;
	}

	/**
	 * Attaching existing artifacts from the develop branch should pass.
	 *
	 * @throws Exception
	 */
	public void testAttachExistingArtifacts() throws Exception {
		// If it didn't pass...
		try {
			Assume.assumeTrue(deployPassed);
		} catch (AssumptionViolatedException ave) {
			Assume.assumeNoException(ave);
		}

		Verifier verifier = createVerifier("/project-stub", "origin/develop", "1.0.0-SNAPSHOT");

		verifier.executeGoal("gitflow-helper:attach-deployed");

		verifier.verifyErrorFreeLog();
	}
}
