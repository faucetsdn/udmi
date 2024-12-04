package com.google.daq.mqtt.util;

import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Abstract collection of stuff for managing vendor families.
 */
public interface ProtocolFamily {

  /**
   * Set of all the supported protocol families.
   */
  Set<Class<? extends ProtocolFamily>> PROTOCOL_FAMILIES = ImmutableSet.of(
      VendorFamily.class,
      BacnetFamily.class);

  /**
   * Map of family name to instance.
   */
  Map<String, ProtocolFamily> NAMED_FAMILIES = generateFamilyMap(PROTOCOL_FAMILIES);

  /**
   * Generate a named map from the listed families.
   */
  static Map<String, ProtocolFamily> generateFamilyMap(
      Set<Class<? extends ProtocolFamily>> protocolFamilies) {
    return protocolFamilies.stream().map(clazz -> {
      try {
        return clazz.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        throw new RuntimeException("While creating protocol family map", e);
      }
    }).collect(Collectors.toMap(ProtocolFamily::familyKey, family -> family));
  }

  /**
   * Return the family name represented by this class.
   */
  String familyKey();

  /**
   * Validate the given point ref for the address family.
   */
  void refValidator(String metadataRef);
}
