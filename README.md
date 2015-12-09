# gitflow-helper-maven-plugin [![Build Status](https://travis-ci.org/egineering-llc/gitflow-helper-maven-plugin.svg?branch=master)](https://travis-ci.org/egineering-llc/gitflow-helper-maven-plugin)

A build extension and plugin that makes Maven work with gitflow projects and CI servers.

It does so by:

 * Enforcing [gitflow](http://nvie.com/posts/a-successful-git-branching-model/) version heuristics in [Maven](http://maven.apache.org/) projects.
 * Setting a [maven-deploy-plugin](https://maven.apache.org/plugins/maven-deploy-plugin/) repository based upon the current git branch.
 * Tagging a git revision if a CI build on the master branch is successful, using the CI server's repository connection. (Zero Maven scm configuration necessary)
 * Promoting existing tested (staged) artifacts for release, rather than re-building the artifacts. Eliminates the risk of accidental master merges or commits resulting in untested code being released, and provides digest hash traceability for the history of artifacts.
 * Automated deployment, promotion, and delivery of projects without the [maven-release-plugin](http://maven.apache.org/maven-release/maven-release-plugin/) or some other [*almost there* solution](https://axelfontaine.com/blog/final-nail.html).
 * Coercing Maven to gracefully support the gitflow workflow, without imposing complex build configurations and leveraging CI systems to provide sensible off-the-shelf configurations.

# Why would I want to use this?

This plugin solves a few specific issues common in consolidated Hudson/Jenkins Continuous Integration (CI) and Continuous Delivery (CD) jobs for projects following the gitflow workflow.

 1. Ensure the developers are following the (git branching) project version rules, and fail the build if they are not.
 2. Enable the maven-deploy-plugin to target a snapshots, test-releases, and releases repository.
 3. _Copy_ (rather than rebuild) the tested artifacts from the test-releases repository to the release repository, without doing a full project rebuild from the master branch.
 4. Reliably tag deploy builds from the 'master' branch
 
In addition to supporting these goals for the project, this plugin does it in a manner that tries to be as effortless (yet configurable) as possible.
If you use non-standard gitflow branch names (emer instead of hotfix), this plugin supports that. If you don't want to do version enforcement, this plugin supports that. 
If you want to use scm tagging with a custom tag format, we support that. If you want to use scm tagging **without having to add the <scm> section to your pom.xml or adding arcane -Dproperty arguments to your Maven command**, this plugin supports that. 
If you want to do three-tier deployments (snapshot, stage, production) without 'professional' artifact repository tools, and **without having to define a <distributionManagement> section to your pom.xml**, yep, this plugin supports that too.
 
# I want all of that. (Usage)

 1. Make sure your build server sets environment variables for GIT_BRANCH, and GIT_URL. Jenkins & Hudson do this by default. The GIT_URL needs to be a developer connection type URL.
 2. Configure the plugin goals and add the build extension to your Maven project. Here's an example that will get you going quickly...

```
<project>
    ...
    <build>
        <plugins>
            <plugin>
                <groupId>com.e-gineering</groupId>
                <artifactId>gitflow-helper-maven-plugin</artifactId>
                <version>${gitflow.helper.plugin.version}</version>
                <configuration>
                    <!-- These repository definitions expect id::layout::url::unique, for example
                         release::default::https://some.server.path/content/repositories/test-releases::false
                    -->
                    <releaseDeploymentRepository>${release.repository}</releaseDeploymentRepository>
                    <stageDeploymentRepository>${stage.repository}</stageDeploymentRepository>
                    <snapshotDeploymentRepository>${snapshot.repository}</snapshotDeploymentRepository>
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
```

## Goal: `enforce-versions` (Version & Branch Name Assertions)

One common stumbling block for teams adjusting to gitflow with Maven projects is the discipline required to keep Maven version numbers up to date.
In practice, the Maven versions should:
 
 * Be synchronized with release branch and hotfix branch names.
 * Never be -SNAPSHOT in the master branch, release, hotfix, or bugfix branches.
 * Always be -SNAPSHOT in the development or feature branches.
 * Be irrelevant if there's no GIT_BRANCH set in your environment.

The `enforce-versions` goal asserts these semantics when a `GIT_BRANCH` variable is detected in the build environment.

The goal accomplishes this by checking the Maven pom.xml version value, and asserting the -SNAPSHOT status, as well as matching the current branch name
against regular expressions, extracting version numbers from the branch names where applicable. If a regex specifies a subgroup 1, the content of that 
subgroup is asserted to equal the version defined in the pom.xml.

The following properties change the behavior of this goal:

| Property             | Default Value | SNAPSHOT allowed? | Description |
| -------------------- | ------------- | --------------------------- | ----------- |
| masterBranchPattern  | origin/master | No | Regex. When matched, signals the master branch is being built. Note the lack of a subgroup. |
| releaseBranchPattern | origin/release/(.*) | No | Regex. When matched, signals a release branch being built. Subgroup 1, if present, must match the Maven project version. |
| hotfixBranchPattern  | origin/hotfix/(.*) | No | Regex. When matched, signals a hotfix branch is being built. Subgroup 1, if present, must match the Maven project version. |
| bugfixBranchPattern  | origin/bugfix/.* | No | Regex. When matched, signals a bugfix branch is being built. Note the lack of a subgroup. |
| developmentBranchPattern | origin/development | Yes | Regex. When matched, signals a development branch is being built. Note the lack of a subgroup. |

## Goal: `retarget-deploy` (Branch Specific Deploy Targets & Staging)

One of the challenges of building a good CI/CD job for Maven environments is the lack of a 'staging' repository baked into Maven.
The maven-release-plugin does introduce a concept of a staging repository, but the imposed workflow from the release plugin is incompatible with CI 
jobs and the gitflow model.

For projects being managed with the gitflow workflow model, release and hotfix branches should be deployed to a stage repository, where artifacts can
be tested and validated prior to being deployed to the release repository.

The `retarget-deploy` goal sets the snapshot and release repository for the project, based upon the build environment `GIT_BRANCH` value. Subsequent 
plugins in the build process (deploy, site-deploy, etc.) will use the repositories set by the `retarget-deploy` goal.

| Property | Description | 
| -------- | ----------- |
| releaseDeploymentRepository | The repository to use for releases. (Builds with a GIT_BRANCH matching `masterBranchPattern`) |
| stageDeploymentRepository | The repository to use for staging. (Builds with a GIT_BRANCH matching `releaseBranchPattern` or `hotfixBranchPattern` | 
| snapshotDeploymentRepository | The repository to use for snapshots. (Builds matching `developmentBranchPattern` |

**The repository properties should follow the following format**, `id::layout::url::uniqueVersion`.

When using this plugin, the `<distributionManagement>` repository definitions can be completely removed from your pom.xml 
The following configuration block:

        <distributionManagement>
            <snapshotRepository>
                <id>snapshots</id>
                <layout>default</layout>
                <url>https://some.server.path/content/repositories/snapshots</url>
                <uniqueVersion>true</uniqueVersion>
            </snapshotRepository>
            <repository>
                <id>releases</id>
                <layout>default</layout>
                <url>https://some.server.path/content/repositories/releases</url>
                <uniqueVersion>false</uniqueVersion>
            </repository>
        </distributionManagement>
        
Can be replaced with the following plugin configuration, which also introduces the stage repository.

    <project...>
    ...
    <build>
        <plugins>
            <plugin>
                <groupId>com.e-gineering</groupId>
                <artifactId>gitflow-helper-maven-plugin</artifactId>
                <version>${gitflow.helper.plugin.version}</version>
                <configuration>
                    <releaseDeploymentRepository>releases::default::>https://some.server.path/content/repositories/releases::false</releaseDeploymentRepository>
                    <stageDeploymentRepository>stage::default::>https://some.server.path/content/repositories/stage::false</stageDeploymentRepository>
                    <snapshotDeploymentRepository>snapshots::default::>https://some.server.path/content/repositories/snapshots::true</snapshotDeploymentRepository>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>retarget-deploy</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
        ...
    </build>

## Goal: `tag-master` ("Automagic" Tagging for Master Branch Releases)

In a gitflow environment, a commit to a master branch should trigger a job to build on the master branch, which would result in the release being tagged if successful.
 
The `tag-master` goal executes the [maven-scm-plugin tag goal](https://maven.apache.org/scm/maven-scm-plugin/tag-mojo.html) for a build
being run with a `GIT_BRANCH` environment variable matching the `masterBranchPattern` regular expression. To determine the SCM URL to use, it does not 
make use of the `<scm>` elements in the pom.xml, but rather picks up the URL from a `GIT_URL` environment variable. The `GIT_URL` is set by default by 
Jenkins and Hudson CI servers.

The following properties can be configured for this goal:

| Property             | Default Value | Description |
| -------------------- | ------------- | ----------- |
| masterBranchPattern  | origin/master | Regex. When matched, signals the master branch is being built. |
| tag                  | ${project.version} | An expression to use for the SCM tag. |
| tag.plugin.groupId   | org.apache.maven.plugins | The groupId of the plugin to use for tagging. |
| tag.plugin.artifactId | maven-scm-plugin | The artifactId of the plugin to use for tagging. | 
| tag.plugin.version | 1.9.4 | The version of the plugin to use for tagging. |


## Goal: `promote-master` and the Build Extension. (Copy Staged Artifacts to Releases)

With gitflow, a new version of a product is prepared in the `release/.*` and `hotfix/.*` branches of the project.
These artifacts are put through their paces and validated before the merge back into the master branch.

In a traditional CI approach, the merge to master triggers a build, which gets deployed to a releases repository, and perhaps deployed to an execution 
environment. This approach has the consequence of deployed artifacts in the release repository having never been tested in a stage or test environment.
Sure, you've tested the branch, but the actual artifact from the stage repository is what you *really* want to have deployed to the release repository.

If stage artifacts are copied into the releases repository when a master commit occurs (ex: the merge from release/2.3.4.5 into master) then the 
artifacts will have the same SHA and MD5 hash, and you'd have full trace-ability for the lifecycle of the artifacts. You'd also have the added benefit 
of achieving the ideal situation for gitflow deployment, where releases originate from the branches created for them, and code is **never deployed  
directly from master**. Rather, master is really only used for tracking releases and branching to support production issues.

To accomplish this the `promote-master` goal and a Maven build extension work together.

With the build extension added to your project, any build with a `GIT_BRANCH` environment variable matching the `masterBranchPattern` will have its
build lifecycle (plugins, goals, etc) altered. Any plugin other than the gitflow-helper-maven-plugin, or the maven-deploy-plugin will be ignored 
(removed from the project reactor). This allows us to enforce the ideal that code should never be built in the master branch.

The `promote-master` goal executes for a build with a `GIT_BRANCH` environment variable matching the `masterBranchPattern` regular expression.
 
This goal resolves (and downloads) the artifacts matching the current `${project.version}` from the stage repository, then attaches them to the 
current project in the Maven build. This lets later plugins in the lifecycle (like the deploy plugin, which the extension won't remove) make use of 
artifacts provided from the stage repository when it uploads to the releases repository. Effectively, this makes a build in master copy the artifacts from 
the stage repository to the releases repository.




