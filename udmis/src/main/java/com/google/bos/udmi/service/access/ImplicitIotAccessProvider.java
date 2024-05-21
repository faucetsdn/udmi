package com.google.bos.udmi.service.access;

import static com.google.udmi.util.Common.DEFAULT_REGION;
import static com.google.udmi.util.GeneralUtils.isNullOrNotEmpty;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.bos.udmi.service.support.IotDataProvider;
import com.google.common.collect.ImmutableSet;
import com.google.udmi.util.GeneralUtils;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import udmi.schema.CloudModel;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;

/**
 * Iot Access Provider that uses internal components.
 */
public class ImplicitIotAccessProvider extends IotAccessBase {

  private static final String REGISTRIES_KEY = "/registries";
  private static final String IMPLICIT_DATABASE_COMPONENT = "database";
  private final boolean enabled;
  private IotDataProvider database;

  public ImplicitIotAccessProvider(IotAccess iotAccess) {
    super(iotAccess);
    enabled = isNullOrNotEmpty(options.get(ENABLED_KEY));
  }

  @Override
  public void activate() {
    database = UdmiServicePod.getComponent(IMPLICIT_DATABASE_COMPONENT);
    // TODO: Get database connection here
    super.activate();
    // TODO: Activate reflector pipe here
  }

  @Override
  public Entry<Long, String> fetchConfig(String registryId, String deviceId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CloudModel fetchDevice(String deviceRegistryId, String deviceId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public String fetchRegistryMetadata(String registryId, String metadataKey) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public String fetchState(String deviceRegistryId, String deviceId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public Set<String> getRegistriesForRegion(String region) {
    if (region == null) {
      return ImmutableSet.of(DEFAULT_REGION);
    }
    if (!region.equals(DEFAULT_REGION)) {
      return ImmutableSet.of();
    }
    String regionsString = ofNullable(database.getSystemEntry(REGISTRIES_KEY)).orElse("");
    return Arrays.stream(regionsString.split(",")).map(String::trim)
        .filter(GeneralUtils::isNotEmpty).collect(Collectors.toSet());
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public CloudModel listDevices(String deviceRegistryId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CloudModel modelDevice(String deviceRegistryId, String deviceId, CloudModel cloudModel) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CloudModel modelRegistry(String deviceRegistryId, String deviceId, CloudModel cloudModel) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public void sendCommandBase(String registryId, String deviceId, SubFolder folder,
      String message) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public String updateConfig(String registryId, String deviceId, String config, Long version) {
    throw new RuntimeException("Not yet implemented");
  }
}
