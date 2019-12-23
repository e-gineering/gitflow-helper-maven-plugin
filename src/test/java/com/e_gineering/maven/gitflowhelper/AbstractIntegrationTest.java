package com.e_gineering.maven.gitflowhelper;


import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

public abstract class AbstractIntegrationTest {

	private static PrintStream out = System.out;
	private static PrintStream err = System.err;

	@Rule
	public TestName name = new TestName();

	protected Verifier createVerifier(String projectPath, String gitBranch, String projectVersion) throws IOException, VerificationException {
		return createVerifier(ResourceExtractor.simpleExtractResources(getClass(), projectPath).getAbsolutePath(), null, gitBranch, projectVersion, false);
	}

	private Verifier createVerifier(String basedir, String settings, String gitBranch, String stubProjectVersion, boolean debug) throws VerificationException {
		Verifier verifier = new Verifier(basedir, debug);
		verifier.setLogFileName(getClass().getSimpleName() + "_" + name.getMethodName() + "-log.txt");
		verifier.setAutoclean(true);

		if (System.getProperty("argLine", "").length() > 0) {
			String opts = "";
			if (verifier.getEnvironmentVariables().get("MAVEN_OPTS") != null) {
				opts += verifier.getEnvironmentVariables().get("MAVEN_OPTS");
			}
			opts += System.getProperty("argLine", "");
			verifier.setEnvironmentVariable("MAVEN_OPTS", opts);
		}
		// Always allow our plugin to use snapshot versions when building / testing ourselves.
		verifier.getCliOptions().add("-DallowGitflowPluginSnapshot=true");
		verifier.getCliOptions().add("-Dstub.project.version=" + stubProjectVersion);
		verifier.getEnvironmentVariables().put("GIT_BRANCH", gitBranch);

		if (settings != null) {
			File settingsFile;
			if (settings.length() > 0) {
				settingsFile = new File("settings-" + settings + ".xml");
			} else {
				settingsFile = new File("settings.xml");
			}

			if (!settingsFile.isAbsolute()) {
				String settingsDir = System.getProperty("maven.it.global-settings.dir", "");
				if (settingsDir.length() > 0) {
					settingsFile = new File(settingsDir, settingsFile.getPath());
				} else {
					//
					// Make is easier to run ITs from m2e in Maven IT mode without having to set any additional
					// properties.
					//
					settingsFile = new File("target/test-classes", settingsFile.getPath());
				}
			}

			String path = settingsFile.getAbsolutePath();

			verifier.getCliOptions().add("--global-settings");
			if (path.indexOf(' ') < 0) {
				verifier.getCliOptions().add(path);
			} else {
				verifier.getCliOptions().add('"' + path + '"');
			}
		}

		verifier.getSystemProperties().put("maven.multiModuleProjectDirectory", basedir);

		verifier.getSystemProperties().put("maven.compiler.source", "1.8");
		verifier.getSystemProperties().put("maven.compiler.target", "1.8");

		return verifier;
	}
}