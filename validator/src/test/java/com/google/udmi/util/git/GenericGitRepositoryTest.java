package com.google.udmi.util.git;

import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for the GenericGitRepository class. These tests use a temporary folder to create real
 * git repositories for testing.
 */
public class GenericGitRepositoryTest {

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  private GenericGitRepository gitRepo;
  private File localPath;
  private File remotePath;

  /**
   * Set up by creating a "remote" bare repository locally.
   */
  @Before
  public void setUp() throws IOException, GitAPIException {
    remotePath = tempFolder.newFolder("remote.git");
    try (Git remoteGit = Git.init().setDirectory(remotePath).setBare(true).call()) {
      File tempClonePath = tempFolder.newFolder("tempClone");
      try (Git tempCloneGit = Git.cloneRepository().setURI(remotePath.toURI().toString())
          .setDirectory(tempClonePath).call()) {
        File initialFile = new File(tempClonePath, "initial.txt");
        Files.write(initialFile.toPath(), "initial content".getBytes());
        tempCloneGit.add().addFilepattern("initial.txt").call();
        tempCloneGit.commit().setMessage("Initial commit").call();

        if (!tempCloneGit.getRepository().getBranch().equalsIgnoreCase("main")) {
          tempCloneGit.branchRename().setNewName("main").call();
        }

        tempCloneGit.push().setRemote("origin").setForce(true).call();

        StoredConfig remoteConfig = remoteGit.getRepository().getConfig();
        remoteConfig.setString("core", null, "bare", "true");
        remoteConfig.setString("remote", "origin", "url", remotePath.toURI().toString());
        remoteConfig.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
        remoteConfig.setString("branch", "main", "remote", "origin");
        remoteConfig.setString("branch", "main", "merge", "refs/heads/main");
        remoteConfig.save();
        remoteGit.getRepository().updateRef(Constants.HEAD).link("refs/heads/main");
      }
    }

    localPath = tempFolder.newFolder("local");

    RepositoryConfig config = RepositoryConfig.forRemote(
        remotePath.toURI().toString(),
        localPath.getAbsolutePath()
    );
    gitRepo = new GenericGitRepository(config);
  }

  /**
   * Ensure git resources are released.
   */
  @After
  public void tearDown() throws IOException {
    if (gitRepo != null) {
      gitRepo.close();
    }
    deleteDirectory(localPath);
    deleteDirectory(remotePath);
  }

