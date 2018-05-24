package com.e_gineering.maven.gitflowhelper;

/**
 * Data holder defining the resolved branch name and type of branch.
 */
public class GitBranchInfo {

    private final String name;

    private final GitBranchType type;

    private final String pattern;

    /**
     * Constructs a GitBranchInfo object for the given name and type.
     *
     * @param name must not be null. (empty string OK)
     * @param type must not be null. (use OTHER)
     * @param pattern may be null
     * @throws IllegalArgumentException if name or type are null
     */
    GitBranchInfo(final String name, final GitBranchType type, final String pattern) {
        if(name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        this.name = name;
        this.type = type;
        this.pattern = pattern;
    }

    public String getName() {
        return name;
    }

    public GitBranchType getType() {
        return type;
    }

    public boolean isSnapshot() {
        return GitBranchType.SNAPSHOT_TYPES.contains(type);
    }

    public boolean isVersioned() {
        return GitBranchType.VERSIONED_TYPES.contains(type);
    }

    public String getPattern() {
        return pattern;
    }

    @Override
    public String toString() {
        return "GitBranchInfo: [" +
                "name='" + name + '\'' +
                ", type=" + type + '\'' +
                ", pattern=" + pattern +
                ']';
    }
}
