package com.google.udmi.util.git;

import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;

/**
 * Interface for a Git Repository.
 */
public interface GitRepositoryInterface extends AutoCloseable {

  void init() throws GitAPIException;

  void cloneRepo() throws GitAPIException, IOException;

  void cloneRepo(String defaultBranch) throws GitAPIException, IOException;

  void add(String filePattern) throws GitAPIException, IOException;

  void remove(String filePattern) throws GitAPIException;

  RevCommit commit(String message) throws GitAPIException, IOException;

  RevCommit commit(String authorName, String authorEmail, String message)
      throws GitAPIException, IOException;

  FetchResult fetch() throws GitAPIException, IOException;

  void pull() throws GitAPIException, IOException;

  Iterable<PushResult> push() throws GitAPIException, IOException;

  String getCurrentBranch() throws IOException, GitAPIException;

  List<String> listLocalBranches() throws GitAPIException, IOException;

  List<String> listRemoteBranches() throws GitAPIException, IOException;

  List<String> listTags() throws GitAPIException, IOException;

  Status getStatus() throws GitAPIException, IOException;

  List<RevCommit> getCommitLog(int maxCount) throws GitAPIException, IOException;

  void checkoutBranch(String branchName) throws GitAPIException, IOException;

  void checkoutRemoteBranch(String branchName) throws IOException, GitAPIException;

  void createBranch(String branchName) throws GitAPIException, IOException;

  void createAndCheckoutBranch(String branchName) throws GitAPIException, IOException;

  void deleteLocalBranch(String branchName, boolean force) throws GitAPIException, IOException;

  default String createPullRequest(String title, String body, String sourceBranch,
      String targetBranch) {
    return createPullRequest(title, body, sourceBranch, targetBranch, null);
  }

  default String createPullRequest(String title, String body, String sourceBranch,
      String targetBranch, String author) {
    throw new UnsupportedOperationException("createPullRequest not implemented");
  }

  default List<String> listOpenPullRequests(String targetBranch)
      throws GitAPIException, IOException {
    throw new UnsupportedOperationException("listOpenPullRequests not implemented");
  }

  default String getCommitUrl(String branch) {
    throw new UnsupportedOperationException("getCommitUrl not implemented");
  }

  @Override
  void close();

}
