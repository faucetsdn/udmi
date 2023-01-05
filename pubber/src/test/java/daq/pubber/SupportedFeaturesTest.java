package daq.pubber;

import static org.junit.Assert.assertEquals;

import com.google.udmi.util.Features;
import org.junit.Test;

/**
 * Tests for the SupportedFeatures class.
 */
public class SupportedFeaturesTest {

  @Test
  public void supportedFeatures() {
    Features supportedFeatures = SupportedFeatures.getFeatures();
    assertEquals("restart feature", 0,
        supportedFeatures.get("system").get("mode").get("restart").size());
  }

}