package com.google.udmi.util.git;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for interactions with a generic Git repository.
 */
public class GenericGitRepository implements GitRepositoryInterface {

  protected static Logger LOGGER = LoggerFactory.getLogger(GenericGitRepository.class);
  protected final RepositoryConfig config;
  protected Git git;

  public GenericGitRepository(RepositoryConfig config) {
    this.config = config;
  }

  protected File prepareLocalDirectory() {
    File localDir = new File(config.localPath());
    if (!localDir.exists()) {
      if (!localDir.mkdirs()) {
        throw new RuntimeException("Could not create local directory: " + config.localPath());
      }
    }
    return localDir;
  }

  protected void open() throws IOException {
    if (this.git != null) {
      this.git.close();
    }
    File localDir = new File(config.localPath());
    if (!new File(localDir, ".git").exists()) {
      throw new IOException(
          "Not a git repository (or .git directory missing): " + localDir.getAbsolutePath());
    }
    this.git = Git.open(localDir);
    LOGGER.info("Opened existing Git repository at {}", config.localPath());
  }

  @Override
  public void init() throws GitAPIException {
    if (config.remoteUrl() != null && !config.remoteUrl().isEmpty()) {
      throw new UnsupportedOperationException(
          "Init is for new local repositories; use clone for remote ones.");
    }
    File localDir = prepareLocalDirectory();
    if (new File(localDir, ".git").exists()) {
      LOGGER.info("Repository already initialized at {}. Attempting to open.", config.localPath());
      try {
        open();
      } catch (IOException e) {
        throw new GitAPIException(
            "Failed to open already initialized repository: " + e.getMessage(), e) {
        };
      }
      return;
    }
    this.git = Git.init().setDirectory(localDir).call();
    LOGGER.info("Initialized empty Git repository in {}", config.localPath());
  }

  public String getDirectory() {
    return config.localPath();
  }

  @Override
  public void cloneRepo() throws GitAPIException, IOException {
    if (config.remoteUrl() == null || config.remoteUrl().isEmpty()) {
      throw new IllegalStateException("Remote URL must be configured for cloning.");
    }
    File localDir = prepareLocalDirectory();
    if (localDir.exists() && localDir.isDirectory() && !new File(localDir, ".git").exists()) {
      if (localDir.list() != null && Objects.requireNonNull(localDir.list()).length > 0) {
        throw new IOException("Local path " + localDir.getAbsolutePath()
            + " exists and is not empty and not a git repo. "
            + "Choose another directory or clear its contents.");
      }
    } else if (new File(localDir, ".git").exists()) {
      LOGGER.info("Repository already exists at {}. Attempting to open.", config.localPath());
      open();
      return;
    }

    LOGGER.info("Cloning {} to {}", config.remoteUrl(), config.localPath());
    CloneCommand cloneCmd = Git.cloneRepository()
        .setURI(config.remoteUrl())
        .setDirectory(localDir);
    if (config.credentialsProvider() != null) {
      cloneCmd.setCredentialsProvider(config.credentialsProvider());
    }
    this.git = cloneCmd.call();
    LOGGER.info("Repository cloned successfully.");
  }

  @Override
  public void cloneRepo(String defaultBranch) throws GitAPIException, IOException {
    if (config.remoteUrl() == null || config.remoteUrl().isEmpty()) {
      throw new IllegalStateException("Remote URL must be configured for cloning.");
    }
    File localDir = prepareLocalDirectory();
    if (localDir.exists() && localDir.isDirectory() && !new File(localDir, ".git").exists()) {
      if (localDir.list() != null && Objects.requireNonNull(localDir.list()).length > 0) {
        throw new IOException("Local path " + localDir.getAbsolutePath()
            + " exists and is not empty and not a git repo. "
            + "Choose another directory or clear its contents.");
      }
    } else if (new File(localDir, ".git").exists()) {
      LOGGER.info("Repository already exists at {}. Attempting to open.", config.localPath());
      open();
      return;
    }

    LOGGER.info("Cloning {} to {}", config.remoteUrl(), config.localPath());
    CloneCommand cloneCmd = Git.cloneRepository()
        .setURI(config.remoteUrl())
        .setDirectory(localDir)
        .setBranch(Constants.R_HEADS + defaultBranch);
    if (config.credentialsProvider() != null) {
      cloneCmd.setCredentialsProvider(config.credentialsProvider());
    }
    this.git = cloneCmd.call();
    LOGGER.info("Repository cloned successfully.");
  }

