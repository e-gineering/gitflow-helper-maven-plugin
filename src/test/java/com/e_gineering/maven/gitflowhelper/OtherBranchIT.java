package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.it.Verifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

import java.util.Arrays;

@RunWith(BlockJUnit4ClassRunner.class)
public class OtherBranchIT extends AbstractIntegrationTest {
	@Test
	public void featureSnapshotSemVer() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/feature/poc/my-feature-branch", "5.0.0-SNAPSHOT");
		try {
			verifier.addCliOption("-Dexpression=project.version");
			verifier.executeGoal("help:evaluate");
			
			verifier.verifyTextInLog("Updating project com.e-gineering:gitflow-helper-maven-plugin-test-stub:5.0.0-SNAPSHOT to: 5.0.0+origin-feature-poc-my-feature-branch-SNAPSHOT");
			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}

	@Test
	public void featureSemVer() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/feature/poc/my-feature-branch.with.other.identifiers", "5.0.1");
		try {
			verifier.executeGoal("initialize");
			
			verifier.verifyTextInLog("Updating project com.e-gineering:gitflow-helper-maven-plugin-test-stub:5.0.1 to: 5.0.1+origin-feature-poc-my-feature-branch.with.other.identifiers-SNAPSHOT");
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
			
			verifier.verifyTextInLog("Updating project com.e-gineering:gitflow-helper-maven-plugin-test-stub:2.0.0 to: 2.0.0+origin-feature-poc-long-running-SNAPSHOT");
			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}

		// Create a -SNAPSHOT of the project-alt1-stub that depends upon the other project's automagic version.
		// The alt project defines a `-` as the otherBranchVersionDelimiter.
		verifier = createVerifier("/project-alt1-stub", "origin/feature/poc/long-running", "2.0.0");
		try {
			verifier.getCliOptions().add("-Ddependency.stub.version=2.0.0+origin-feature-poc-long-running-SNAPSHOT");
			verifier.getCliOptions().add("-Dplugin.stub.version=2.0.0+origin-feature-poc-long-running-SNAPSHOT");

			verifier.executeGoal("deploy");
			// the alt project uses the `-` as the version delimiter, rather than `+`
			verifier.verifyTextInLog("Updating project com.e-gineering:gitflow-helper-maven-plugin-test-stub-alt:2.0.0 to: 2.0.0-origin-feature-poc-long-running-SNAPSHOT");
			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}

	@Test
	public void attachDeployed() throws Exception {
		Verifier verifier = createVerifier("/project-stub", "origin/feature/poc/reattach", "5.0.0-SNAPSHOT");
		try {
			verifier.executeGoal("deploy");
			verifier.verifyTextInLog("Updating project com.e-gineering:gitflow-helper-maven-plugin-test-stub:5.0.0-SNAPSHOT to: 5.0.0+origin-feature-poc-reattach-SNAPSHOT");
			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}

		verifier = createVerifier("/project-stub", "origin/feature/poc/reattach", "5.0.0-SNAPSHOT");
		try {
			verifier.executeGoals(Arrays.asList("validate", "gitflow-helper:attach-deployed"));
			verifier.verifyTextInLog("Updating project com.e-gineering:gitflow-helper-maven-plugin-test-stub:5.0.0-SNAPSHOT to: 5.0.0+origin-feature-poc-reattach-SNAPSHOT");
			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}

	@Test
	public void buildMultiModuleProject() throws Exception {
		Verifier verifier = createVerifier("/multi-module-project-stub", "origin/feature/poc/test-partial-multi-module", "5.0.0-SNAPSHOT");
		try {
			verifier.executeGoal("install");

			// Verify that all 3 maven projects that are part of the multi-module build are recognized
			verifier.verifyTextInLog("Updating project com.e-gineering:gitflow-helper-maven-plugin-multi-module-parent-stub:5.0.0-SNAPSHOT to: 5.0.0+origin-feature-poc-test-partial-multi-module-SNAPSHOT");
			verifier.verifyTextInLog("Updating project com.e-gineering:gitflow-helper-maven-plugin-multi-module-child1-stub:5.0.0-SNAPSHOT to: 5.0.0+origin-feature-poc-test-partial-multi-module-SNAPSHOT");
			verifier.verifyTextInLog("Updating project com.e-gineering:gitflow-helper-maven-plugin-multi-module-child2-stub:5.0.0-SNAPSHOT to: 5.0.0+origin-feature-poc-test-partial-multi-module-SNAPSHOT");

			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}

	@Test
	public void partialBuildMultiModuleProject() throws Exception {
		// Partial builds of a multi-module project *must* be preceded by a full build
		buildMultiModuleProject();

		// After a full build, try to build module child2 in isolation
		Verifier verifier = createVerifier("/multi-module-project-stub", "origin/feature/poc/test-partial-multi-module", "5.0.0-SNAPSHOT");
		try {
			// Only build module child2 (thus this being a partial build)
			verifier.addCliOption("-pl child2");
			verifier.executeGoal("install");

			// Verify that the plugin detects that we are doing a partial build
			verifier.verifyTextInLog("Found top level project, but outside reactor: com.e-gineering:gitflow-helper-maven-plugin-multi-module-parent-stub");

			// Verify that the dependency to a module not part of the current build *is* being rewritten
			verifier.verifyTextInLog("Updating outside-reactor project com.e-gineering:gitflow-helper-maven-plugin-multi-module-child1-stub:5.0.0-SNAPSHOT to: 5.0.0+origin-feature-poc-test-partial-multi-module-SNAPSHOT");

			// Verify that the project that *is* part of the reactor is left alone in the construction of the cross-walk map.
			verifier.verifyTextInLog("Skipping com.e-gineering:gitflow-helper-maven-plugin-multi-module-child2-stub: already part of reactor");

			verifier.verifyErrorFreeLog();
		} finally {
			verifier.resetStreams();
		}
	}
}
