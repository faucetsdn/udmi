package udmi.lib.blob;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import udmi.lib.base.UdmiException;

/**
 * Unit tests for DataUriFetcher.
 */
public class DataUriFetcherTest {

  private final DataUriFetcher fetcher = new DataUriFetcher();

  @Test
  public void testFetchJsonDataUri() {
    // "hello" in base64 is "aGVsbG8="
    String url = "data:application/json;base64,aGVsbG8=";
    byte[] expected = "hello".getBytes();
    byte[] actual = fetcher.fetch(url);
    assertArrayEquals(expected, actual);
  }

  @Test
  public void testFetchTextDataUri() {
    // "world" in base64 is "d29ybGQ="
    String url = "data:text/plain;base64,d29ybGQ=";
    byte[] expected = "world".getBytes();
    byte[] actual = fetcher.fetch(url);
    assertArrayEquals(expected, actual);
  }

  @Test
  public void testFetchUnsupportedScheme() {
    String url = "http://example.com/blob";
    try {
      fetcher.fetch(url);
      fail("Expected RuntimeException for unsupported scheme");
    } catch (RuntimeException e) {
      // Expected
    }
  }

  @Test
  public void testFetchInvalidBase64() {
    String url = "data:text/plain;base64,invalid-base-64!!!";
    try {
      fetcher.fetch(url);
      fail("Expected UdmiException for invalid base64 payload");
    } catch (UdmiException e) {
      // Expected
    }
  }
}