  protected Git getGit() {
    if (git == null) {
      throw new IllegalStateException(
          "Git repository is not initialized or opened. "
              + "Call init(), cloneRepo(), or ensure the constructor opened an existing repo.");
    }
    return git;
  }

  public boolean isWorkingTreeClean() throws GitAPIException {
    return getGit().status().call().isClean();
  }

  @Override
  public void add(String filePattern) throws GitAPIException, IOException {
    getGit().add().addFilepattern(filePattern).call();
  }

  /**
   * Stages the removal of a file from the repository. This is equivalent to `git rm`.
   *
   * @param filePattern The file pattern to remove from the index.
   * @throws GitAPIException if there is an error executing the Git command.
   */
  @Override
  public void remove(String filePattern) throws GitAPIException {
    getGit().rm().addFilepattern(filePattern).call();
  }

  @Override
  public RevCommit commit(String message) throws GitAPIException {
    return getGit().commit().setMessage(message).call();
  }

  @Override
  public RevCommit commit(String authorName, String authorEmail, String message)
      throws GitAPIException {
    PersonIdent author = new PersonIdent(authorName, authorEmail);
    return getGit().commit().setAuthor(author).setCommitter(author).setMessage(message).call();
  }

  @Override
  public FetchResult fetch() throws GitAPIException {
    FetchCommand fetchCmd = getGit().fetch();
    if (config.credentialsProvider() != null) {
      fetchCmd.setCredentialsProvider(config.credentialsProvider());
    }
    return fetchCmd.call();
  }

  @Override
  public void pull() throws GitAPIException {
    PullCommand pullCmd = getGit().pull();
    if (config.credentialsProvider() != null) {
      pullCmd.setCredentialsProvider(config.credentialsProvider());
    }
    PullResult result = pullCmd.call();
    if (!result.isSuccessful()) {
      String fetchResultMessage = result.getFetchResult() != null
          ? result.getFetchResult().getMessages() : "No fetch result messages.";
      String mergeStatus = result.getMergeResult() != null
          ? result.getMergeResult().getMergeStatus().toString() : "No merge result.";

      throw new GitAPIException(String.format("Pull failed. Fetch result: %s. Merge status: %s",
          fetchResultMessage, mergeStatus)) {
      };
    }
    LOGGER.info("Pull successful. Fetched from: {}", result.getFetchResult().getURI());
  }

  @Override
  public Iterable<PushResult> push() throws GitAPIException {
    PushCommand pushCmd = getGit().push();
    if (config.credentialsProvider() != null) {
      pushCmd.setCredentialsProvider(config.credentialsProvider());
    }
    Iterable<PushResult> results = pushCmd.call();
    for (PushResult result : results) {
      for (RemoteRefUpdate update : result.getRemoteUpdates()) {
        if (update.getStatus() != RemoteRefUpdate.Status.OK
            && update.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE) {
          throw new GitAPIException(
              "Push failed for " + update.getRemoteName() + ": " + update.getStatus() + " - "
                  + update.getMessage()) {
          };
        }
        LOGGER.info("Push successful for {} : {}", update.getRemoteName(), update.getStatus());
      }
    }
    return results;
  }

  @Override
  public String getCurrentBranch() throws IOException {
    return getGit().getRepository().getBranch();
  }

