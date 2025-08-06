package com.google.daq.mqtt.sequencer;

import static com.google.udmi.util.GeneralUtils.catchToNull;

import com.google.udmi.util.SiteModel;
import java.util.Set;

/**
 * Resolve the DISCOVERY facet.
 */
public class DiscoveryFacetResolver implements FacetResolver {

  @Override
  public Set<String> resolve(SiteModel siteModel, String deviceId) {
    return catchToNull(() -> siteModel.getMetadata(deviceId).discovery.families.keySet());
  }
}
