package com.e_gineering.maven.gitflowhelper;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFile;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.log.ScmLogDispatcher;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.provider.ScmProvider;
import org.apache.maven.scm.provider.git.gitexe.command.branch.GitBranchCommand;
import org.apache.maven.scm.provider.git.repository.GitScmProviderRepository;
import org.apache.maven.scm.repository.ScmRepository;

public abstract class ScmUtils {
    public static String getGitBranch(ScmManager scmManager, MavenProject project) throws ScmException {
        String scmConnectionUrl = project.getScm().getConnection();
        String scmDeveloperConnectionUrl = project.getScm().getDeveloperConnection();
        String connectionUrl = StringUtils.isNotBlank(scmDeveloperConnectionUrl) ? scmDeveloperConnectionUrl : scmConnectionUrl;
        if (StringUtils.isBlank(connectionUrl)) {
            return "${env.GIT_BRANCH}";
        }

        ScmRepository repository = scmManager.makeScmRepository(connectionUrl);
        ScmProvider provider = scmManager.getProviderByRepository(repository);
        if (!GitScmProviderRepository.PROTOCOL_GIT.equals(provider.getScmType())) {
            return null;
        } else {
            ScmFileSet fileSet = new ScmFileSet(project.getBasedir());
            return GitBranchCommand.getCurrentBranch(new ScmLogDispatcher(), (GitScmProviderRepository)repository.getProviderRepository(), fileSet);
        }
    }
}
