package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * A helper factory for creating maven (GroupId, ArtifactId, Extension, Classifier, Version) coordinates.
 */
public interface GavCoordinateHelper {
    /**
     * Get GAV coordinates for the {@link ArtifactResult}.
     *
     * @param result the result
     * @return the GAV coordinate string
     */
    String getCoordinates(ArtifactResult result);

    /**
     * Get GAV coordinates for the {@link org.eclipse.aether.artifact.Artifact}.
     *
     * @param artifact the artifact
     * @return the GAV coordinate string
     */
    String getCoordinates(org.eclipse.aether.artifact.Artifact artifact);

    /**
     * Get GAV coordinates for the {@link Artifact}.
     *
     * @param artifact the artifact
     * @return the GAV coordinate string
     */
    String getCoordinates(Artifact artifact);
}