  @Override
  public List<String> listLocalBranches() throws GitAPIException {
    return getGit().branchList().call().stream()
        .map(Ref::getName)
        .collect(Collectors.toList());
  }

  @Override
  public List<String> listRemoteBranches() throws GitAPIException {
    return getGit().branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call().stream()
        .map(Ref::getName)
        .collect(Collectors.toList());
  }

  @Override
  public List<String> listTags() throws GitAPIException {
    return getGit().tagList().call().stream()
        .map(Ref::getName)
        .map(name -> name.startsWith(Constants.R_TAGS) ? name.substring(Constants.R_TAGS.length())
            : name)
        .collect(Collectors.toList());
  }

  @Override
  public Status getStatus() throws GitAPIException {
    return getGit().status().call();
  }

  @Override
  public List<RevCommit> getCommitLog(int maxCount) throws GitAPIException {
    Iterable<RevCommit> log = getGit().log().setMaxCount(maxCount).call();
    return StreamSupport.stream(log.spliterator(), false).collect(Collectors.toList());
  }

  @Override
  public void checkoutBranch(String branchName) throws GitAPIException {
    getGit().checkout().setName(branchName).call();
  }

  @Override
  public void checkoutRemoteBranch(String branchName) throws IOException, GitAPIException {
    String remoteBranchName = "origin/" + branchName;
    if (getGit().getRepository().findRef(remoteBranchName) != null) {
      getGit().checkout()
          .setCreateBranch(true)
          .setName(branchName)
          .setUpstreamMode(SetupUpstreamMode.TRACK)
          .setStartPoint(remoteBranchName)
          .call();
    } else {
      throw new GitAPIException(
          "Branch not found: No remote-tracking branch named '" + branchName + "' exists.") {
      };

    }
  }

  @Override
  public void createBranch(String branchName) throws GitAPIException {
    getGit().branchCreate().setName(branchName).call();
  }

  @Override
  public void createAndCheckoutBranch(String branchName) throws GitAPIException {
    getGit().checkout().setCreateBranch(true).setName(branchName).call();
  }

  @Override
  public void deleteLocalBranch(String branchName, boolean force)
      throws GitAPIException, IOException {
    if (getCurrentBranch().equals(branchName)) {
      String mainBranch = Constants.R_HEADS + "main";
      String masterBranch = Constants.R_HEADS + "master";
      List<String> localBranches = listLocalBranches();

      if (localBranches.contains(mainBranch)) {
        checkoutBranch("main");
      } else if (localBranches.contains(masterBranch)) {
        checkoutBranch("master");
      } else if (!localBranches.isEmpty() && !localBranches.get(0).endsWith(branchName)) {
        checkoutBranch(localBranches.get(0).substring(Constants.R_HEADS.length()));
      } else {
        throw new IllegalStateException("Cannot delete current branch '" + branchName
            + "' and no other branch (like main/master) is available to checkout.");
      }
    }
    getGit().branchDelete().setBranchNames(branchName).setForce(force).call();
  }

  /**
   * Gets the commit hash (SHA-1) for the tip of a given branch.
   *
   * @param branchName The name of the branch (e.g., "main", "feature/new-login").
   * @return The full 40-character commit hash as a String.
   * @throws IOException if the branch cannot be found or an I/O error occurs.
   */
  public String getCommitHashForBranch(String branchName) throws IOException {
    ObjectId branchId = getGit().getRepository().resolve(branchName);

    if (branchId == null) {
      throw new IOException("Could not find a branch named '" + branchName + "'");
    }

    return branchId.getName();
  }

  public String getCommitHashForCurrentBranch() throws IOException {
    return getCommitHashForBranch(getCurrentBranch());
  }

  @Override
  public void close() {
    if (git != null) {
      git.close();
      git = null;
      LOGGER.debug("Git repository at {} closed.", config.localPath());
    }
  }

}
