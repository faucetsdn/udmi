package com.google.bos.udmi.service.core;

import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Objects;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.Envelope.SubFolder;

public abstract class DistributorBase extends ContainerBase {

  private static final Map<Protocol, Class<? extends DistributorBase>> DISTRIBUTORS =
      ImmutableMap.of(Protocol.IOT_ACCESS, IotAccessDistributor.class);

  public static ContainerBase from(EndpointConfiguration config) {
    try {
      return Objects.requireNonNull(DISTRIBUTORS.get(config.protocol), "unknown distributor")
          .getDeclaredConstructor(EndpointConfiguration.class).newInstance(config);
    } catch (Exception e) {
      throw new RuntimeException("While instantiating distributor type " + config.protocol, e);
    }
  }

  public abstract void activate();

  public abstract void shutdown();

  abstract void registryBackoffClear(String deviceRegistryId, String deviceId);

  abstract void sendCommand(String reflectRegistry, String deviceRegistry, SubFolder udmi,
      String stringify);

  abstract void setProviderAffinity(String deviceRegistryId, String deviceId, String source);
}
