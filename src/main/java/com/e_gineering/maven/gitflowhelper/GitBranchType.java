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
    FEATURE_OR_BUGFIX_BRANCH,
    OTHER,
    UNDEFINED;

    static final EnumSet<GitBranchType> VERSIONED_TYPES = EnumSet.of(MASTER, SUPPORT, RELEASE, HOTFIX);
    static final EnumSet<GitBranchType> SNAPSHOT_TYPES = EnumSet.of(DEVELOPMENT, FEATURE_OR_BUGFIX_BRANCH, OTHER);
    static final EnumSet<GitBranchType> UNIQUELY_VERSIONED_TYPES = EnumSet.of(SUPPORT, RELEASE, HOTFIX);
}