  @Test
  public void init_createsNewRepository() throws GitAPIException {
    // Arrange
    File newRepoPath = tempFolder.getRoot().toPath().resolve("newInit").toFile();
    RepositoryConfig newConfig = new RepositoryConfig(null, newRepoPath.getAbsolutePath(),
        RepositoryType.LOCAL_REMOTE, null, null);
    GenericGitRepository newGitRepo = new GenericGitRepository(newConfig);

    // Act
    newGitRepo.init();

    // Assert
    assertTrue("A .git directory should exist after init",
        new File(newRepoPath, ".git").exists());
    newGitRepo.close();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void init_withRemoteUrl_throwsException() throws GitAPIException {
    // Arrange: gitRepo is configured with a remote URL in setUp()

    // Act
    gitRepo.init();
  }

  @Test
  public void cloneRepo_successful() throws GitAPIException, IOException {
    // Act
    gitRepo.cloneRepo();

    // Assert
    assertTrue("A .git directory should exist after clone", new File(localPath, ".git").exists());
    assertTrue("Initial file from remote should exist locally",
        new File(localPath, "initial.txt").exists());
    assertEquals("main", gitRepo.getCurrentBranch());
  }

  @Test
  public void cloneRepo_withSpecificBranch() throws GitAPIException, IOException {
    // Arrange: Add a new branch to the remote repo
    File tempClonePath = tempFolder.newFolder("brancher");
    try (Git tempCloneGit = Git.cloneRepository().setURI(remotePath.toURI().toString())
        .setDirectory(tempClonePath).call()) {
      tempCloneGit.checkout().setCreateBranch(true).setName("feature-branch").call();
      File featureFile = new File(tempClonePath, "feature.txt");
      Files.write(featureFile.toPath(), "feature content".getBytes());
      tempCloneGit.add().addFilepattern("feature.txt").call();
      tempCloneGit.commit().setMessage("Feature commit").call();
      tempCloneGit.push().setRemote("origin").add("feature-branch").call();
    }

    // Act
    gitRepo.cloneRepo("feature-branch");

    // Assert
    assertTrue("A .git directory should exist after clone", new File(localPath, ".git").exists());
    assertTrue("Feature file should exist locally", new File(localPath, "feature.txt").exists());
    assertTrue("Initial file should exist on this branch",
        new File(localPath, "initial.txt").exists());
    assertEquals("feature-branch", gitRepo.getCurrentBranch());
  }

  @Test(expected = IOException.class)
  public void cloneRepo_intoNonEmptyDirectory_throwsException()
      throws GitAPIException, IOException {
    // Arrange
    File dummyFile = new File(localPath, "dummy.txt");
    dummyFile.createNewFile();

    // Act
    gitRepo.cloneRepo();
  }

  @Test
  public void addAndCommit_successful() throws GitAPIException, IOException {
    // Arrange
    gitRepo.cloneRepo();
    File newFile = new File(localPath, "test.txt");
    Files.write(newFile.toPath(), "hello world".getBytes());

    // Act
    gitRepo.add("test.txt");
    RevCommit commit = gitRepo.commit("Add test.txt");

    // Assert
    assertNotNull(commit);
    assertTrue("Working tree should be clean after commit", gitRepo.isWorkingTreeClean());
    assertEquals("Add test.txt", commit.getShortMessage());
  }

  @Test
  public void commit_withAuthor_successful() throws GitAPIException, IOException {
    // Arrange
    gitRepo.cloneRepo();
    File newFile = new File(localPath, "author_test.txt");
    Files.write(newFile.toPath(), "some content".getBytes());
    gitRepo.add(".");

    String authorName = "Test Author";
    String authorEmail = "test@author.com";

    // Act
    RevCommit commit = gitRepo.commit(authorName, authorEmail, "Authored commit");

    // Assert
    assertEquals(authorName, commit.getAuthorIdent().getName());
    assertEquals(authorEmail, commit.getAuthorIdent().getEmailAddress());
  }

  @Test
  public void pullAndPush_successful() throws GitAPIException, IOException {
    // Arrange: Clone the repo
    gitRepo.cloneRepo();

    // Act 1: Make a local change and push it
    Path localFilePath = Paths.get(localPath.getAbsolutePath(), "local_change.txt");
    Files.write(localFilePath, "data from local".getBytes());
    gitRepo.add(".");
    gitRepo.commit("Local commit");
    gitRepo.push();

    // Assert 1: The remote should have the change - verify by cloning to another dir
    File verificationClonePath = tempFolder.newFolder("verification");
    try (Git verificationGit = Git.cloneRepository().setURI(remotePath.toURI().toString())
        .setDirectory(verificationClonePath).call()) {
      assertTrue("Pushed file should exist in a fresh clone",
          new File(verificationClonePath, "local_change.txt").exists());
    }

    // Act 2: Make a "remote" change and pull it
    try (Git remoteGit = Git.open(verificationClonePath)) {
      Path remoteFilePath = Paths.get(verificationClonePath.getAbsolutePath(), "remote_change.txt");
      Files.write(remoteFilePath, "data from remote".getBytes());
      remoteGit.add().addFilepattern(".").call();
      remoteGit.commit().setMessage("Remote commit").call();
      remoteGit.push().call();
    }

    // Act 3: Pull the change into the original local repo
    gitRepo.pull();

    // Assert 3: The local repo should now have the remote change
    assertTrue("Pulled file should exist in the local repo",
        new File(localPath, "remote_change.txt").exists());
  }

  @Test
  public void branchOperations_successful() throws GitAPIException, IOException {
    // Arrange
    gitRepo.cloneRepo();
    String newBranch = "feature/new-work";

    // Act & Assert: Create and checkout branch
    gitRepo.createAndCheckoutBranch(newBranch);
    assertEquals(newBranch, gitRepo.getCurrentBranch());

    // Act & Assert: List local branches
    List<String> localBranches = gitRepo.listLocalBranches();
    assertTrue(localBranches.contains(Constants.R_HEADS + "main"));
    assertTrue(localBranches.contains(Constants.R_HEADS + newBranch));

    // Act & Assert: Checkout main
    gitRepo.checkoutBranch("main");
    assertEquals("main", gitRepo.getCurrentBranch());

    // Act & Assert: Delete local branch
    gitRepo.deleteLocalBranch(newBranch, true);
    localBranches = gitRepo.listLocalBranches();
    assertFalse(localBranches.contains(Constants.R_HEADS + newBranch));
  }

  @Test
  public void getStatus_reportsChanges() throws GitAPIException, IOException {
    // Arrange
    gitRepo.cloneRepo();

    // Act: Create an untracked file
    File untrackedFile = new File(localPath, "untracked.txt");
    untrackedFile.createNewFile();

    // Assert: Check for untracked file
    Status status = gitRepo.getStatus();
    assertTrue(status.getUntracked().contains("untracked.txt"));
    assertFalse(status.isClean());

    // Act: Add the file
    gitRepo.add("untracked.txt");

    // Assert: Check for added file
    status = gitRepo.getStatus();
    assertTrue(status.getAdded().contains("untracked.txt"));
    assertFalse(status.getUntracked().contains("untracked.txt"));

    // Act: Commit the file
    gitRepo.commit("Add untracked file");

    // Assert: Working tree is clean
    status = gitRepo.getStatus();
    assertTrue(status.isClean());
  }

  @Test
  public void listRemoteBranches_successful() throws GitAPIException, IOException {
    // Arrange
    gitRepo.cloneRepo();

    // Act
    List<String> remoteBranches = gitRepo.listRemoteBranches();

    // Assert
    assertTrue(remoteBranches.contains("refs/remotes/origin/main"));
    assertEquals(1, remoteBranches.size());
  }

  @Test
  public void getCommitLog_successful() throws GitAPIException, IOException {
    // Arrange
    gitRepo.cloneRepo();
    Files.write(localPath.toPath().resolve("new_file.txt"), "content".getBytes());
    gitRepo.add("new_file.txt");
    gitRepo.commit("Commit 2");
    Files.write(localPath.toPath().resolve("another_file.txt"), "content".getBytes());
    gitRepo.add("another_file.txt");
    gitRepo.commit("Commit 3");

    // Act
    List<RevCommit> commits = gitRepo.getCommitLog(5);

    // Assert
    // There was 1 initial commit + 2 new ones
    assertEquals(3, commits.size());
    assertEquals("Commit 3", commits.get(0).getShortMessage());
    assertEquals("Commit 2", commits.get(1).getShortMessage());
    assertEquals("Initial commit", commits.get(2).getShortMessage());
  }

  @Test(expected = IllegalStateException.class)
  public void getGit_whenNotInitialized_throwsException() throws GitAPIException {
    // Arrange
    GenericGitRepository uninitializedRepo = new GenericGitRepository(
        new RepositoryConfig(null, "/tmp/nonexistent", RepositoryType.LOCAL_REMOTE, null, null));

    // Act & Assert
    uninitializedRepo.isWorkingTreeClean();
  }

  @Test
  public void close_releasesResources() throws GitAPIException, IOException {
    // Arrange
    gitRepo.cloneRepo();
    assertNotNull(gitRepo.getGit());

    // Act
    gitRepo.close();

    // Assert
    try {
      gitRepo.getCurrentBranch();
      fail("Should have thrown IllegalStateException because git object is null after close()");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("Git repository is not initialized or opened"));
    }
  }
}