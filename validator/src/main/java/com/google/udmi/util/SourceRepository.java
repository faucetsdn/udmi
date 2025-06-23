package com.google.udmi.util;

import static com.google.udmi.util.git.RepositoryConfig.forRemote;
import static com.google.udmi.util.git.RepositoryConfig.fromGoogleCloudSourceRepoName;
import static org.apache.commons.io.FileUtils.deleteDirectory;

import com.google.udmi.util.git.GenericGitRepository;
import com.google.udmi.util.git.GoogleCloudSourceRepository;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class encapsulates all interactions required by BAMBI and other services
 * with the site model source repositories.
 */
public class SourceRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(SourceRepository.class);
  private final GenericGitRepository repository;

  /**
   * Source repository containing the site model.
   *
   * @param repoName e.g. ZZ-TRI-FECTA
   * @param repoClonePath local path for the repository
   * @param localOriginDir path to a local "origin - used for testing
   * @param gcpProject GCP project id
   * @param udmiNamespace GKE namespace
   */
  public SourceRepository(String repoName, String repoClonePath, String localOriginDir,
      String gcpProject, String udmiNamespace) {
    try {
      if (localOriginDir != null) {
        String remotePath = Paths.get(localOriginDir, repoName).toString();
        LOGGER.info("Using local git origin at {}", remotePath);
        repository = new GenericGitRepository(forRemote(remotePath, repoClonePath));
      } else {
        LOGGER.info("Using Google Cloud Source Repository {} in project {}", repoName, gcpProject);
        repository = new GoogleCloudSourceRepository(
            fromGoogleCloudSourceRepoName(repoName, repoClonePath, gcpProject), udmiNamespace);
      }
    } catch (GeneralSecurityException | IOException e) {
      throw new RuntimeException("Could not initialize source repository", e);
    }
  }

  /**
   * Get local path to the repository.
   *
   * @return path to the directory.
   */
  public String getDirectory() {
    return repository.getDirectory();
  }

  private void cleanUpDirectory(String directory) throws IOException {
    File dirFile = new File(directory);
    if (dirFile.exists()) {
      LOGGER.info("Deleting existing directory {}", directory);
      deleteDirectory(dirFile);
    }
  }

  /**
   * Clone the repository locally.
   *
   * @param branch name of the branch to clone.
   * @return true if operation is successful, false otherwise.
   */
  public boolean clone(String branch) {
    try {
      cleanUpDirectory(repository.getDirectory());
      repository.cloneRepo(branch);
    } catch (IOException | GitAPIException e) {
      LOGGER.error("Unable to clone repository ", e);
      return false;
    }
    return true;
  }

  /**
   * Create a new branch and checkout.
   *
   * @param branch name of the branch
   * @return true if operation is successful, false otherwise.
   */
  public boolean checkoutNewBranch(String branch) {
    try {
      repository.createAndCheckoutBranch(branch);
    } catch (GitAPIException e) {
      LOGGER.error("Unable to create and checkout new branch {}", branch, e);
      return false;
    }
    return true;
  }

  /**
   * If working tree has any changes, commit and push them to the current branch.
   *
   * @param commitMessage Commit Message
   * @return true if operation is successful, false otherwise.
   */
  public boolean commitAndPush(String commitMessage) {
    try {
      if (repository.isWorkingTreeClean()) {
        LOGGER.info("Working tree is clean, nothing to commit.");
      } else {
        LOGGER.info("Committing and pushing changes to branch {}", repository.getCurrentBranch());
        repository.add(".");
        repository.commit(commitMessage);
        repository.push();
        LOGGER.info("Push successful to branch {}.", repository.getCurrentBranch());
      }
    } catch (GitAPIException | IOException e) {
      LOGGER.error("Unable to commit and push", e);
      return false;
    }
    return true;
  }

  /**
   * Delete the directory containing the local repository.
   *
   * @return true if operation is successful, false otherwise.
   */
  public boolean delete() {
    try {
      cleanUpDirectory(repository.getDirectory());
    } catch (IOException e) {
      LOGGER.error("Could not delete local repository " + repository.getDirectory());
      return false;
    }
    return true;
  }

}
