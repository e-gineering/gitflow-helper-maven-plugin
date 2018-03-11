package com.e_gineering.maven.gitflowhelper;

import com.e_gineering.maven.gitflowhelper.properties.ExpansionBuffer;
import com.e_gineering.maven.gitflowhelper.properties.PropertyResolver;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.log.ScmLogDispatcher;
import org.apache.maven.scm.log.ScmLogger;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.provider.ScmProvider;
import org.apache.maven.scm.provider.git.gitexe.command.GitCommandLineUtils;
import org.apache.maven.scm.provider.git.gitexe.command.branch.GitBranchCommand;
import org.apache.maven.scm.provider.git.repository.GitScmProviderRepository;
import org.apache.maven.scm.repository.ScmRepository;
import org.apache.maven.scm.util.AbstractConsumer;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ScmUtils {

    private static final String DEFAULT_URL_EXPRESSION = "${env.GIT_URL}";

    /**
     * Given the ScmManager for the current execution cycle, and the MavenProject structure, determine the SCM URL or
     * an expression we can resolve the URL from.
     *
     * @param project    The Current maven Project
     * @return The developerConnection, if none set, the connection, if none set, then the expression, <code>"${env.GIT_URL}"</code>
     */
    public static String resolveUrlOrExpression(final MavenProject project) {
        String connectionUrl;

        // Some projects don't specify SCM Blocks, and instead rely upon the CI server to provide an '${env.GIT_BRANCH}'
        if (project.getScm() != null) {
            // Start with the developer connection, then fall back to the non-developer connection.
            connectionUrl = project.getScm().getDeveloperConnection();
            if (StringUtils.isBlank(connectionUrl)) {
                connectionUrl = project.getScm().getConnection();
            }

            // Issue #74, missing an emtpy / null check before returning.
            if (!StringUtils.isBlank(connectionUrl)) {
                return connectionUrl;
            }
        }

        return DEFAULT_URL_EXPRESSION;
    }

    /**
     * Get information on (most importantly, the type of) the current Git branch.
     * If SCM information is configured in the POM (<code>&lt;scm&gt;</code>):
     * <ul>
     *     <li>If we're on a local branch, resolve the branch name.</li>
     *     <li>If we're on a detached HEAD, try to find the type of branch(es) that point to HEAD.</li>
     * </ul>
     * If no SCM information is configured or if the 'gitBranchExpression' parameter is specified,
     * try to resolve it to a value using the environment variables (${git.BRANCH_NAME}).
     * @param scmManager The current maven ScmManager
     * @param project The Current maven Project
     * @param log A Log to write to
     * @param masterBranchPattern Regex pattern matching master branches
     * @param supportBranchPattern Regex pattern matching support branches
     * @param releaseBranchPattern Regex pattern matching release branches
     * @param hotfixBranchPattern Regex pattern matching hotfix branches
     * @param developmentBranchPattern Regex pattern matching development branches
     * @param featureOrBugfixBranchPattern Regex pattern matching feature or bugfix branches
     * @return The detected Git branch info, or null if no branch info could be resolved
     */
    // TODO: should gitBranchExpression take precedence?
    // TODO: clean-up this spaghetti mess
    public static GitBranchInfo getGitBranchInfo(final ScmManager scmManager, final MavenProject project, final Log log, final String gitBranchExpression,
                                                 final String masterBranchPattern, final String supportBranchPattern, final String releaseBranchPattern,
                                                 final String hotfixBranchPattern, final String developmentBranchPattern, final String featureOrBugfixBranchPattern) {
        String connectionUrl = resolveUrlOrExpression(project);
        // If a connectionURL other than the default expression was resolved, try to resolve the branch.
        if (!StringUtils.equals(connectionUrl, DEFAULT_URL_EXPRESSION)) {
            try {
                ScmRepository repository = scmManager.makeScmRepository(connectionUrl);
                ScmProvider provider = scmManager.getProviderByRepository(repository);
                ScmFileSet fileSet = new ScmFileSet(project.getBasedir());

                if (GitScmProviderRepository.PROTOCOL_GIT.equals(provider.getScmType())) {
                    ScmLogDispatcher scmLogger = new ScmLogDispatcher();
                    GitScmProviderRepository gitScmProviderRepository = (GitScmProviderRepository) repository.getProviderRepository();

                    // First, try the local branch
                    try {
                        String localBranch = GitBranchCommand.getCurrentBranch(scmLogger, gitScmProviderRepository, fileSet);
                        GitBranchType branchType = resolveBranchType(localBranch, masterBranchPattern, supportBranchPattern, releaseBranchPattern, hotfixBranchPattern, developmentBranchPattern, featureOrBugfixBranchPattern);
                        return new GitBranchInfo(localBranch, branchType);
                    } catch(ScmException se) {
                        log.debug("Can't detect a local branch; detached HEAD? Will try to resolve that instead...");
                    }

                    // Next, try to resolve the detached HEAD to a single branch type

                    // Do a rev-parse to get the commit hash that HEAD points to
                    String sha1 = sha1ForHEAD(scmLogger, fileSet);
                    log.debug("HEAD is pointing at " + sha1);

                    // Now use show-ref to determine the branches that HEAD's sha1 points to.
                    Set<String> branches = branchesForSha1(sha1, scmLogger, fileSet);
                    log.debug("Found the following branches for " + sha1 + ": " + branches);

                    // Check if the set of branches can be resolved to a single type
                    GitBranchType resolvedBranchType = null;
                    String branchName = null;
                    for (String branch : branches) {
                        GitBranchType branchType = resolveBranchType(branch, masterBranchPattern, supportBranchPattern, releaseBranchPattern, hotfixBranchPattern, developmentBranchPattern, featureOrBugfixBranchPattern);
                        if (resolvedBranchType == null) {
                            resolvedBranchType = branchType;
                        } else if (resolvedBranchType != branchType) {
                            log.warn("Can't resolve " + sha1 + " to a single branch type");
                            return null;
                        } else if (resolvedBranchType == GitBranchType.RELEASE || resolvedBranchType == GitBranchType.HOTFIX || resolvedBranchType == GitBranchType.SUPPORT) {
                            // There may be only one RELEASE, HOTFIX or SUPPORT branch, as they contain version numbers
                            log.warn("Found multiple versioned branches for " + sha1);
                            return null;
                        }
                        // Now we've got multiple non-versioned branches. That's no problem.
                        branchName = branch;
                    }

                    if (resolvedBranchType != null) {
                        return new GitBranchInfo(branchName, resolvedBranchType);
                    } else {
                        log.warn("Can't resolve " + sha1 + " to any branch");
                        return null;
                    }
                } else {
                    log.warn("Project SCM defines a non-git SCM provider. Falling back to variable resolution.");
                }
            } catch (ScmException se) {
                log.warn("Unable to resolve Git Branch from Project SCM definition.", se);
            }
        } else {
            log.debug("No <scm> info found, relying on gitBranchExpression: " + gitBranchExpression);
        }

        // TODO: handle ${env.GIT_BRANCH} (and should it take precedence over resolving the branch first?)
        Properties systemEnvVars;
        try {
            systemEnvVars = CommandLineUtils.getSystemEnvVars();
        } catch (IOException ioe) {
            log.warn("Unable to read System Environment Variables. Can't determine the Git branch type.", ioe);
            return null;
        }

        PropertyResolver resolver = new PropertyResolver();
        String resolvedExpression = resolver.resolveValue(gitBranchExpression, project.getProperties(), systemEnvVars);
        ExpansionBuffer eb = new ExpansionBuffer(resolvedExpression);

        if (!eb.hasMoreLegalPlaceholders()) {
            GitBranchType branchType = resolveBranchType(resolvedExpression, masterBranchPattern, supportBranchPattern, releaseBranchPattern, hotfixBranchPattern, developmentBranchPattern, featureOrBugfixBranchPattern);
            return new GitBranchInfo(resolvedExpression, branchType);
        } else {
            log.warn("Not all placeholders in gitBranchExpression can be resolved: '" + resolvedExpression + "'. Can't determine the Git branch type.");
            return null;
        }
    }

    private static GitBranchType resolveBranchType(String branchName, String masterBranchPattern, String supportBranchPattern, String releaseBranchPattern,
                                                   String hotfixBranchPattern, String developmentBranchPattern, String featureOrBugfixBranchPattern) {
        if (branchName.matches(masterBranchPattern)) {
            return GitBranchType.MASTER;
        } else if (branchName.matches(supportBranchPattern)) {
            return GitBranchType.SUPPORT;
        } else if (branchName.matches(releaseBranchPattern)) {
            return GitBranchType.RELEASE;
        } else if (branchName.matches(hotfixBranchPattern)) {
            return GitBranchType.HOTFIX;
        } else if (branchName.matches(developmentBranchPattern)) {
            return GitBranchType.DEVELOPMENT;
        } else if (branchName.matches(featureOrBugfixBranchPattern)) {
            return GitBranchType.FEATURE_OR_BUGFIX_BRANCH;
        } else {
            return GitBranchType.OTHER;
        }
    }

    private static String sha1ForHEAD(ScmLogger logger, ScmFileSet fileSet) throws ScmException {
        Commandline cl = GitCommandLineUtils.getBaseGitCommandLine( fileSet.getBasedir(), "rev-parse" );
        cl.createArg().setValue("HEAD");

        RevParseConsumer rpConsumer = new RevParseConsumer(logger);
        execGitCmd(logger, rpConsumer, cl);

        return rpConsumer.getRev();
    }

    private static Set<String> branchesForSha1(String sha1, ScmLogger logger, ScmFileSet fileSet) throws ScmException {
        Commandline cl = GitCommandLineUtils.getBaseGitCommandLine( fileSet.getBasedir(), "show-ref" );

        ShowRefForSha1Consumer srConsumer = new ShowRefForSha1Consumer(sha1, logger);
        execGitCmd(logger, srConsumer, cl);

        return srConsumer.getBranches();
    }

    private static void execGitCmd(ScmLogger logger, StreamConsumer consumer, Commandline cl) throws ScmException {
        CommandLineUtils.StringStreamConsumer stderr = new CommandLineUtils.StringStreamConsumer();

        int exitCode = GitCommandLineUtils.execute(cl, consumer, stderr, logger);

        if (exitCode != 0)
        {
            throw new ScmException("Git command failed: " + stderr.getOutput());
        }
    }

    private static class RevParseConsumer extends AbstractConsumer {

        private String rev;

        public RevParseConsumer(final ScmLogger logger) {
            super(logger);
        }

        public void consumeLine(final String line) {
            rev = line.trim();
        }

        public String getRev() {
            return rev;
        }
    }

    private static class ShowRefForSha1Consumer extends AbstractConsumer {

        /* Pattern doesn't support remote names with a forward slash in it, but what are the odds? :-) */
        private static final Pattern SHOW_REF_PATTERN = Pattern.compile("(?<sha1>[a-z0-9]{40}) (?:refs/heads/|refs/remotes/[\\w-.]+/)(?<branch>[\\w-/.]+)");
        private final String sha1HEAD;
        private final Set<String> branches = new HashSet<>();

        public ShowRefForSha1Consumer(final String sha1HEAD, final ScmLogger logger) {
            super(logger);
            this.sha1HEAD = sha1HEAD;
        }

        public void consumeLine(final String line) {
            Matcher m = SHOW_REF_PATTERN.matcher(line.trim());
            if (m.matches()) {
                String sha1 = m.group("sha1");
                if (sha1.equals(sha1HEAD)) {
                    String branch = m.group("branch");
                    branches.add(branch);
                }
            }
        }

        public Set<String> getBranches() {
            return branches;
        }
    }
}
