package com.google.daq.mqtt.sequencer;

import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.udmi.util.SiteModel;
import java.util.Map;
import java.util.Set;
import udmi.lib.ProtocolFamily;
import udmi.schema.FamilyDiscoveryModel;
import udmi.schema.Metadata;

/**
 * Resolve the DISCOVERY facet.
 */
public class DiscoveryFacetResolver implements FacetResolver {

  public static final String PRIMARY_DISCOVERY_FAMILY = ProtocolFamily.VENDOR;

  /**
   * Resolve the discovery families declared for the device. A device without a {@code discovery}
   * block, or without any {@code discovery.families}, resolves to an empty set. A device that is
   * not present in the site model is an error, not an empty facet set.
   */
  @Override
  public Set<String> resolve(SiteModel siteModel, String deviceId) {
    Metadata metadata = requireNonNull(siteModel.getMetadata(deviceId),
        format("No metadata found for device %s", deviceId));
    Map<String, FamilyDiscoveryModel> families =
        ifNotNullGet(metadata.discovery, discovery -> discovery.families);
    return ifNotNullGet(families, Map::keySet, ImmutableSet.of());
  }

  @Override
  public String primary() {
    return PRIMARY_DISCOVERY_FAMILY;
  }
}
