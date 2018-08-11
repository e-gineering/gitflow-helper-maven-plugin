package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.it.Verifier;
import org.junit.Assume;
import org.junit.internal.AssumptionViolatedException;

public class ReleaseBranchIT extends AbstractIntegrationTest {

	/**
	 * Non-snapshot versions on the develop branch should fail.
	 */
	public void testSnapshotDeployFails() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/release/1.0.0", "1.0.0-SNAPSHOT");

		// Allow -SNAPSHOT builds of the plugin to succeed while still asserting the version match.
		verifier.getCliOptions().add("-DenforceNonSnapshots=false");
		try {
			verifier.executeGoal("deploy");
		} catch (Exception ex) {
			verifier.verifyTextInLog("The current git branch: [origin/release/1.0.0] is defined as a release branch. The maven project or one of its parents is currently a snapshot version.");
		}
		verifier.resetStreams();
	}

	/**.
	 * Snapshot versions on the develop branch should pass
	 * @throws Exception
	 */
	public void testDeploySuccess() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/release/1.0.0", "1.0.0");

		// Allow -SNAPSHOT builds of the plugin to succeed while still asserting the version match.
		verifier.getCliOptions().add("-DenforceNonSnapshots=false");

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
		Verifier verifier = createVerifier("/project-stub", "origin/release/1.0.0", "1.0.0");

		// Allow -SNAPSHOT builds of the plugin to succeed while still asserting the version match.
		verifier.getCliOptions().add("-DenforceNonSnapshots=false");

		verifier.executeGoal("deploy");

		verifier.verifyErrorFreeLog();

		verifier.resetStreams();

		// Now re-attach in another verifier.
		verifier = createVerifier("/project-stub", "origin/release/1.0.0", "1.0.0");

		// Allow -SNAPSHOT builds of the plugin to succeed while still asserting the version match.
		verifier.getCliOptions().add("-DenforceNonSnapshots=false");
		verifier.executeGoal("gitflow-helper:attach-deployed");

		verifier.verifyErrorFreeLog();
	}
}
