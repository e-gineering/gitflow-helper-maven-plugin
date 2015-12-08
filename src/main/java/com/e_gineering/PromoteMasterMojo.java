package com.e_gineering;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Set the target repository for deployment based upon the GIT_BRANCH being built.
 */
@Mojo(name = "promote-master", defaultPhase = LifecyclePhase.INSTALL)
public class PromoteMasterMojo extends AbstractGitBasedDeployMojo {

    @Component
    private ArtifactResolver artifactResolver;

    @Parameter(defaultValue = "${repositorySystemSession}")
    private RepositorySystemSession session;

    @Component
    private MavenSession mavenSession;

    @Component
    private BuildPluginManager pluginManager;


    @Component
    private MavenProjectHelper projectHelper;

    @Override
    protected void execute(final GitBranchType type) throws MojoExecutionException, MojoFailureException {
        switch (type) {
            case MASTER: {
                getLog().info("Attaching existing artifacts from stageDeploymentRepository [" + stageDeploymentRepository + "]");

                List<RemoteRepository> remoteRepositories = Arrays.asList(getRepository(stageDeploymentRepository));

                // A place to store our resolved files...
                List<ArtifactResult> resolvedArtifacts = new ArrayList<ArtifactResult>();


                // Build up a set of ArtifactRequests, for the pom, the current packaging layout, the -sources.jar and the -javadoc.jar and the
                List<ArtifactRequest> requiredArtifacts = new ArrayList<ArtifactRequest>();


                // Keep track of the original base directory.
                getLog().info("Disabling local repository @ " + session.getLocalRepository().getBasedir());
                Field localBaseDir = null;
                File originalBaseDir = session.getLocalRepositoryManager().getRepository().getBasedir();

                // Disable the local repository.
                try {
                    localBaseDir = LocalRepository.class.getDeclaredField("basedir");
                    localBaseDir.setAccessible(true);

                    // Generate a new temp directory.
                    File tempRepo = Files.createTempDirectory("gitflow-helper-maven-plugin-repo").toFile();
                    tempRepo.deleteOnExit();

                    getLog().info("Using temporary local repository @ " + tempRepo.getAbsolutePath());
                    localBaseDir.set(session.getLocalRepositoryManager().getRepository(), tempRepo);
                } catch (Exception ex) {
                    getLog().warn("Failed to disable local repository path.", ex);
                }

                // This is required!
                requiredArtifacts.add(new ArtifactRequest(new DefaultArtifact(project.getGroupId(), project.getArtifactId(), project.getPackaging(), project.getVersion()), remoteRepositories, null));
                try {
                    resolvedArtifacts.addAll(artifactResolver.resolveArtifacts(session, requiredArtifacts));
                } catch (ArtifactResolutionException are) {
                    throw new MojoExecutionException("Failed to resolve the required project files from the stageDeploymentRepository", are);
                }

                // Optional Artifacts... We do these one at a time so we don't fail the build....
                List<ArtifactRequest> optionalArtifacts = new ArrayList<ArtifactRequest>();
                optionalArtifacts.add(new ArtifactRequest(new DefaultArtifact(project.getGroupId(), project.getArtifactId(), "javadoc", "jar", project.getVersion()), remoteRepositories, null));
                optionalArtifacts.add(new ArtifactRequest(new DefaultArtifact(project.getGroupId(), project.getArtifactId(), "sources", "jar", project.getVersion()), remoteRepositories, null));
                optionalArtifacts.add(new ArtifactRequest(new DefaultArtifact(project.getGroupId(), project.getArtifactId(), "tests", "jar", project.getVersion()), remoteRepositories, null));

                for (int i = 0; i < optionalArtifacts.size(); i++) {
                    try {
                        resolvedArtifacts.add(artifactResolver.resolveArtifact(session, optionalArtifacts.get(i)));
                    } catch (ArtifactResolutionException are) {
                        getLog().info("Optional Artifact not found: " + optionalArtifacts.get(i).getArtifact());
                    }
                }

                getLog().info("Resolved: " + resolvedArtifacts.size() + " artifacts.");

                for (int i = 0; i < resolvedArtifacts.size(); i++) {
                    Artifact artifact = resolvedArtifacts.get(i).getArtifact();
                    if (i == 0) {
                        project.getArtifact().setFile(artifact.getFile());
                    } else {
                        projectHelper.attachArtifact(project, artifact.getFile(), artifact.getClassifier());
                    }
                }

                // Restore the local repository.
                try {
                    localBaseDir.set(session.getLocalRepositoryManager().getRepository(), originalBaseDir);
                    localBaseDir.setAccessible(false);
                } catch (Exception ex) {
                    getLog().warn("Failed to restore original local repository path.", ex);
                }


                break;
            }
        }
    }
}
