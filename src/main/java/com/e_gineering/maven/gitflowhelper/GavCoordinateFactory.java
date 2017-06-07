package com.e_gineering.maven.gitflowhelper;

import com.google.common.base.Joiner;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.resolution.ArtifactResult;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.emptyToNull;

/**
 * A helper factory for creating maven (GroupId, ArtifactId, Extension, Classifier, Version) coordinates.
 */
class GavCoordinateFactory {
    // TODO(somebody): Consider using maven @Component to make this an injectable component in the mojo

    private static final Joiner GAV_JOINER = Joiner.on(':').skipNulls();

    private final RepositorySystemSession session;

    private final MavenProject project;
    private final Log log;

    /**
     * Creates a new {@link GavCoordinateFactory}.
     *
     * @param session the repository session
     * @param project the project
     * @param log     the logger
     */
    GavCoordinateFactory(RepositorySystemSession session, MavenProject project, Log log) {
        this.session = checkNotNull(session, "session must not be null");
        this.project = checkNotNull(project, "project must not be null");
        this.log = checkNotNull(log, "log must not be null");
    }

    /**
     * Get GAV coordinates for the {@link ArtifactResult}.
     *
     * @param result the result
     * @return the GAV coordinate string
     */
    String getCoordinates(ArtifactResult result) {
        return getCoordinates(result.getArtifact());
    }

    /**
     * Get GAV coordinates for the {@link org.eclipse.aether.artifact.Artifact}.
     *
     * @param artifact the artifact
     * @return the GAV coordinate string
     */
    @SuppressWarnings("WeakerAccess")
    String getCoordinates(org.eclipse.aether.artifact.Artifact artifact) {
        return getCoordinates(
                emptyToNull(artifact.getGroupId()),
                emptyToNull(artifact.getArtifactId()),
                emptyToNull(artifact.getBaseVersion()),
                emptyToNull(artifact.getExtension()),
                emptyToNull(artifact.getClassifier())
        );
    }

    /**
     * Get GAV coordinates for the {@link Artifact}.
     *
     * @param artifact the artifact
     * @return the GAV coordinate string
     */
    String getCoordinates(Artifact artifact) {
        log.debug("   Encoding Coordinates For: " + artifact);

        // Get the extension according to the artifact type.
        String extension = session.getArtifactTypeRegistry().get(artifact.getType()).getExtension();

        // assert that the file extension matches the artifact packaging extension type, if there is an artifact file.
        if (artifact.getFile() != null && !artifact.getFile().getName().toLowerCase().endsWith(extension.toLowerCase())) {
            String filename = artifact.getFile().getName();
            String fileExtension = filename.substring(filename.lastIndexOf('.') + 1);
            log.warn(
                    "    Artifact file name: " + artifact.getFile().getName() + " of type "
                            + artifact.getType() + " does not match the extension for the ArtifactType: "
                            + extension + ". "
                            + "This is likely an issue with the packaging definition for '" + artifact.getType()
                            + "' artifacts, which may be missing an extension definition. "
                            + "The gitflow helper catalog will use the actual file extension: " + fileExtension
            );
            extension = fileExtension;
        }

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
