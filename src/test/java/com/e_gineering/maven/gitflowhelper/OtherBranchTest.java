package com.e_gineering.maven.gitflowhelper;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

@RunWith(BlockJUnit4ClassRunner.class)
public class OtherBranchTest {

    private final String baseVersion = "1.0.2";
    private final String baseSnapshotVersion = "1.0.2-SNAPSHOT";
    private final String branchName = "feature/other-branch-name";
    private final String expectedSnapshotResult = "1.0.2+feature-other-branch-name-SNAPSHOT";

    private OtherBranchVersionExtension getExtension() {
        OtherBranchVersionExtension extension = new OtherBranchVersionExtension();
        extension.otherBranchVersionDelimiter = "+";
        return extension;
    }

    @Test
    public void assertOtherBranchNameIsPrefixedBeforeSnapshot() {
        OtherBranchVersionExtension extension = getExtension();
        Assert.assertEquals(expectedSnapshotResult, extension.getAsBranchSnapshotVersion(baseSnapshotVersion,branchName));
    }

    @Test
    public void assertOtherBranchNameIsOnlyPrefixedBeforeSnapshotOneTime() {
        OtherBranchVersionExtension extension = getExtension();
        Assert.assertEquals(expectedSnapshotResult, extension.getAsBranchSnapshotVersion(extension.getAsBranchSnapshotVersion(baseSnapshotVersion,branchName),branchName));
    }
}
