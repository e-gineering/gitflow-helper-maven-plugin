package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.it.Verifier;

public class DevelopBranchIT extends AbstractIntegrationTest {
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
}
