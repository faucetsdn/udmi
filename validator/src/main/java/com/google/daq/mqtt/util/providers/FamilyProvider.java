package com.google.daq.mqtt.util.providers;

import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Abstract collection of stuff for managing vendor families.
 */
public interface FamilyProvider {

  /**
   * Set of all the supported protocol families.
   */
  Set<Class<? extends FamilyProvider>> PROTOCOL_FAMILIES = ImmutableSet.of(
      IotFamilyProvider.class,
      VendorFamilyProvider.class,
      EtherFamilyProvider.class,
      Ipv4FamilyProvider.class,
      Ipv6FamilyProvider.class,
      BacnetFamilyProvider.class,
      FqdnFamilyProvider.class
  );

  /**
   * Map of family name to instance.
   */
  Map<String, FamilyProvider> NAMED_FAMILIES = generateFamilyMap(PROTOCOL_FAMILIES);

  /**
   * Generate a named map from the listed families.
   */
  static Map<String, FamilyProvider> generateFamilyMap(
      Set<Class<? extends FamilyProvider>> protocolFamilies) {
    return protocolFamilies.stream().map(clazz -> {
      try {
        return clazz.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        throw new RuntimeException("While creating protocol family map", e);
      }
    }).collect(Collectors.toMap(FamilyProvider::familyKey, family -> family));
  }

  static String constructRef(String family, String device, String point) {
    return family + "://" + device + "/" + point;
  }

  /**
   * Return the family name represented by this class.
   */
  String familyKey();

  /**
   * Validate the given point ref for the address family.
   */
  void validateRef(String metadataRef);

  void validateAddr(String scanAddr);

  default void validateNetwork(String network) {
    throw new IllegalArgumentException("Network designator not allowed for family " + familyKey());
  }
}
