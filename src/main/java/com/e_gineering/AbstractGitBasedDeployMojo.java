package com.e_gineering;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.repository.RemoteRepository;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sets the project release repository and snapshot repository to the proper locations given the current git branch.
 */
public abstract class AbstractGitBasedDeployMojo extends AbstractGitEnforcerMojo {

    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.+)::(.+)::(.+)::(.+)");

    @Parameter(property = "releaseDeploymentRepository", required = true)
    protected String releaseDeploymentRepository;

    @Parameter(property = "stageDeploymentRepository", required = true)
    protected String stageDeploymentRepository;

    @Parameter(property = "snapshotDeploymentRepository", required = true)
    protected String snapshotDeploymentRepository;

    @Component
    protected ArtifactRepositoryFactory repositoryFactory;

    @Component(role = ArtifactRepositoryLayout.class)
    private Map<String, ArtifactRepositoryLayout> repositoryLayouts;

    protected abstract void execute(final GitBranchType type) throws MojoExecutionException, MojoFailureException;

    private void logExecute(final GitBranchType type) throws MojoExecutionException, MojoFailureException {
        if (!type.equals(GitBranchType.OTHER)) {
            getLog().info("Building for " + type.name() + " git branch.");
        }
        execute(type);
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        String gitBranch = System.getenv("GIT_BRANCH");
        if (gitBranch != null) {
            getLog().debug("Detected GIT_BRANCH: '" + gitBranch + "' in build environment.");

            /*
             * /origin/master goes to the maven 'release' repo.
             * /origin/release/.* , /origin/hotfix/.* , and /origin/bugfix/.* go to the maven 'test' repo.
             * /origin/development goes to the 'snapshot' repo.
             * All other builds will use the default semantics for 'deploy'.
             */
            if (gitBranch.matches(masterBranchPattern)) {
                logExecute(GitBranchType.MASTER);
            } else if (gitBranch.matches(releaseBranchPattern)) {
                logExecute(GitBranchType.RELEASE);
            } else if (gitBranch.matches(hotfixBranchPattern)) {
                logExecute(GitBranchType.HOTFIX);
            } else if (gitBranch.matches(bugfixBranchPattern)) {
                logExecute(GitBranchType.BUGFIX);
            } else if (gitBranch.matches(developmentBranchPattern)) {
                logExecute(GitBranchType.DEVELOPMENT);
            } else {
                logExecute(GitBranchType.OTHER);
            }
        } else {
            getLog().debug("GIT_BRANCH Undefined. Build will continue as configured.");
        }
    }

    protected ArtifactRepository getDeploymentRepository(final String altRepository) throws MojoExecutionException, MojoFailureException {
        Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher(altRepository);
        if (!matcher.matches()) {
            throw new MojoFailureException(altRepository, "Invalid syntax for repository.",
                    "Invalid syntax for repository. Use \"id::layout::url::unique\".");
        }

        String id = matcher.group(1).trim();
        String layout = matcher.group(2).trim();
        String url = matcher.group(3).trim();
        boolean unique = Boolean.parseBoolean(matcher.group(4).trim());

        ArtifactRepositoryLayout repoLayout = getLayout(layout);

        return repositoryFactory.createDeploymentArtifactRepository(id, url, repoLayout, unique);
    }

    protected RemoteRepository getRepository(final String altRepository) throws MojoExecutionException, MojoFailureException {
        Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher(altRepository);
        if (!matcher.matches()) {
            throw new MojoFailureException(altRepository, "Invalid syntax for repository.",
                    "Invalid syntax for repository. Use \"id::layout::url::unique\".");
        }

        String id = matcher.group(1).trim();
        String layout = matcher.group(2).trim();
        String url = matcher.group(3).trim();
        boolean unique = Boolean.parseBoolean(matcher.group(4).trim());

        ArtifactRepositoryLayout repoLayout = getLayout(layout);

        return new RemoteRepository.Builder(id, layout, url).build();
    }

    private ArtifactRepositoryLayout getLayout(final String id) throws MojoExecutionException {
        ArtifactRepositoryLayout layout = repositoryLayouts.get(id);
        if (layout == null) {
            throw new MojoExecutionException("Invalid repository layout: " + id);
        }

        return layout;
    }
}
