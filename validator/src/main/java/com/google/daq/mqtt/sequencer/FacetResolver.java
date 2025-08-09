package com.google.daq.mqtt.sequencer;

import com.google.udmi.util.SiteModel;
import java.util.Set;

/**
 * Generic resolver for a discovery facet.
 */
public interface FacetResolver {

  /**
   * Resolve a facet set for the given site model and device.
   */
  Set<String> resolve(SiteModel siteModel, String deviceId);

  /**
   * Returns the primary value for this facet, which is used for testing simplification.
   */
  String primary();
}
