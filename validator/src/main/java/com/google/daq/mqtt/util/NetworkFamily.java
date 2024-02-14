package com.google.daq.mqtt.util;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

public interface NetworkFamily {

  Map<String, NetworkFamily> FAMILIES = ImmutableMap.of(
      VirtualFamily.FAMILY, VirtualFamily.INSTANCE);

  void refValidator(String metadataRef);
}
