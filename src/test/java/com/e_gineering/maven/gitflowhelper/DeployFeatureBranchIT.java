package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.it.Verifier;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

@RunWith(BlockJUnit4ClassRunner.class)
public class DeployFeatureBranchIT extends AbstractIntegrationTest {

    @Test
    public void deployFeatureBranchWithBranchNameInMavenVersion() throws Exception {
        String version = "1.0.0-feature-my-feature-SNAPSHOT"; // POM has 1.0.0-SNAPSHOT, branch name should be mixed in

        Verifier verifier = createVerifier("/multi-module-project-stub", "feature/my-feature", version);

        try {
            verifier.executeGoal("deploy");
            verifier.verifyErrorFreeLog();

            // Check if the parent POM file exists in the local repo
            File parentArtifactDir = getArtifactDir("gitflow-helper-maven-plugin-multi-module-parent-test-stub", version);
            Assert.assertTrue(artifactHasFiles(parentArtifactDir));
            File parentPomFile = getPomFromArtifactDir(parentArtifactDir);
            Assert.assertNotNull(parentPomFile);

            // Check if the parent POM version number contains the branch name
            Model parentPom = parsePomFile(parentPomFile);
            Assert.assertEquals(version, parentPom.getVersion());

            // Check if the module POM file exists in the local repo
            File moduleArtifactDir = getArtifactDir("module", version);
            Assert.assertTrue(artifactHasFiles(moduleArtifactDir));
            File modulePomFile = getPomFromArtifactDir(moduleArtifactDir);
            Assert.assertNotNull(modulePomFile);

            // Check if the module POM version number contains the branch name
            Model modulePom = parsePomFile(modulePomFile);
            Assert.assertEquals(version, modulePom.getParent().getVersion());
        } finally {
            verifier.resetStreams();
        }
    }

    private File getArtifactDir(final String artifactId, final String version) {
        return new File(System.getProperty("basedir"),
                "target/it-repositories/snapshots/com/e-gineering/" + artifactId + "/" + version
        );
    }

    private boolean artifactHasFiles(final File artifactDir) {
        return artifactDir.exists() && artifactDir.isDirectory() && artifactDir.list().length > 0;
    }

    private File getPomFromArtifactDir(final File artifactDir) {
        File pom = null;

        File[] poms = artifactDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".pom");
            }
        });

        if (poms != null && poms.length > 0) {
            Arrays.sort(poms, new Comparator<File>() {
                @Override
                public int compare(File file1, File file2) {
                    return Long.compare(file2.lastModified(), file1.lastModified());
                }
            });

            pom = poms[0]; // The latest POM
        }

        return pom;
    }

    private Model parsePomFile(final File pomFile) {
        Model pom = null;
        try (FileReader pomReader = new FileReader(pomFile)) {
            pom = new MavenXpp3Reader().read(pomReader);
        } catch (IOException | XmlPullParserException e) {
            Assert.fail("Cannot parse POM");
        }

        return pom;
    }

}
