package com.google.daq.mqtt.util;

import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface NetworkFamily {

  Set<Class<? extends NetworkFamily>> NETWORK_FAMILIES = ImmutableSet.of(VirtualFamily.class);
  Map<String, NetworkFamily> NAMED_FAMILIES = generateFamilyMap(NETWORK_FAMILIES);

  static Map<String, NetworkFamily> generateFamilyMap(
      Set<Class<? extends NetworkFamily>> networkFamilies) {
    return networkFamilies.stream().map(clazz -> {
      try {
        return clazz.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        throw new RuntimeException("While creating network family map", e);
      }
    }).collect(Collectors.toMap(NetworkFamily::familyName, family -> family));
  }

  String familyName();

  void refValidator(String metadataRef);
}
