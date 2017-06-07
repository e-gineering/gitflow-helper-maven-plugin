package com.e_gineering.maven.gitflowhelper;

import org.eclipse.aether.RepositorySystemSession;

/**
 * A {@link GavCoordinateHelper} factory.
 */
public interface GavCoordinateHelperFactory {

    String ROLE = GavCoordinateHelperFactory.class.getName();

    /**
     * Create a coordinate helper scoped to the {@link RepositorySystemSession}.
     *
     * @param session the session
     * @return the coordinate helper
     */
    GavCoordinateHelper using(RepositorySystemSession session);
}
