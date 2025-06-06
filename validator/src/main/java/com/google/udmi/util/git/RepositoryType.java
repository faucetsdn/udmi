package com.google.udmi.util.git;

/**
 * Enum for types of supported repositories.
 */
public enum RepositoryType {
  GOOGLE_CLOUD_SOURCE, // GCP Cloud Source Repositories
  LOCAL_REMOTE,        // A generic Git server or local file system repo
}