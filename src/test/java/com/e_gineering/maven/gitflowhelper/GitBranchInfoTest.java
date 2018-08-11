package com.e_gineering.maven.gitflowhelper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

@RunWith(BlockJUnit4ClassRunner.class)
public class GitBranchInfoTest {

	@Test(expected = IllegalArgumentException.class)
	public void testInitNoNameParams() {
		new GitBranchInfo(null, GitBranchType.MASTER, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInitNoTypeParams() {
		new GitBranchInfo("origin/release", null, null);
	}

}
