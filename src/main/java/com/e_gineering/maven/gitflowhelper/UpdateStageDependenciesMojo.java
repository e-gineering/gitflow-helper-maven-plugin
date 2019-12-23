package com.e_gineering.maven.gitflowhelper;

import org.apache.commons.io.FileUtils;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Forces a re-resolution of all dependency artifacts which were resolved from the 'stage' remote repository.
 */
@Mojo(name = "update-stage-dependencies", defaultPhase = LifecyclePhase.INITIALIZE)
public class UpdateStageDependenciesMojo extends AbstractGitflowBasedRepositoryMojo {

    @Component
    ProjectDependenciesResolver dependenciesResolver;

    @Override
    protected void execute(final GitBranchInfo branchInfo) throws MojoExecutionException, MojoFailureException {
        getLog().debug("update-stage-dependencies setting up Repository session...");

        DefaultRepositorySystemSession reresolveSession = new DefaultRepositorySystemSession(repositorySystemSession);
        reresolveSession.setUpdatePolicy(org.eclipse.aether.repository.RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        reresolveSession.setCache(new DefaultRepositoryCache());

        LocalRepositoryManager localRepositoryManager = reresolveSession.getLocalRepositoryManager();

        getLog().debug("configuring stage as the remote repository for artifact resolution requests...");
        List<RemoteRepository> stageRepo = Arrays.asList(RepositoryUtils.toRepo(getDeploymentRepository(stageDeploymentRepository)));

        boolean itemsPurged = false;

        try {
            DefaultDependencyResolutionRequest projectDepsRequest = new DefaultDependencyResolutionRequest(project, reresolveSession);
            DependencyResolutionResult depencencyResult = dependenciesResolver.resolve(projectDepsRequest);

            for (Dependency dependency : depencencyResult.getResolvedDependencies()) {
                if (!dependency.getArtifact().isSnapshot()) {
                    // Find the artifact in the local repo, and if it came from the 'stageRepo', populate that info
                    // as the 'repository' on the artifact.
                    LocalArtifactResult localResult = localRepositoryManager.find(reresolveSession,
                            new LocalArtifactRequest(dependency.getArtifact(), stageRepo, null));

                    // If the result has a file... and the getRepository() matched the stage repo id...
                    if (localResult.getFile() != null && localResult.getRepository() != null) {
                        getLog().info("Purging: " + dependency + " from remote repository: " + localResult.getRepository() + ".");
                        File deleteTarget = new File(localRepositoryManager.getRepository().getBasedir(), localRepositoryManager.getPathForLocalArtifact(dependency.getArtifact()));

                        if (deleteTarget.isDirectory()) {
                            try {
                                FileUtils.deleteDirectory(deleteTarget);
                            } catch (IOException ioe) {
                                getLog().warn("Failed to purge stage artifact from local repository: " + deleteTarget, ioe);
                            }
                        } else if (!deleteTarget.delete()) {
                            getLog().warn("Failed to purge stage artifact from local repository: " + deleteTarget);
                        }
                        itemsPurged = true;
                    }
                }
            }
        } catch (DependencyResolutionException dre) {
            throw new MojoExecutionException("Initial dependency resolution to resolve dependencies which may have been provided by the 'stage' repository failed.", dre);
        }


        if (itemsPurged) {
            try {
                getLog().info("Resolving purged dependencies...");
                dependenciesResolver.resolve(new DefaultDependencyResolutionRequest(project, reresolveSession));
                getLog().info("All stage dependencies purged and re-resolved.");
            } catch (DependencyResolutionException e) {
                throw new MojoExecutionException("Post-purge dependency resolution failed!", e);
            }
        }
    }
}
