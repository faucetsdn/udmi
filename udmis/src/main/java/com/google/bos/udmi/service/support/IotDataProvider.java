package com.google.bos.udmi.service.support;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

import com.google.bos.udmi.service.pod.UdmiComponent;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import udmi.schema.IotAccess;
import udmi.schema.IotAccess.IotProvider;

/**
 * Interface for iot data providers.
 */
public interface IotDataProvider extends UdmiComponent {

  Map<IotProvider, Class<? extends IotDataProvider>> PROVIDERS =
      ImmutableMap.of(IotProvider.ETCD, EtcdDataProvider.class);

  /**
   * Construct a provider given the configuration.
   */
  static IotDataProvider from(IotAccess iotAccess) {
    try {
      checkState(PROVIDERS.containsKey(iotAccess.provider),
          "Unknown data provider " + iotAccess.provider);
      return PROVIDERS.get(iotAccess.provider).getDeclaredConstructor(IotAccess.class)
          .newInstance(iotAccess);
    } catch (Exception e) {
      throw new RuntimeException(
          format("While instantiating data provider type %s", iotAccess.provider), e);
    }
  }

  String getSystemEntry(String regionsKey);
}
