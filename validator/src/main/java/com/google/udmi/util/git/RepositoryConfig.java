package com.google.udmi.util.git;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sourcerepo.v1.CloudSourceRepositories;
import com.google.api.services.sourcerepo.v1.CloudSourceRepositoriesScopes;
import com.google.api.services.sourcerepo.v1.model.Repo;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.udmi.util.GoogleCloudCredentialsProvider;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import org.eclipse.jgit.transport.CredentialsProvider;


/**
 * Repository Config. Used to initialize a GenericGitRepository.
 *
 * @param remoteUrl URL for remote repository, can also be a local path
 * @param localPath URL for the local repository
 * @param type Type of the repository
 * @param credentialsProvider To manage credentials
 * @param projectId Project ID specifically for Google cloud source repositories
 */
public record RepositoryConfig(String remoteUrl,
                               String localPath,
                               RepositoryType type,
                               CredentialsProvider credentialsProvider,
                               String projectId) {

  public static RepositoryConfig forRemote(String remoteUrl, String localPath,
      CredentialsProvider credentialsProvider) {
    return new RepositoryConfig(remoteUrl, localPath, RepositoryType.LOCAL_REMOTE,
        credentialsProvider, null);
  }

  public static RepositoryConfig forRemote(String remoteUrl, String localPath) {
    return new RepositoryConfig(remoteUrl, localPath, RepositoryType.LOCAL_REMOTE, null, null);
  }

  public static RepositoryConfig forGoogleCloudSourceRepoUrl(String remoteUrl, String localPath,
      CredentialsProvider credentialsProvider, String projectId) {
    return new RepositoryConfig(remoteUrl, localPath, RepositoryType.GOOGLE_CLOUD_SOURCE,
        credentialsProvider, projectId);
  }

  /**
   * Get RepositoryConfig for a GCSR from its repository name.
   */
  public static RepositoryConfig fromGoogleCloudSourceRepoName(String repoName, String localPath,
      CredentialsProvider credentialsProvider, String projectId)
      throws IOException, GeneralSecurityException {
    GoogleCredentials credential =
        GoogleCredentials.getApplicationDefault().createScoped(Collections.singletonList(
            CloudSourceRepositoriesScopes.SOURCE_READ_ONLY));
    CloudSourceRepositories service = new CloudSourceRepositories.Builder(
        GoogleNetHttpTransport.newTrustedTransport(),
        GsonFactory.getDefaultInstance(),
        new HttpCredentialsAdapter(credential)
    ).setApplicationName("UDMI Tools").build();
    String repoResourceName = String.format("projects/%s/repos/%s", projectId, repoName);
    Repo repo = service.projects().repos().get(repoResourceName).execute();

    return forGoogleCloudSourceRepoUrl(repo.getUrl(), localPath, credentialsProvider, projectId);
  }

  public static RepositoryConfig fromGoogleCloudSourceRepoName(String repoName, String localPath,
      String projectId)
      throws IOException, GeneralSecurityException {
    return fromGoogleCloudSourceRepoName(repoName, localPath, new GoogleCloudCredentialsProvider(
        Collections.singletonList(CloudSourceRepositoriesScopes.CLOUD_PLATFORM)), projectId);
  }

}
