package com.e_gineering.maven.gitflowhelper;

import com.e_gineering.maven.gitflowhelper.properties.PropertyResolver;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.component.annotations.Component;

import java.util.HashMap;
import java.util.Map;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = "other-branch-version")
public class OtherBranchVersionExtension extends AbstractBranchDetectingExtension {
    
    @Override
    public void afterProjectsRead(final MavenSession session) throws MavenExecutionException {
        super.afterProjectsRead(session);
        
        if (pluginFound) {
            logger.debug("other-branch-version extension active.");
            if (branchInfo != null) {
                logger.debug("other-branch-version extension on git branch: " + branchInfo.toString());
                if(branchInfo.getType().equals(GitBranchType.OTHER)) {
                    String otherBranchesToDeploy = PropertyResolver.resolveValue(otherDeployBranchPattern, session.getTopLevelProject().getProperties(), systemEnvVars);
                    logger.debug("OTHER branch detected. Testing against pattern: `" + otherBranchesToDeploy + "`");
                    if (!"".equals(otherBranchesToDeploy) && branchInfo.getName().matches(otherBranchesToDeploy)) {
                        HashMap<MavenProject, String> oldVersions = new HashMap<>();
                        
                        // Update the versions on all the project modules.
                        for (MavenProject project : session.getProjects()) {
                            String newVersion = getAsBranchSnapshotVersion(project.getVersion(), branchInfo.getName());
                            oldVersions.put(project, project.getVersion());
                            logger.info("Updating project " + project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion() + " to: " + newVersion);
                            project.setVersion(newVersion);
                        }
                        
                        // Now enumerate all the projects looking for dependencies that use the updated projects.
                        for (MavenProject project: session.getProjects()) {
                            // Dependency Management
                            if (project.getDependencyManagement() != null) {
                                for (Dependency dep : project.getDependencyManagement().getDependencies()) {
                                    for (Map.Entry<MavenProject, String> adjustedProjects : oldVersions.entrySet()) {
                                        if (isProjectOfDependency(adjustedProjects.getKey(), dep)) {
                                            logger.debug("Updating project: " + project + " managed dependency: " + dep);
                                            dep.setVersion(adjustedProjects.getValue());
                                        }
                                    }
                                }
                            }
                            
                            // Standard Dependencies
                            for (Dependency dep : project.getDependencies()) {
                                for (Map.Entry<MavenProject, String> adjustedProjects : oldVersions.entrySet()) {
                                    if (isProjectOfDependency(adjustedProjects.getKey(), dep)) {
                                        dep.setVersion(adjustedProjects.getValue());
                                    }
                                }
                            }

                            // Plugin Management
                            if (project.getPluginManagement() != null) {
                                for (Plugin plugin : project.getPluginManagement().getPlugins()) {
                                    for (Map.Entry<MavenProject, String> adjustedProjects : oldVersions.entrySet()) {
                                        if (isProjectOfPlugin(adjustedProjects.getKey(), plugin)) {
                                            plugin.setVersion(adjustedProjects.getValue());
                                        }
                                    }
                                }
                            }
                            
                            // Plugins
                            for (Plugin plugin : project.getBuildPlugins()) {
                                for (Map.Entry<MavenProject, String> adjustedProjects : oldVersions.entrySet()) {
                                    if (isProjectOfPlugin(adjustedProjects.getKey(), plugin)) {
                                        plugin.setVersion(adjustedProjects.getValue());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private boolean isProjectOfPlugin(MavenProject project, Plugin plugin) {
        return project.getGroupId().equals(plugin.getGroupId()) &&
                project.getArtifactId().equals(plugin.getArtifactId()) &&
                project.getVersion().equals(plugin.getVersion());
    }
    
    private boolean isProjectOfDependency(MavenProject project, Dependency dependency) {
        return project.getGroupId().equals(dependency.getGroupId()) &&
                project.getArtifactId().equals(dependency.getArtifactId()) &&
                project.getVersion().equals(dependency.getVersion());
    }
    
    /**
     * Given a String version (which may be a final or -SNAPSHOT version) return a
     * version version string mangled to include a `+normalized-branch-name-SNAPSHOT format version.
     *
     * @param version The base version (ie, 1.0.2-SNAPSHOT)
     * @param branchName to be normalized
     * @return A mangled version string with the branchname and -SNAPSHOT.
     */
    private String getAsBranchSnapshotVersion(final String version, final String branchName) {
        return version.replace("-SNAPSHOT", "") + otherBranchVersionDelimiter + branchName.replaceAll("[^0-9A-Za-z-.]", "-") + "-SNAPSHOT";
    }
    
}
