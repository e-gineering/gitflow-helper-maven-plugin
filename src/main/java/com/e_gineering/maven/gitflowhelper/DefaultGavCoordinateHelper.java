package com.e_gineering.maven.gitflowhelper;

import com.google.common.base.Joiner;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.resolution.ArtifactResult;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.emptyToNull;

/**
 * A helper factory for creating maven (GroupId, ArtifactId, Extension, Classifier, Version) coordinates.
 */
class DefaultGavCoordinateHelper implements GavCoordinateHelper {

    private static final Joiner GAV_JOINER = Joiner.on(':').skipNulls();

    private final Logger log;

    private final MavenProject project;

    private final RepositorySystemSession session;

    /**
     * Creates a new {@link DefaultGavCoordinateHelperFactory}.
     *
     * @param session the repository session
     * @param project the project
     * @param log     the logger
     */
    DefaultGavCoordinateHelper(RepositorySystemSession session, MavenProject project, Logger log) {
        this.session = checkNotNull(session, "session must not be null");
        this.project = checkNotNull(project, "project must not be null");
        this.log = checkNotNull(log, "log must not be null");
    }

    @Override
    public String getCoordinates(ArtifactResult result) {
        return getCoordinates(result.getArtifact());
    }

    @Override
    public String getCoordinates(org.eclipse.aether.artifact.Artifact artifact) {
        return getCoordinates(
                emptyToNull(artifact.getGroupId()),
                emptyToNull(artifact.getArtifactId()),
                emptyToNull(artifact.getBaseVersion()),
                emptyToNull(artifact.getExtension()),
                emptyToNull(artifact.getClassifier())
        );
    }

    @Override
    public String getCoordinates(Artifact artifact) {
        log.debug("   Encoding Coordinates For: " + artifact);

        // Get the extension according to the artifact type.
        String extension = artifact.getArtifactHandler().getExtension();

        return getCoordinates(
                artifact.getGroupId(),
                artifact.getArtifactId(),
                project.getVersion(),
                extension, artifact.hasClassifier() ? artifact.getClassifier() : null
        );
    }

    private String getCoordinates(String groupId,
                                  String artifactId,
                                  String version,
                                  @Nullable String extension,
                                  @Nullable String classifier) {
        checkNotNull(groupId, "groupId must not be null");
        checkNotNull(artifactId, "artifactId must not be null");
        checkNotNull(version, "version must not be null");
        return GAV_JOINER.join(groupId, artifactId, extension, classifier, version);
    }
}
