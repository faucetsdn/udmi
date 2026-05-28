package udmi.lib.blob.intf;

/**
 * Interface for fetching blob data from a given URL.
 */
public interface BlobFetcher {

  /**
   * Fetches blob data from the given URL.
   *
   * @param url The URL to fetch from.
   * @return The fetched data as a byte array.
   */
  byte[] fetch(String url);
}
