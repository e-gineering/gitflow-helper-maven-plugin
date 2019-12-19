package com.e_gineering.maven.gitflowhelper;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.resolution.ArtifactResult;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * A helper factory for creating maven (GroupId, ArtifactId, Extension, Classifier, Version) coordinates.
 */
class DefaultGavCoordinateHelper implements GavCoordinateHelper {
    
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
        this.session = Objects.requireNonNull(session, "session must not be null");
        this.project = Objects.requireNonNull(project, "project must not be null");
        this.log = Objects.requireNonNull(log, "log must not be null");
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
    
    private static String emptyToNull(final String s) {
        return StringUtils.isBlank(s) ? null : s;
    }

    @Override
    public String getCoordinates(Artifact artifact) {
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
        Objects.requireNonNull(groupId, "groupId must not be null");
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(version, "version must not be null");
        
        StringBuilder result = new StringBuilder();
        for (String s : new String[]{groupId, artifactId, extension, classifier, version}) {
            if (s != null) {
                if (result.length() > 0) {
                    result.append(":");
                }
                result.append(s);
            }
        }
        return result.toString();
    }
}
