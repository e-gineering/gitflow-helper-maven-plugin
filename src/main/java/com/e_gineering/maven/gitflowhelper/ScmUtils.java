package com.e_gineering.maven.gitflowhelper;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.log.ScmLogDispatcher;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.provider.ScmProvider;
import org.apache.maven.scm.provider.git.gitexe.command.branch.GitBranchCommand;
import org.apache.maven.scm.provider.git.repository.GitScmProviderRepository;
import org.apache.maven.scm.repository.ScmRepository;

public abstract class ScmUtils {

    public static final String DEFAULT_URL_EXPRESSION = "${env.GIT_URL}";
    public static final String DEFAULT_BRANCH_EXPRESSION = "${env.GIT_BRANCH}";

    /**
     * Given the ScmManager for the current execution cycle, and the MavenProject structure, determine the SCM URL or
     * an expression we can resolve the URL from.
     *
     * @param scmManager The current maven ScmManager
     * @param project    The Current maven Project
     * @param log        A Log to write to
     * @return The developerConnection, if none set, the connection, if none set, then the expression, <code>"${env.GIT_URL}"</code>
     */
    public static String resolveUrlOrExpression(final ScmManager scmManager, final MavenProject project, final Log log) {
        String connectionUrl = null;

        // Some projects don't specify SCM Blocks, and instead rely upon the CI server to provide an '${env.GIT_BRANCH}'
        if (project.getScm() != null) {
            // Start with the developer connection, then fall back to the non-developer connection.
            connectionUrl = project.getScm().getDeveloperConnection();
            if (StringUtils.isBlank(connectionUrl)) {
                connectionUrl = project.getScm().getConnection();
            }
            return connectionUrl;
        }

        return DEFAULT_URL_EXPRESSION;
    }

    /**
     * Given the ScmManager for the current execution cycle, and the MavenProject structure, determine if we can
     * find a maven-provided manner of resolving the current git branch.
     *
     * @param scmManager The current maven ScmManager
     * @param project    The Current maven Project
     * @param log        A Log to write to
     * @return The current git branch name, or <code>${env.GIT_BRACH}</code> if the current git branch could not be resolved.
     * @throws ScmException
     */
    public static String resolveBranchOrExpression(final ScmManager scmManager, final MavenProject project, final Log log) {
        String connectionUrl = resolveUrlOrExpression(scmManager, project, log);

        // If a connectionURL other than the default expression was resolved, try to resolve the branch.
        if (!StringUtils.equals(connectionUrl, DEFAULT_URL_EXPRESSION)) {
            try {
                ScmRepository repository = scmManager.makeScmRepository(connectionUrl);
                ScmProvider provider = scmManager.getProviderByRepository(repository);

                if (GitScmProviderRepository.PROTOCOL_GIT.equals(provider.getScmType())) {
                    ScmFileSet fileSet = new ScmFileSet(project.getBasedir());
                    return GitBranchCommand.getCurrentBranch(new ScmLogDispatcher(), (GitScmProviderRepository) repository.getProviderRepository(), fileSet);
                } else {
                    log.warn("Project SCM defines a non-git SCM provider. Falling back to  variable resolution.");
                }
            } catch (ScmException se) {
                log.warn("Unable to resolve Git Branch from Project SCM definition.", se);
            }
        }

        log.debug("Git branch unresolvable from Project SCM definition, defaulting to " + DEFAULT_BRANCH_EXPRESSION);
        return DEFAULT_BRANCH_EXPRESSION;
    }
}
