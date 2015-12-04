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
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 * Set the target repository for deployment based upon the GIT_BRANCH being built.
 */
@Mojo(name = "promote-master", defaultPhase = LifecyclePhase.DEPLOY)
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
                getLog().info("Building from master branch. Attempting to resolve tested artifacts from [" + testDeploymentRepository + "]");

                List<RemoteRepository> remoteRepositories = Arrays.asList(getRepository(testDeploymentRepository));

                // A place to store our resolved files...
                List<ArtifactResult> resolvedArtifacts = new ArrayList<ArtifactResult>();


                // Build up a set of ArtifactRequests, for the pom, the current packaging layout, the -sources.jar and the -javadoc.jar and the
                List<ArtifactRequest> requiredArtifacts = new ArrayList<ArtifactRequest>();

                // This is required!
                requiredArtifacts.add(new ArtifactRequest(new DefaultArtifact(project.getGroupId(), project.getArtifactId(), project.getPackaging(), project.getVersion()), remoteRepositories, null));
                try {
                    resolvedArtifacts.addAll(artifactResolver.resolveArtifacts(session, requiredArtifacts));
                } catch (ArtifactResolutionException are) {
                    throw new MojoExecutionException("Failed to resolve the required project files from the testDeploymentRepository", are);
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

                // Invoke the Deploy task to attach the resolved (and attached to this build) artifacts.
                executeMojo(
                        plugin(
                                groupId("org.apache.maven.plugins"),
                                artifactId("maven-deploy-plugin"),
                                version("2.8.2")
                        ),
                        goal("deploy"),
                        configuration(),
                        executionEnvironment(
                                project,
                                mavenSession,
                                pluginManager
                        )
                );

                break;
            }
            default: {
                getLog().debug("Building from non-master branch [" + gitBranch + "]. Build will continue as configured.");
            }
        }
    }
}
