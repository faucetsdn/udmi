package com.google.udmi.util.git;

import com.google.api.services.sourcerepo.v1.CloudSourceRepositoriesScopes;
import com.google.auth.oauth2.GoogleCredentials;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import java.io.IOException;

public class GoogleCloudCredentialsProvider extends CredentialsProvider {

  private final GoogleCredentials credentials;

  public GoogleCloudCredentialsProvider() throws IOException {
    this.credentials = GoogleCredentials.getApplicationDefault()
        .createScoped(CloudSourceRepositoriesScopes.CLOUD_PLATFORM);
  }

  @Override
  public boolean get(URIish uri, CredentialItem... items) {
    // JGit will ask for username and password. We only provide the password.
    for (CredentialItem item : items) {
      if (item instanceof CredentialItem.Password) {
        try {
          // Refresh the token every time it's requested
          credentials.refresh();
          String token = credentials.getAccessToken().getTokenValue();
          ((CredentialItem.Password) item).setValue(token.toCharArray());
          return true; // We have provided the credentials
        } catch (IOException e) {
          // Handle error, maybe log it
          e.printStackTrace();
          return false;
        }
      } else if (item instanceof CredentialItem.Username) {
        // The username is always the same for Google Cloud Source Repositories
        ((CredentialItem.Username) item).setValue("oauth2accesstoken");
      }
    }
    return false;
  }

  @Override
  public boolean isInteractive() {
    return false;
  }

  @Override
  public boolean supports(CredentialItem... items) {
    for (CredentialItem item : items) {
      if (item instanceof CredentialItem.Password || item instanceof CredentialItem.Username) {
        continue;
      }
      return false;
    }
    return true;
  }
}

// How to use the robust provider:
// CredentialsProvider gcpProvider = new GoogleCloudCredentialsProvider();
// pushCommand.setCredentialsProvider(gcpProvider);
// pushCommand.call(); // This will now work even if the app runs for days.