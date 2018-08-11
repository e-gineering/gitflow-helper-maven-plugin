package com.e_gineering.maven.gitflowhelper;


import junit.framework.TestCase;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

public abstract class AbstractIntegrationTest extends TestCase {
	private static PrintStream out = System.out;
	private static PrintStream err = System.err;


	/**
	 * The zero-based column index where to print the test result.
	 */
	private static final int RESULT_COLUMN = 60;

	@Override
	protected void runTest() throws Throwable {
		String testMethod = getClass().getSimpleName() + "." + getName();
		out.print(testMethod);
		out.print(pad(RESULT_COLUMN - testMethod.length()));

		String status = "";
		long start = System.nanoTime();
		try {
			super.runTest();
		} finally {
			long end = System.nanoTime();
			status += TimeUnit.MILLISECONDS.convert((end - start), TimeUnit.NANOSECONDS) + "ms";
			out.println(status);
		}
	}

	protected Verifier createVerifier(String projectPath, String gitBranch, String projectVersion) throws IOException, VerificationException {
		return createVerifier(ResourceExtractor.simpleExtractResources(getClass(), projectPath).getAbsolutePath(), null, gitBranch, projectVersion, false);
	}

	private Verifier createVerifier(String basedir, String settings, String gitBranch, String gitflowProjectVersion, boolean debug) throws VerificationException {
		Verifier verifier = new Verifier(basedir, debug);

		verifier.setAutoclean(false);

		if (System.getProperty("argLine", "").length() > 0) {
			String opts = "";
			if (verifier.getEnvironmentVariables().get("MAVEN_OPTS") != null) {
				opts += verifier.getEnvironmentVariables().get("MAVEN_OPTS");
			}
			opts += System.getProperty("argLine", "");
			verifier.setEnvironmentVariable("MAVEN_OPTS", opts);
		}
		verifier.getCliOptions().add("-Dgitflow.project.version=" + gitflowProjectVersion);
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

		verifier.getSystemProperties().put("maven.compiler.source", "1.7");
		verifier.getSystemProperties().put("maven.compiler.target", "1.7");

		return verifier;
	}


	private String pad(int chars) {
		StringBuilder buffer = new StringBuilder(128);
		for (int i = 0; i < chars; i++) {
			buffer.append('.');
		}
		return buffer.toString();
	}
}