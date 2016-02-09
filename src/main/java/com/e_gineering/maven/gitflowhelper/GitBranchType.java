package com.e_gineering.maven.gitflowhelper;

import java.util.EnumSet;

/**
 * Enum for different types of Git Branches...
 */
public enum GitBranchType {
    MASTER,
    RELEASE,
    HOTFIX,
    DEVELOPMENT,
    OTHER,
    UNDEFINED;

    static EnumSet<GitBranchType> VERSIONED_TYPES = EnumSet.of(GitBranchType.MASTER, GitBranchType.RELEASE, GitBranchType.HOTFIX);
}
