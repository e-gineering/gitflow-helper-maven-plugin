# gitflow-helper-maven-plugin [![Build Status](https://travis-ci.org/egineering-llc/gitflow-helper-maven-plugin.svg?branch=master)](https://travis-ci.org/egineering-llc/gitflow-helper-maven-plugin)

A maven plugin intended to be used in conjunction with Jenkins / Hudson builds for :

 * Enforcing [gitflow](http://nvie.com/posts/a-successful-git-branching-model/) version heuristics in [Maven](http://maven.apache.org/) projects.
 * Setting a [maven-deploy-plugin](https://maven.apache.org/plugins/maven-deploy-plugin/) repository based upon the current git branch.
 * Tagging a git revision if a CI build on the master branch is successful, using the CI server's repository connection. (Zero Maven scm configuration necessary)
 * Promoting existing test deployments for release, rather than re-building the artifact. Eliminates the risk of accidental master merges or commits resulting in untested code being release. 

# Why would I want to use this?

This plugin solves a few specific issues common in a consolidated Hudson/Jenkins Continuous Integration (CI) and Continuous Delivery (CD) job.

 1. Ensure the developers are following the (git branching) project version rules, and fail the build if they are not.
 2. Enable the maven-deploy-plugin to target a snapshots, test-releases, and releases repository.
 3. _Copy_ (rather than rebuild) the tested artifacts from the test-releases repository to the release repository, without doing a full project rebuild from the master branch.
 4. Reliably tag deploy builds from the 'master' branch
 
# I want all of that. (Usage)

The above tasks can be enabled on your build by configuring the plugin goals with the default lifecycle bindings, and adding the build extension.

    <project...>
    ...
    <build>
        <plugins>
            <plugin>
                <groupId>com.e-gineering</groupId>
                <artifactId>gitflow-helper-maven-plugin</artifactId>
                <version>${gitflow.helper.plugin.version}</version>
                <configuration>
                    <!-- These repository definitions expect id::layout::url::unique 
                         release::default::https://some.server.path/content/repositories/test-releases::false
                    -->
                    <releaseDeploymentRepository>${release.repository}</releaseDeploymentRepository>
                    <testDeploymentRepository>${test.repository}</testDeploymentRepository>
                    <snapshotDeploymentRepository>${snapshot.repository}</snapshotDeploymentRepository>
                    <tag>${project.artifactId}-${project.version}</tag>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>enforce-versions</goal>
                            <goal>retarget-deploy</goal>
                            <goal>tag-master</goal>
                            <goal>promote-master</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
        
        ...
        
        <extensions>
            <extension>
                <groupId>com.e-gineering</groupId>
                <artifactId>gitflow-helper-maven-plugin</artifactId>
                <version>${gitflow.helper.plugin.version}</version>
            </extension>
            
            ...
            
        </extensions>
    </build>
    
    ...
    
    
    </project>


## Version Assertion

// TODO: Elaborate.

## Deployment Targeting

Using freely available tooling, it becomes much easier to handle CI/CD with small teams by separating the concepts of 'deployment' and 'promotion'.

If you've ever wished Maven had more than two repositories for you to target (snapshots or releases), then this plugin may be for you.

Promotion becomes the process of taking a releasable version (built from source) and merging those changes into another 
branch which will deploy to a more mature repository. As builds are deployed to repositories, they can then be promoted 
and installed into an environment.

You can achieve the gold standard of CI/CD with tools like [Jenkins](https://jenkins-ci.org/), [Bitbucket Server](https://www.atlassian.com/software/bitbucket/server) (formerly Stash), and [Nexus](http://www.sonatype.org/nexus/), and keep it simple enough for small (or large!) teams to effectively use and maintain.

// TODO: Elaborate.

## Master Branch (Release) Tagging

// TODO: Elaborate

## Master Branch (Release) Promotion

// TODO: Elaborate
