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
import org.apache.maven.scm.provider.git.gitexe.command.GitCommandLineUtils;
import org.apache.maven.scm.provider.git.gitexe.command.branch.GitBranchCommand;
import org.apache.maven.scm.provider.git.repository.GitScmProviderRepository;
import org.apache.maven.scm.repository.ScmRepository;
import org.apache.maven.scm.util.AbstractConsumer;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ScmUtils {

    private static final String DEFAULT_URL_EXPRESSION = "${env.GIT_URL}";
    private static final String DEFAULT_BRANCH_EXPRESSION = "${env.GIT_BRANCH}";

    private Properties systemEnvVars;
    private ScmManager scmManager;
    private MavenProject project;
    private Log log;
    private String masterBranchPattern;
    private String supportBranchPattern;
    private String releaseBranchPattern;
    private String hotfixBranchPattern;
    private String developmentBranchPattern;
    private String featureOrBugfixBranchPattern;

    public ScmUtils(final Properties systemEnvVars, final ScmManager scmManager, final MavenProject project, final Log log,
                    final String masterBranchPattern, final String supportBranchPattern, final String releaseBranchPattern,
                    final String hotfixBranchPattern, final String developmentBranchPattern)
    {
        this.systemEnvVars = systemEnvVars;
        this.scmManager = scmManager;
        this.project = project;
        this.log = log;
        this.masterBranchPattern = masterBranchPattern;
        this.supportBranchPattern = supportBranchPattern;
        this.releaseBranchPattern = releaseBranchPattern;
        this.hotfixBranchPattern = hotfixBranchPattern;
        this.developmentBranchPattern = developmentBranchPattern;
        this.featureOrBugfixBranchPattern = featureOrBugfixBranchPattern;
    }

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
     * Attempts to resolve the current branch of the build.
     */
    public GitBranchInfo resolveBranchInfo(final String gitBranchExpression) {
        // Start off with the name or expression provided from the config parameter.
        // Remember, the config parameter may be `null` (it is by default).
        String branchNameOrExpression = gitBranchExpression;

        // Make sure we have a non-null value. (may have been passed in from the @Parameter)
        if (branchNameOrExpression == null) {
            log.debug("Git branch expression was null, defaulting to: " + DEFAULT_BRANCH_EXPRESSION);
            branchNameOrExpression = DEFAULT_BRANCH_EXPRESSION;
        }

        // Now force it to resolve any properties.
        String resolvedBranchName = PropertyResolver.resolveValue(branchNameOrExpression, project.getProperties(), systemEnvVars);

        // It's possible that we were unable to expand all the properties.
        // In this case we'll try reading the git branch name by invoking the git command.
        ExpansionBuffer eb = new ExpansionBuffer(resolvedBranchName);
        if (eb.hasMoreLegalPlaceholders()) {
            resolvedBranchName = getBranchNameFromGit();
            log.debug("Git branch expression couldn't be resolved, using branch name from git metadata: " + resolvedBranchName);
        }

        if (!branchNameOrExpression.equals(resolvedBranchName) || log.isDebugEnabled()) { // Resolves Issue #9
            if (log.isDebugEnabled()) {
                log.debug("Resolved gitBranchExpression: '" + gitBranchExpression + "' to '" + resolvedBranchName + "'");
            }
        }

        return resolveBranchType(resolvedBranchName);
    }

    private String getBranchNameFromGit() {
        String connectionUrl = resolveUrlOrExpression(project);

        String result = null;
        try {
            ScmRepository repository = scmManager.makeScmRepository(connectionUrl);
            if (!GitScmProviderRepository.PROTOCOL_GIT.equals(scmManager.getProviderByRepository(repository).getScmType())) {
                throw new ScmException("Unable to resolve branches from non-git <scm> definitions.");
            }

            // We know it's a GIT repo...
            GitScmProviderRepository gitScmProviderRepository = (GitScmProviderRepository) repository.getProviderRepository();

            ScmFileSet fileSet = new ScmFileSet(project.getBasedir());
            ScmLogDispatcher scmLogger = new ScmLogDispatcher();

            try {
                result = GitBranchCommand.getCurrentBranch(scmLogger, gitScmProviderRepository, fileSet);
            } catch (ScmException scme) {
                log.debug("Exception attempting to resolve a local branch. Attempting detached HEAD resolution");

                // Try to resolve a detached HEAD to a single branch.
                // If we have more than one branch resolving, make sure they're the same type.
                // If there are more than one _type_ of branch resolved for the detached HEAD, then we'll need to
                // fall back to the branchNameOrExpression (to resolve via environment properties).

                // Do a rev-parse to get the commit hash that HEAD points to
                String sha1 = sha1ForHEAD(scmLogger, fileSet);
                log.debug("HEAD is pointing at " + sha1);

                // Now use show-ref to determine the branches that HEAD's sha1 points to.
                Set<String> branches = branchesForSha1(sha1, scmLogger, fileSet);
                log.debug("Found the following branches for " + sha1 + ": " + branches);

                // State tracking as we loop
                GitBranchType type = null;
                String name = null;

                for (String candidateName : branches) {
                    GitBranchType candidateType = resolveBranchType(candidateName).getType();
                    // First iteration of a resolved type.
                    if (type == null){
                        type = candidateType;
                        name = candidateName; // Use the first name we get.
                        continue;
                    }

                    // A subsequent branch which resolved.
                    if (candidateType != type) {
                        throw new ScmException("Multiple branches with different types resolved for " + sha1);
                    }

                    // If a branch type is a versioned branch, there can be only one branch type matching that version.
                    if (GitBranchType.UNIQUELY_VERSIONED_TYPES.contains(candidateType)) {
                        throw new ScmException("Multiple branches of different type reference the same release version for " + sha1);
                    }
                }

                // Detached head resolution was successful.
                // Either we iterated once, or all of the subsequent types were resolved to the same type as the
                // first branch, and there was only one of those branches which may have been a uniquely versioned branch.
                result = name;
            }
        } catch (ScmException scme) {
            // Only do the following if the SCM resolution fails miserably.
            log.warn("Unable to resolve a branch from SCM. Falling back to property replacement.", scme);
        } catch (IllegalArgumentException iae) {
            log.debug("IllegalArgumentException likely the result of the <scm> block missing from the pom.xml", iae);
        }
        return result;
    }

    private GitBranchInfo resolveBranchType(String branchName) {
        if (branchName == null || branchName.equals("") || branchName.equals(DEFAULT_BRANCH_EXPRESSION)) {
            return new GitBranchInfo("", GitBranchType.UNDEFINED, null); // Force UNDEFINED to be "" for the name.
        } else if (branchName.matches(masterBranchPattern)) {
            return new GitBranchInfo(branchName, GitBranchType.MASTER, masterBranchPattern);
        } else if (branchName.matches(supportBranchPattern)) {
            return new GitBranchInfo(branchName, GitBranchType.SUPPORT, supportBranchPattern);
        } else if (branchName.matches(releaseBranchPattern)) {
            return new GitBranchInfo(branchName, GitBranchType.RELEASE, releaseBranchPattern);
        } else if (branchName.matches(hotfixBranchPattern)) {
            return new GitBranchInfo(branchName, GitBranchType.HOTFIX, hotfixBranchPattern);
        } else if (branchName.matches(developmentBranchPattern)) {
            return new GitBranchInfo(branchName, GitBranchType.DEVELOPMENT, developmentBranchPattern);
        } else {
            return new GitBranchInfo(branchName, GitBranchType.OTHER, null);
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
