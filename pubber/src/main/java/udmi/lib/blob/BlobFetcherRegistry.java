package udmi.lib.blob;

import java.util.HashMap;
import java.util.Map;
import udmi.lib.blob.intf.BlobFetcher;

/**
 * Registry for blob fetchers based on URL schemes.
 */
public class BlobFetcherRegistry {

  private static final Map<String, BlobFetcher> REGISTRY = new HashMap<>();

  static {
    // Register default fetchers
    registerBlobFetcher("data", new DataUriFetcher());
  }

  /**
   * Registers a fetcher for a specific scheme.
   */
  public static void registerBlobFetcher(String scheme, BlobFetcher fetcher) {
    REGISTRY.put(scheme.toLowerCase(), fetcher);
  }

  /**
   * Retrieves the appropriate fetcher for a given URL based on its scheme.
   */
  public static BlobFetcher getFetcher(String url) {
    String scheme = getScheme(url);
    BlobFetcher fetcher = REGISTRY.get(scheme);
    if (fetcher == null) {
      throw new IllegalArgumentException("No fetcher registered for scheme: " + scheme);
    }
    return fetcher;
  }

  private static String getScheme(String url) {
    if (url.startsWith("data:")) {
      return "data";
    }
    int colonIndex = url.indexOf(":");
    if (colonIndex != -1) {
      return url.substring(0, colonIndex).toLowerCase();
    }
    throw new IllegalArgumentException("Invalid URL format (missing scheme): " + url);
  }
}
