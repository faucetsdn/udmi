package daq.pubber;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for Pubber.
 */
public class PubberTest extends TestBase {

  private static final String TEST_PROJECT = "test-project";
  private static final String TEST_SITE = "../sites/udmi_site_model";
  private static final String BAD_DEVICE = "ASHDQWHD";
  private static final String SERIAL_NO = "18217398172";

  @Test
  public void missingDevice() {
    try {
      List<String> args = ImmutableList.of(TEST_PROJECT, TEST_SITE, BAD_DEVICE, SERIAL_NO);
      Pubber.main(args.toArray(new String[0]));
    } catch (Throwable e) {
      while (e != null) {
        String message = e.getMessage();
        if (message.contains(BAD_DEVICE)) {
          return;
        }
        e = e.getCause();
      }
    }
    throw new RuntimeException("No exception thrown for bad device");
  }
}