package com.google.udmi.util;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

/**
 * Google Cloud Credentials Provider - refreshes credentials periodically for the requested scopes.
 */
public class GoogleCloudCredentialsProvider extends CredentialsProvider {

  private final GoogleCredentials credentials;

  public GoogleCloudCredentialsProvider(List<String> scopes) throws IOException {
    this.credentials = GoogleCredentials.getApplicationDefault().createScoped(scopes);
  }

  @Override
  public boolean get(URIish uri, CredentialItem... items) {
    for (CredentialItem item : items) {
      if (item instanceof CredentialItem.Password) {
        try {
          credentials.refresh();
          String token = credentials.getAccessToken().getTokenValue();
          ((CredentialItem.Password) item).setValue(token.toCharArray());
          return true;
        } catch (IOException e) {
          e.printStackTrace();
          return false;
        }
      } else if (item instanceof CredentialItem.Username) {
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
