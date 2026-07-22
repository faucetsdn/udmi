package udmi.lib.blob;

import static java.lang.String.format;

import java.util.Base64;
import udmi.lib.base.UdmiException;
import udmi.lib.blob.intf.BlobFetcher;
import udmi.schema.Category;


/**
 * Fetcher implementation for data: URLs.
 */
public class DataUriFetcher implements BlobFetcher {

  @Override
  public byte[] fetch(String url) {
    if (!url.startsWith("data:") || !url.contains(";base64,")) {
      throw new RuntimeException(format("URL encoding not supported: %s", url));
    }
    try {
      int base64MarkerIndex = url.indexOf(";base64,");
      String base64Data = url.substring(base64MarkerIndex + ";base64,".length());
      return Base64.getDecoder().decode(base64Data);
    } catch (IllegalArgumentException e) {
      throw new UdmiException(Category.BLOBSET_BLOB_PARSE, "Failed to decode base64 payload");
    }
  }
}
