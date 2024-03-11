package com.google.daq.mqtt.util;

import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import udmi.schema.Common.ProtocolFamily;

/**
 * Abstract collection of stuff for managing vendor families.
 */
public interface NetworkFamily {

  /**
   * Set of all the supported network families.
   */
  Set<Class<? extends NetworkFamily>> NETWORK_FAMILIES = ImmutableSet.of(
      VendorFamily.class,
      BacnetFamily.class);

  /**
   * Map of family name to instance.
   */
  Map<ProtocolFamily, NetworkFamily> NAMED_FAMILIES = generateFamilyMap(NETWORK_FAMILIES);

  /**
   * Generate a named map from the listed families.
   */
  static Map<ProtocolFamily, NetworkFamily> generateFamilyMap(
      Set<Class<? extends NetworkFamily>> networkFamilies) {
    return networkFamilies.stream().map(clazz -> {
      try {
        return clazz.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        throw new RuntimeException("While creating network family map", e);
      }
    }).collect(Collectors.toMap(NetworkFamily::familyKey, family -> family));
  }

  /**
   * Return the family name represented by this class.
   */
  ProtocolFamily familyKey();

  /**
   * Validate the given point ref for the address family.
   */
  void refValidator(String metadataRef);
}
