package udmi.lib.client.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Unit tests for PublisherHost static methods.
 */
public class PublisherHostTest {

  @Test
  public void testAcquireBlobDataJson() {
    // "hello" in base64 is "aGVsbG8="
    // sha256 of "hello" is "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    String url = "data:application/json;base64,aGVsbG8=";
    String sha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
    String actual = PublisherHost.acquireBlobData(url, sha256);
    assertEquals("hello", actual);
  }

  @Test
  public void testAcquireBlobDataText() {
    // "world" in base64 is "d29ybGQ="
    // sha256 of "world" is "486ea46224d1bb4fb680f34f7c9ad96a8f24ec88be73ea8e5a6c65260e9cb8a7"
    String url = "data:text/plain;base64,d29ybGQ=";
    String sha256 = "486ea46224d1bb4fb680f34f7c9ad96a8f24ec88be73ea8e5a6c65260e9cb8a7";
    String actual = PublisherHost.acquireBlobData(url, sha256);
    assertEquals("world", actual);
  }

  @Test
  public void testAcquireBlobDataHashMismatch() {
    String url = "data:text/plain;base64,d29ybGQ=";
    String wrongSha256 = "wrong-sha256-hash-value-1234567890abcdef1234567890abcdef12345";
    try {
      PublisherHost.acquireBlobData(url, wrongSha256);
      fail("Expected RuntimeException for hash mismatch");
    } catch (RuntimeException e) {
      // Expected
    }
  }

  @Test
  public void testAcquireBlobDataUnsupportedScheme() {
    String url = "http://example.com/blob";
    try {
      PublisherHost.acquireBlobData(url, "some-sha256");
      fail("Expected RuntimeException for unsupported scheme");
    } catch (RuntimeException e) {
      // Expected
    }
  }
}
