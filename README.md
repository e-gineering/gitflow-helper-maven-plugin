# gitflow-helper-maven-plugin [![Build Status](https://travis-ci.org/egineering-llc/gitflow-helper-maven-plugin.svg?branch=master)](https://travis-ci.org/egineering-llc/gitflow-helper-maven-plugin) [![Coverage Status](https://coveralls.io/repos/github/egineering-llc/gitflow-helper-maven-plugin/badge.svg)](https://coveralls.io/github/egineering-llc/gitflow-helper-maven-plugin)

A build extension and plugin that helps Maven play nicely with gitflow projects, CI servers and local development.

It does so by:

 * Enforcing [gitflow](http://nvie.com/posts/a-successful-git-branching-model/) version heuristics in [Maven](http://maven.apache.org/) projects.
 * Coercing Maven to gracefully support the gitflow workflow without imposing complex CI job configurations or complex Maven setups.
    * Setting distributionManagement repositories (for things like [maven-deploy-plugin](https://maven.apache.org/plugins/maven-deploy-plugin/)) based upon the current git branch.
    * SCM tagging builds for master and support branches. You can use the project SCM definition, or if you omit it, you canO resolve the CI server's repository connection information. (Zero Maven scm configuration necessary)
    * Promoting existing tested (staged) artifacts for release, rather than re-building the artifacts. Eliminates the risk of accidental master merges or commits resulting in untested code being released, and provides digest hash traceability for the history of artifacts.
    * Enabling the decoupling of repository deployment and execution environment delivery based on the current git branch.
    * Allowing for long-running non-release branches to be deployed to snapshots, automatically reversioning the artifacts based off the branch name.
 * Automated deployment, promotion, and delivery of projects without the [maven-release-plugin](http://maven.apache.org/maven-release/maven-release-plugin/) or some other [*almost there* solution](https://axelfontaine.com/blog/final-nail.html).
 * Customizing maven project and system properties based upon the current branch being built. This allows test cases to target different execution environments without changing the artifact results.
 * Enabling automatic purging and resolving (force update) of 'release' and 'hotfix' release versioned dependencies resolved from the 'stage' repository.

# Why would I want to use this?

This plugin solves a few specific issues common in consolidated Hudson/Jenkins Continuous Integration (CI) and Continuous Delivery (CD) jobs for projects following the gitflow workflow.

 1. Ensure the developers are following the (git branching) project version rules, and fail the build if they are not.
 2. Enable the maven-deploy-plugin to target a snapshots, test-releases, and releases repository.
 3. _Copy_ (rather than rebuild) the tested artifacts from the test-releases repository to the release repository, without doing a full project rebuild from the master or support branches.
 4. Set arbitrary project properties based upon the type of GIT branch being built. 
 5. Reliably tag deploy builds from the master and support branches
 6. Enable split 'deploy' vs. 'deliver' maven CI job configuration, without rebuilding artifacts for the 'deliver' phase.
 7. Allow for deployment of long-running feature branches to repositories without having to mangle the version in the pom.xml.
 
In addition to supporting these goals for the project, this plugin does it in a manner that tries to be as effortless (yet configurable) as possible.
If you use non-standard gitflow branch names (emer instead of hotfix), this plugin supports that. If you don't want to do version enforcement, this plugin supports that. 
If you want to use scm tagging with a custom tag format, we support that. If you want to use scm tagging **without having to add the <scm> section to your pom.xml or adding arcane -Dproperty arguments to your Maven command**, this plugin supports that. 
If you want to do three-tier deployments (snapshot, stage, production) without 'professional' artifact repository tools, and **without having to define a <distributionManagement> section to your pom.xml**, yep, this plugin supports that too.

All of the solutions to these issues are implemented independently in different plugin goals, so you can pick and choose what parts you'd like to leverage.
 
# I want all of that. (Usage)

 1. Make sure you have a your Project SCM configured for your git repository, or that your build server sets environment variables for git branches and git URLs.
    Out of the box, the plugin will try to resolve the git branch based upon the SCM definition on your maven project, or fall back to the environment variables set by Jenkins and Hudson.
 2. Configure the plugin goals and add the build extension to your Maven project. Here's an example that will get you going quickly with all the features...

```
<project>
    ...
    <scm>
        <developerConnection>scm:git:ssh://git@server/project/path.git</developerConnection>
    </scm>
    ...
    <!-- Configure a local nexus mirror that won't act as a mirror for repos used as gitflow deployment targets. 
         This can go in a default active profile in your ~/.m2/settings.xml
    -->
    <mirrors>
        <mirror>
            <mirrorOf>*,!localnexus-releases,!localnexus-stage,!localnexus-snapshots</mirrorOf>
            <url>https://localnexus/nexus/content/groups/public</url>
        </mirror>
    </mirrors>
    ...
    <!-- Configure a set of repositories for publishing artifacts. Even if you share the same server id credentials,
         It's a good idea to give these discrete ids. Otherwise if you use the 'update-stage-dependencies' you may get 
         some strange behavior!
         
         This can go in a default active profile in your ~/.m2/settings.xml
    -->
    <repositories>
        <repository>
            <id>localnexus-releases</id>
            <url>https://localnexus/nexus/content/repositories/releases</url>
            <snapshots><enabled>false</enabled></snapshots>
            <releases><enabled>true</enabled></releases>
        </repository>
        <repository>
            <id>localnexus-stage</id>
            <url>https://localnexus/nexus/content/repositories/test-releases</url>
            <snapshots><enabled>false</enabled></snapshots>
            <releases><enabled>true</enabled></releases>
        </repository>
        <repository>
            <id>localnexus-snapshots</id>
            <url>http://localnexus/nexus/content/repositories/snapshots</url>
            <snapshots><enabled>true</enabled></snapshots>
            <releases><enabled>false</enabled></releases>
        </repository>
        <repository>
            <id>central</id>
            <url>http://central</url>
            <snapshots><enabled>true</enabled></snapshots>
            <releases><enabled>true</enabled></releases>
        </repository>
    </repositories>    
    ...
    <build>
        <plugins>
            <plugin>
                <groupId>com.e-gineering</groupId>
                <artifactId>gitflow-helper-maven-plugin</artifactId>
                <version>${gitflow.helper.plugin.version}</version>
                <extensions>true</extensions>
                <configuration>
                    <!-- Tell the plugins what repositories to use (by id) -->
                    <releaseDeploymentRepository>localnexus-releases</releaseDeploymentRepository>
                    <stageDeploymentRepository>localnexus-stage</stageDeploymentRepository>
                    <snapshotDeploymentRepository>localnexus-snapshots</snapshotDeploymentRepository>
                    <!-- Allow branches starting with feature/poc to be published as automagically versioned branch-name-SNAPSHOT artifacts -->
                    <otherDeployBranchPattern>(origin/)?feature/poc/.*</otherDeployBranchPattern>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>enforce-versions</goal>
                            <goal>set-properties</goal>
                            <goal>retarget-deploy</goal>
                            <goal>update-stage-dependencies</goal>
                            <goal>tag-master</goal>
                            <goal>promote-master</goal>
                        </goals>
                        <configuration>
                            <masterBranchPropertyFile>foo/bar/prod.props</masterBranchPropertyFile>
                            <supportBranchPropertyFile>foo/bar/support.props</supportBranchPropertyFile>
                            <hotfixBranchPropertyFile>foo/bar/emer.props</hotfixBranchPropertyFile>
                            <releaseBranchPropertyFile>foo/bar/test.props</releaseBranchPropertyFile>
                            <developmentBranchPropertyFile>foo/bar/dev.props</developmentBranchPropertyFile>
                            <otherBranchPropertyFile>foo/bar/ci.props</otherBranchPropertyFile>
                            <undefinedBranchPropertyFile>foo/bar/local.props</undefinedBranchPropertyFile>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
        ...
    </build>
...
</project>
```

## Goal: `enforce-versions` (Version & Branch Name Assertions)

One common stumbling block for teams adjusting to gitflow with Maven projects is the discipline required to keep Maven version numbers up to date.
In practice, the Maven versions should:
 
 * Be synchronized with release branch and hotfix branch names.
 * Never be -SNAPSHOT in the master, support, release, or hotfix branches. Also, no -SNAPSHOT parent or (plugin) dependencies are allowed.
 * Always be -SNAPSHOT in the develop branch.
 * Be irrelevant if there's no git branch resolvable from your environment or working in a branch which is not deployed to remote repositories.

The `enforce-versions` goal asserts these semantics when it can resolve the `gitBranchExpression`.

The goal accomplishes this by checking the Maven pom.xml version value, and asserting the -SNAPSHOT status, as well as matching the current branch name
against regular expressions, extracting version numbers from the branch names where applicable. If a regex specifies subgroups, the content of the 
last subgroup is asserted to equal the version defined in the pom.xml.

The following properties change the behavior of this goal:

| Property             | Default Value | SNAPSHOT allowed? | Description |
| -------------------- | ------------- | --------------------------- | ----------- |
| gitBranchExpression  | current git branch resolved from SCM or ${env.GIT_BRANCH} | n/a | Maven property expression to resolve in order to determine the current git branch |
| deploySnapshotTypeBranches  | `false` | n/a | When `true`, the POM version should end with the feature branch name and -SNAPSHOT, e.g. `1.0.0-myfeature-SNAPSHOT`. This prevents a feature branch snapshot from "overwriting" a snapshot from the develop branch. |
| enforceNonSnapshots | `true` | n/a | When `true`, enforce the requirement that none of the following may contain a -SNAPSHOT: the POM version, any parent, or any (plugin) dependencies. |
| releaseBranchMatchType  | `equals` | n/a | When `equals`, the POM version should be identical to the branch name for release and hotfix branches (e.g. POM version should be `1.0.0` for branch `release/1.0.0`). When `startsWith`, POM version should start with the name branch (e.g. POM version could be `1.0.1` for branch `release/1.0`. When using the `update-stage-dependencies` mojo, set to `equals`, otherwise set to `startsWith`. |
| masterBranchPattern  | (origin/)?master | No | Regex. When matched, signals the master branch is being built. |
| supportBranchPattern | (origin/)?support/(.*) | No | Regex. When matches, signals a support branch (long term master-equivalent for older release) being built. Last subgroup, if present, must be start of the Maven project version. |
| releaseBranchPattern | (origin/)?release/(.*) | No | Regex. When matched, signals a release branch being built. Last subgroup, if present, must match the Maven project version. |
| hotfixBranchPattern  | (origin/)?hotfix/(.*) | No | Regex. When matched, signals a hotfix branch is being built. Last subgroup, if present, must match the Maven project version. |
| developmentBranchPattern | (origin/)?develop | Yes | Regex. When matched, signals a development branch is being built. Note the lack of a subgroup. |

## Goal: `set-properties` (Dynamically Set Maven Project / System Properties)

Some situations with automated testing (and integration testing in particular) demand changing configuration properties 
based upon the branch type being built. This is a common necessity when configuring automated DB refactorings as part of
a build, or needing to setup / configure datasources for automated tests to run against.

The `set-properties` goal allows for setting project (or system) properties, dynamically based on the detected git
branch being built. Properties can be specified as a Properties collection in plugin configuration, or can be loaded
from a property file during the build. Both property key names and property values will have placeholders resolved.

Multiple executions can be configured, and each execution can target different scopes (system or project), and can load
properties from files with an assigned keyPrefix, letting you name-space properties from execution ids.


## Goal: `retarget-deploy` (Branch Specific Deploy Targets & Staging)

One of the challenges of building a good CI/CD job for Maven environments is the lack of a 'staging' repository baked into Maven.
The maven-release-plugin does introduce a concept of a staging repository, but the imposed workflow from the release plugin is incompatible with CI 
jobs and the gitflow model.

For projects being managed with the gitflow workflow model, release and hotfix branches should be deployed to a stage repository, where artifacts can
be tested and validated prior to being deployed to the release repository.

The `retarget-deploy` goal sets the snapshot and release repository based upon the resolved value of the `gitBranchExpression`. Subsequent 
plugins in the build process (deploy, site-deploy, etc.) will use the repositories set by the `retarget-deploy` goal.

| Property | Default Value | Description | 
| -------- | ------------- | ----------- |
| gitBranchExpression  | current git branch resolved from SCM or ${env.GIT_BRANCH} | Maven property expression to resolve in order to determine the current git branch |
| releaseDeploymentRepository | n/a | The repository to use for releases. (Builds with a GIT_BRANCH matching `masterBranchPattern` or `supportBranchPattern`) |
| stageDeploymentRepository | n/a | The repository to use for staging. (Builds with a GIT_BRANCH matching `releaseBranchPattern` or `hotfixBranchPattern`) | 
| snapshotDeploymentRepository | n/a | The repository to use for snapshots. (Builds matching `developmentBranchPattern`) |
| otherDeployBranchPattern | n/a | Regex. When matched, the branch name is normalized and any artifacts produced by the build will include the normalized branch name and -SNAPSHOT. Deployment will target the snapshot repository |

**The repository properties should follow the following format**, `id::layout::url::uniqueVersion`.

When using this plugin, the `<distributionManagement>` repository definitions should be removed from your pom.xml 
This block, is replaced by defining 'normal' repositories which are then referenced by the `<id>` and used by the gitflow-helper-maven-plugin to retarget artifact repository deployment and resolution.

        <distributionManagement>
            <snapshotRepository>
                <id>snapshots</id>
                <layout>default</layout>
                <url>https://some.server.path/content/repositories/snapshots</url>
            </snapshotRepository>
            <repository>
                <id>releases</id>
                <layout>default</layout>
                <url>https://some.server.path/content/repositories/releases</url>
            </repository>
        </distributionManagement>
        
Keep in mind repositories can be defined in a user settings.xml as part of your development profiles to keep from repeating yourself in project files.
Below is an example configuration for the gitflow-helper-maven-plugin.

    <project...>
    ...
    <repositories>
        <repository>
            <id>snapshots</id>
            <url>https://some.server.path/content/repositories/snapshots</url>
            <snapshots><enabled>true</enabled></snapshots>
            <releases><enabled>false</enabled></releases>
        </repository>
        <repository>
            <id>test-releases</id>
            <url>https://some.server.path/content/repositories/test-releases</url>
            <snapshots><enabled>false</enabled></snapshots>
            <releases><enabled>true</enabled></releases>
        </repository>
        <repository>
            <id>releases</id>
            <url>https://some.server.path/content/repositories/releases</url>
            <snapshots><enabled>false</enabled></snapshots>
            <releases><enabled>true</enabled></releases>
        </repository>
    </repositories>
    ...
    <build>
        <plugins>
            <plugin>
                <groupId>com.e-gineering</groupId>
                <artifactId>gitflow-helper-maven-plugin</artifactId>
                <version>${gitflow.helper.plugin.version}</version>
                <configuration>
                    <releaseDeploymentRepository>releases</releaseDeploymentRepository>
                    <stageDeploymentRepository>stage</stageDeploymentRepository>
                    <snapshotDeploymentRepository>snapshots</snapshotDeploymentRepository>
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

### Deploying non-release (OTHER) type branches as -SNAPSHOT releases.

In addition to setting up repository targets for release branches, the `retarget-depoy` branch can deploy other branches
matching the `otherDeployBranchPattern` as -SNAPSHOT artifacts which include the branch name as build metadata.
By default this is loosely based on the [semVer](https://semver.org) semantic version scheme, in that the plugin will 
reversion any artifacts to be produced with `+feature-branch-name-normalized-SNAPSHOT` where any characters not in 
`[0-9A-Za-z-.]` will be replaced with `-`. In cases where the `+` delimiter is problematic, you can override that default
by specifying `<otherBranchVersionDelimiter>` in your configuration block.

Using this feature, artifact versions for feature branches will _always_ be -SNAPSHOT, and will _always_ target the 
Snapshots repository. The intent for this configuration setting is to provide a way for long-running branches (matching 
a naming convention you define) can be published to a SNAPSHOT repo for use by other projects, and to prevent feature 
branches forked from release branches from mangling the test release in an artifact repository.

## Goal: `update-stage-dependencies` (Force update of dependency staged Releases)

The maven `-U` command line switch does a fine job of updating SNAPSHOT versions from snapshot repositories, there is no
built-in way to force maven to re-resolve non-snapshot release versions. This goal addresses that shortcoming in a fairly
straight-forward manner. Any release version dependency of the project which was provided to the local repository by a
remote repository with the same ID as the `<stageDeploymentRepository>`, will be purged from the local repository and
re-resolved (so you get the latest version from either the stage repository, or your release repository).

It is **very important** if you're using this goal, that the **`stageDeploymentReposity` have a unique repository/server id**.
If you use the same ID for release, snapshot, and stage, every time you exeucte this goal, every release version
dependency will be purged and re-resolved.

If you have a local build / install of a release version, this goal will currently not update that package, by design.
You will need to manually remove your local build (or have a newer version resolve from a remote) before this goal will
purge it.

# Goal: `tag-master` ("Automagic" Tagging for Master Branch Releases)

In a gitflow environment, a commit to a master branch should trigger a job to build on the master branch, which would result in the release being tagged if successful.
 
The `tag-master` goal invokes the SCM manager to tag the source repository when `gitBranchExpression` resolves to a value matching the `masterBranchPattern` or
`supportBranchPattern` regular expressions. To determine the SCM URL to use, the plugin looks for a `developerConnection` or `connection` information in an SCM block
 and if not found the `gitURLExpression` is evaluated at run-time. 
The default expression, `${env.GIT_URL}`, is one that is commonly provided by Jenkins & Hudson. 

The following properties can be configured for this goal:

| Property             | Default Value | Description |
| -------------------- | ------------- | ----------- |
| gitBranchExpression  | current git branch resolved from SCM or ${env.GIT_BRANCH} | Maven property expression to resolve in order to determine the current git branch |
| gitURLExpression     | current git branch resolved from SCM or ${env.GIT_URL} | Maven property expression to resolve for the GIT URL connection to use. |
| masterBranchPattern  | (origin/)?master | Regex. When matched against the resolved value of `gitBranchExpression` this plugin tags the SCM using the `gitURLExpression` to resolve the git URL to use. |
| supportBranchPattern | (origin/)?support/(.*) | Regex. When matches against the resolved value of `gitBranchExpression` this plugin tags the SCM using the `gitURLExpression` to resolve the git URL to use. | 
| tag                  | ${project.version} | An expression to use for the SCM tag. |


## Goal: `promote-master` and the Build Extension. (Copy Staged Artifacts to Releases)

With gitflow, a new version of a product is prepared in the `release/.*` and `hotfix/.*` branches of the project.
These artifacts are put through their paces and validated before the merge back into the master branch or a support branch.

In a traditional CI approach, the merge to master triggers a build, which gets deployed to a releases repository, and perhaps deployed to an execution 
environment. This approach has the consequence of deployed artifacts in the release repository having never been tested in a stage or test environment.
Sure, you've tested the branch, but the actual artifact from the stage repository is what you *really* want to have deployed to the release repository.

If stage artifacts are copied into the releases repository when a master (or support branch) commit occurs (ex: the merge from release/2.3.4.5 into master) then the 
artifacts will have the same SHA and MD5 hash, and you'd have full trace-ability for the lifecycle of the artifacts. You'd also have the added benefit 
of achieving the ideal situation for gitflow deployment, where releases originate from the branches created for them, and code is **never deployed  
directly from master**. Rather, master is really only used for tracking releases and branching to support production issues.

To accomplish this the `promote-master` goal and a Maven build extension work together.

With the build extension added to your project, any build where the `gitBranchExpression` matches the `masterBranchPattern` or `supportBranchPattern` will have it's
build lifecycle (plugins, goals, etc) altered. Any plugin other than the gitflow-helper-maven-plugin, the maven-deploy-plugin, or plugins with goals
 explicitly referenced on the command line will be ignored (removed from the project reactor). 
This allows us to enforce the ideal that code should never be built in the master branch.

The `promote-master` goal executes when the `gitBranchExpression` resolves to a value matching the `masterBranchPattern` or `supportBranchPattern` regular expression.
 
This goal resolves (and downloads) the artifacts matching the current `${project.version}` from the stage repository, then attaches them to the 
current project in the Maven build. This lets later plugins in the lifecycle (like the deploy plugin, which the extension won't remove) make use of 
artifacts provided from the stage repository when it uploads to the releases repository. Effectively, this makes a build in master (or support) copy the artifacts from 
the stage repository to the releases repository.


## Goal: `attach-deployed` (Deliver already Deployed artifacts)

In some cases it is not advantageous to have instantaneous delivery of deployed artifacts into execution environments.
The Maven lifecycle has no concept of this. The manner in which traditional 'deploy' (really, delivery) plugins deliver 
new artifacts to execution environments overlaps with the 'deploy' to a binary artifact repository. The overlap of these
two operations into a single Maven lifecycle phase represents a conflict of interest when attempting to deliver already
deployed artifacts without re-building the artifacts at the time of delivery. Within the context of auditing deployed 
artifact provenance, this is a 'bad thing'.

The `attach-deployed` goal will execute a clean, resolve previously built artifacts appropriate for the git branch 
being built, attach the artifacts to the project, and place them in the `/target` directory as part of the Maven
package phase.

The following table describes the git branch expression -> repository used for resolving prebuilt artifact mapping.
 
| Git Branch Expression | Source Repository for re-attachment |
| --------------------- | ---------- |
| masterBranchPattern   | release    |
| supportBranchPattern  | release    |
| releaseBranchPattern  | stage      |
| hotfixBranchPattern   | stage      |
| developmentBranchPattern | snapshots | 
| otherBranchesToDeploy | snapshots | 
| All Others            | local      |
 
As an example, assume you have two CI jobs. 

 * One which builds and deploys (to an artifact repository) the project for each commit.
 * Another which is manually triggered, takes a branch as a user-input parameter, and delivers that branch to the proper
   execution environment.
   
 The first job would likely run the following maven goals:
    `mvn clean deploy`
    
 The second job could then run these maven goals:
    `mvn gitflow-helper:attach-deploy jboss-as:deploy-only`
    
The effect would be that the first job builds, and pushes binaries to the proper artifact repository.
The second job would have a clean workspace, with the proper version of the project defined by the pom.xml in the branch
it's building. The attach-deploy will 'clean' the maven project, then download the binary artifacts from the repository
that the first build deployed into. Once they're attached to the project, the `jboss-as:deploy-only` goal will deliver
the artifacts built by the first job into a jboss application server.

# Additional Notes
## How Git branch name resolution works
1. If the `<scm>` sections of the pom points to a git repository,  `git symbolic-ref HEAD` to is used to check the local branch name.
2. If the `symbolic-ref` fails then it's likely due to a detached HEAD.
   This is typical of CI servers like Jenkins, where the commit hash that was just pushed is pulled.
   This can also be done as a consequene of attempting to rebuild from a tag, without branching, or in some 
   workflows where code reviews are done without branches.   
   
   In the case of a detached HEAD the plugin will:
    * Resolve the HEAD to a commit using `git rev-parse HEAD`.
    * `git show-ref` to resolve which (local/remote) branches point to the commit.
    * If the detached HEAD commit resolves to a single branch type, it uses that branch name.
3. If the first two methods fail, the plugin attempts to resolve `${env.GIT_BRANCH}`.

## To Debug the plugin (replicating a test-case but without being run from jUnit)
You can 'bootstrap' the plugin into your local repository and get the test project stubbed by running:
`mvn -Dmaven.test.skip=true install` 

Then, change directories:
`cd target/test-classes/project-stub`

From there, you'll need to supply the required environment variables or commandline arguments to `mvnDebug`:
```
export GIT_BRANCH=origin/feature/mybranch-foo-bar
mvnDebug -Dstub.project.version=5.0.0-SNAPSHOT -DotherBranchDeploy=semver -DallowGitflowPluginSnapshot=true  deploy
```
You can then connect a remote debugger and step through the plugin code.

## Building with IntelliJ IDEA notes
### To Debug Test Code:
Configure the Maven commandline to include
`-DforkCount=0` 

### To inspect code-coverage results from Integration Tests:
* Select the **Analyze** -> **Show Coverage Data** menu.
* In the dialog that appears, click the **+** in the upper left corner to `Add (Insert)`, and browse to `target/jacoco.exec`.
* Selecting that file will show coverage data in the code editor.
