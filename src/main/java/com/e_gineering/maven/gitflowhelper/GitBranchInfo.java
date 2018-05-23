package com.e_gineering.maven.gitflowhelper;

public class GitBranchInfo {

    private final String branchName;

    private final GitBranchType branchType;

    public GitBranchInfo(String branchName, GitBranchType branchType) {
        this.branchName = branchName;
        this.branchType = branchType;
    }

    public String getBranchName() {
        return branchName;
    }

    public GitBranchType getBranchType() {
        return branchType;
    }

    @Override
    public String toString() {
        return "Git branch info: [" +
                "branchName='" + branchName + '\'' +
                ", branchType=" + branchType +
                ']';
    }
}
