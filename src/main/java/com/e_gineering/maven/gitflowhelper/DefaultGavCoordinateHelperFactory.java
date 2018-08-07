package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.aether.RepositorySystemSession;

/**
 * A helper factory for creating {@link GavCoordinateHelper} instances scoped to a repository session.
 */
@Component(role = GavCoordinateHelperFactory.class)
class DefaultGavCoordinateHelperFactory implements GavCoordinateHelperFactory {

    @Requirement
    private Logger log;

    @Requirement
    private MavenProject project;

    @Override
    public GavCoordinateHelper using(RepositorySystemSession session) {
        return new DefaultGavCoordinateHelper(session, project, log);
    }
}
