package udmi.lib.blob;

import static java.lang.String.format;
import static udmi.lib.client.host.PublisherHost.DATA_URL_JSON_BASE64;

import java.util.Base64;
import udmi.lib.base.UdmiException.BlobParseException;
import udmi.lib.blob.intf.BlobFetcher;


/**
 * Fetcher implementation for data: application/json;base64 URLs.
 */
public class DataUriFetcher implements BlobFetcher {

  @Override
  public byte[] fetch(String url) {
    if (!url.startsWith(DATA_URL_JSON_BASE64)) {
      throw new RuntimeException(format("URL encoding not supported: %s", url));
    }
    try {
      return Base64.getDecoder().decode(url.substring(DATA_URL_JSON_BASE64.length()));
    } catch (IllegalArgumentException e) {
      throw new BlobParseException("Failed to decode base64 payload");
    }
  }
}
