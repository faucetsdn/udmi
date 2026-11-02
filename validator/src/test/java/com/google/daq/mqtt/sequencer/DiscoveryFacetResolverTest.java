package com.google.daq.mqtt.sequencer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.TestCommon;
import com.google.udmi.util.SiteModel;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the discovery facet resolver.
 */
public class DiscoveryFacetResolverTest {

  private static final String DEVICE_WITH_FAMILIES = "AHU-1";
  private static final String DEVICE_WITHOUT_FAMILIES = "AHU-22";
  private static final String UNKNOWN_DEVICE = "XXX-0";

  private final DiscoveryFacetResolver resolver = new DiscoveryFacetResolver();
  private SiteModel siteModel;

  @Before
  public void loadSiteModel() {
    siteModel = new SiteModel(TestCommon.SITE_DIR);
    siteModel.initialize();
  }

  @Test
  public void resolveDeviceWithFamilies() {
    Set<String> facets = resolver.resolve(siteModel, DEVICE_WITH_FAMILIES);
    assertEquals("discovery families", ImmutableSet.of("vendor", "bacnet"), facets);
  }

  @Test
  public void resolveDeviceWithoutFamilies() {
    Set<String> facets = resolver.resolve(siteModel, DEVICE_WITHOUT_FAMILIES);
    assertNotNull("resolved facets should never be null", facets);
    assertTrue("no discovery families expected", facets.isEmpty());
  }

  @Test
  public void resolveUnknownDeviceFails() {
    NullPointerException thrown = assertThrows(NullPointerException.class,
        () -> resolver.resolve(siteModel, UNKNOWN_DEVICE));
    assertTrue("error identifies the device", thrown.getMessage().contains(UNKNOWN_DEVICE));
  }
}

