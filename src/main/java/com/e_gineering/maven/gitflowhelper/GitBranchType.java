package com.e_gineering.maven.gitflowhelper;

import java.util.EnumSet;

/**
 * Enum for different types of Git Branches...
 */
public enum GitBranchType {
    MASTER,
    SUPPORT,
    RELEASE,
    HOTFIX,
    DEVELOPMENT,
    OTHER,
    UNDEFINED;

    static final EnumSet<GitBranchType> VERSIONED_TYPES = EnumSet.of(GitBranchType.MASTER, GitBranchType.SUPPORT, GitBranchType.RELEASE, GitBranchType.HOTFIX);
    static final EnumSet<GitBranchType> SNAPSHOT_TYPES = EnumSet.of(GitBranchType.DEVELOPMENT, GitBranchType.OTHER);
}
