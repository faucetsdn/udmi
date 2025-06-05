package com.google.udmi.util.git;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.slf4j.LoggerFactory;

public class GoogleCloudSourceRepository extends GenericGitRepository {

  public GoogleCloudSourceRepository(RepositoryConfig config) {
    super(config);
  }

  public static void main(String[] args)
      throws IOException, GitAPIException, GeneralSecurityException {
    Logger jgitLogger = (Logger) LoggerFactory.getLogger("org.eclipse.jgit");
    jgitLogger.setLevel(Level.INFO);

    /*
    String projectId = "bos-platform-dev";
    String repoName = "ZZ-TRI-FECTA-1";
    String cloneDir = "/usr/local/google/home/heykhyati/Projects/udmi/var/udmi/sites/ZZ-TRI-FECTA-1";

    try (GoogleCloudSourceRepository repo2 = new GoogleCloudSourceRepository(
        RepositoryConfig.fromGoogleCloudSourceRepoName(repoName, cloneDir,
            new GoogleCloudCredentialsProvider(), projectId))) {

      repo2.cloneRepo("main");

      List<String> l = repo2.listLocalBranches();
      for (String n: l) {
        System.out.println("Local: " + n);
      }

      l = repo2.listRemoteBranches();
      for (String n: l) {
        System.out.println("Remote: " + n);
      }

      repo2.checkoutRemoteBranch("proposal");
    }
    */

    try (GenericGitRepository repo1 = new GenericGitRepository(RepositoryConfig.forRemote(
        "/usr/local/google/home/heykhyati/Projects/udmi/var/udmi/sites/ZZ-TRI-FECTA-1",
        "/usr/local/google/home/heykhyati/Projects/udmi/var/udmi/sites/ZZ-TRI-FECTA-2",
        null
    ))) {
      repo1.open();
      List<String> l = repo1.listLocalBranches();
      for (String n : l) {
        System.out.println("Local: " + n);
      }
      try {
        repo1.checkoutBranch("main");
      } catch (RefNotFoundException e) {
        repo1.checkoutRemoteBranch("main");
      }
      System.out.println("Current branch: " + repo1.getCurrentBranch());
    }
  }

  @Override
  public String createPullRequest(String title, String body, String sourceBranch,
      String targetBranch) throws GitAPIException, IOException {
    throw new UnsupportedOperationException("createPullRequest not implemented");
  }

  @Override
  public List<String> listOpenPullRequests(String targetBranch)
      throws GitAPIException, IOException {
    throw new UnsupportedOperationException("listOpenPullRequests not implemented");
  }

}
