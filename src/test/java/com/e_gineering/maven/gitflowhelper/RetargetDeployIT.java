package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.it.Verifier;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

import java.io.File;

@RunWith(BlockJUnit4ClassRunner.class)
public class RetargetDeployIT extends AbstractIntegrationTest {

	@Test
	public void devTargetsSnapshot() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/develop", "3.0.0-SNAPSHOT");

		verifier.executeGoal("deploy");

		verifier.verifyErrorFreeLog();
		verifier.resetStreams();

		// Ensure the file exists in the repo.
		File artifactDir = new File(System.getProperty("basedir"), "target/it-repositories/snapshots/com/e-gineering/gitflow-helper-maven-plugin-test-stub/3.0.0-SNAPSHOT");
		Assert.assertTrue(artifactDir.exists() && artifactDir.isDirectory() && artifactDir.list().length > 0);
	}

	@Test
	public void releaseTargetsTest() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/release/3.1.0", "3.1.0");

		verifier.executeGoal("deploy");

		verifier.verifyErrorFreeLog();
		verifier.resetStreams();

		// Ensure the file exists in the repo.
		File artifactDir = new File(System.getProperty("basedir"), "target/it-repositories/test-releases/com/e-gineering/gitflow-helper-maven-plugin-test-stub/3.1.0");
		Assert.assertTrue(artifactDir.exists() && artifactDir.isDirectory() && artifactDir.list().length > 0);
	}

	@Test
	public void hotfixTargetsTest() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/hotfix/3.1.5", "3.1.5");

		verifier.executeGoal("deploy");

		verifier.verifyErrorFreeLog();
		verifier.resetStreams();

		// Ensure the file exists in the repo.
		File artifactDir = new File(System.getProperty("basedir"), "target/it-repositories/test-releases/com/e-gineering/gitflow-helper-maven-plugin-test-stub/3.1.5");
		Assert.assertTrue(artifactDir.exists() && artifactDir.isDirectory() && artifactDir.list().length > 0);
	}

	@Test
	public void supportTargetsReleases() throws Exception {
		// Deploy a hotfix to the test-releases.
		Verifier verifier = createVerifier("/project-stub", "origin/hotfix/3.2.1", "3.2.1");
		verifier.executeGoal("deploy");
		verifier.verifyErrorFreeLog();
		verifier.resetStreams();

		File artifactDir = new File(System.getProperty("basedir"), "target/it-repositories/test-releases/com/e-gineering/gitflow-helper-maven-plugin-test-stub/3.2.1");
		Assert.assertTrue(artifactDir.exists() && artifactDir.isDirectory() && artifactDir.list().length > 0);

		// Promote with the support branch
		verifier = createVerifier("/project-stub", "origin/support/3.2", "3.2.1");
		verifier.executeGoal("deploy");

		verifier.verifyErrorFreeLog();
		verifier.resetStreams();

		// Ensure the file exists in the repo.
		artifactDir = new File(System.getProperty("basedir"), "target/it-repositories/releases/com/e-gineering/gitflow-helper-maven-plugin-test-stub/3.2.1");
		Assert.assertTrue(artifactDir.exists() && artifactDir.isDirectory() && artifactDir.list().length > 0);
	}

	@Test
	public void masterTargetsReleases() throws Exception {
		// Deploy a hotfix to the test-releases.
		Verifier verifier = createVerifier("/project-stub", "origin/release/3.3.0", "3.3.0");
		verifier.executeGoal("deploy");
		verifier.verifyErrorFreeLog();
		verifier.resetStreams();

		File artifactDir = new File(System.getProperty("basedir"), "target/it-repositories/test-releases/com/e-gineering/gitflow-helper-maven-plugin-test-stub/3.3.0");
		Assert.assertTrue(artifactDir.exists() && artifactDir.isDirectory() && artifactDir.list().length > 0);

		// Promote with the support branch
		verifier = createVerifier("/project-stub", "origin/master", "3.3.0");
		verifier.setMavenDebug(true);
		verifier.executeGoal("deploy");

		verifier.verifyErrorFreeLog();
		verifier.resetStreams();

		// Ensure the file exists in the repo.
		artifactDir = new File(System.getProperty("basedir"), "target/it-repositories/releases/com/e-gineering/gitflow-helper-maven-plugin-test-stub/3.3.0");
		Assert.assertTrue(artifactDir.exists() && artifactDir.isDirectory() && artifactDir.list().length > 0);
	}

	@Test
	public void othersUnsetRepos() throws Exception {
		// Deploy a hotfix to the test-releases.
		Verifier verifier = createVerifier("/project-stub", "feature/undeployable", "3.5.0-SNAPSHOT");
		try {
			verifier.executeGoal("deploy");
			verifier.verifyTextInLog("[INFO] Skipping artifact deployment");
		} finally {
			verifier.resetStreams();
		}
	}
}
