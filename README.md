# gitflow-helper-maven-plugin [![Build Status](https://travis-ci.org/egineering-llc/gitflow-helper-maven-plugin.svg?branch=master)](https://travis-ci.org/egineering-llc/gitflow-helper--maven-plugin)

A maven plugin intended to be used in conjunction with Jenkins / Hudson builds for :

 * Enforcing [gitflow](http://nvie.com/posts/a-successful-git-branching-model/) version heuristics in [Maven](http://maven.apache.org/) projects.
 * Setting a [maven-deploy-plugin](https://maven.apache.org/plugins/maven-deploy-plugin/) repository based upon the current git branch.

# Why would I want to use this?

This plugin solves two specific issues common in a consolidated Hudson/Jenkins Continuous Integration (CI) and Continuous Delivery (CD) job.

 1. Ensure the developers are following the (git branching) project version rules, and fail the build they're not.
 2. Enable the maven-deploy-plugin to target a snapshots, test-releases, and releases repository.
  
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
