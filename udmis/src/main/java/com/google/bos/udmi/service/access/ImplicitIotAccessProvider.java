package com.google.bos.udmi.service.access;

import static com.google.udmi.util.Common.DEFAULT_REGION;
import static com.google.udmi.util.GeneralUtils.isNullOrNotEmpty;
import static com.google.udmi.util.JsonUtil.asMap;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.core.ReflectProcessor;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.bos.udmi.service.support.IotDataProvider;
import com.google.common.collect.ImmutableSet;
import com.google.udmi.util.GeneralUtils;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import udmi.schema.CloudModel;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.IotAccess;

/**
 * Iot Access Provider that uses internal components.
 */
public class ImplicitIotAccessProvider extends IotAccessBase {

  public static final String CONFIG_VER_KEY = "config_ver";
  public static final String LAST_CONFIG_KEY = "last_config";
  private static final String REGISTRIES_KEY = "registries";
  private static final String IMPLICIT_DATABASE_COMPONENT = "database";
  private final boolean enabled;
  private IotDataProvider database;
  private ReflectProcessor reflect;

  public ImplicitIotAccessProvider(IotAccess iotAccess) {
    super(iotAccess);
    enabled = isNullOrNotEmpty(options.get(ENABLED_KEY));
  }

  @Override
  public void activate() {
    database = UdmiServicePod.getComponent(IMPLICIT_DATABASE_COMPONENT);
    reflect = UdmiServicePod.getComponent(ReflectProcessor.class);
    super.activate();
  }

  @Override
  public Entry<Long, String> fetchConfig(String registryId, String deviceId) {
    // TODO: Implement lease for atomic transaction.
    String config = database.key(LAST_CONFIG_KEY).forRegistry(registryId).forDevice(deviceId).get();
    String version = database.key(CONFIG_VER_KEY).forRegistry(registryId).forDevice(deviceId).get();
    Long versionLong = ofNullable(version).map(Long::parseLong).orElse(null);
    return new SimpleEntry<>(versionLong, ofNullable(config).orElse(EMPTY_JSON));
  }

  @Override
  public CloudModel fetchDevice(String deviceRegistryId, String deviceId) {
    throw new RuntimeException("fetchDevice not yet implemented");
  }

  @Override
  public String fetchRegistryMetadata(String registryId, String metadataKey) {
    return database.key(metadataKey).forRegistry(registryId).get();
  }

  @Override
  public String fetchState(String deviceRegistryId, String deviceId) {
    throw new RuntimeException("fetchState not yet implemented");
  }

  @Override
  public Set<String> getRegistriesForRegion(String region) {
    if (region == null) {
      return ImmutableSet.of(DEFAULT_REGION);
    }
    if (!region.equals(DEFAULT_REGION)) {
      return ImmutableSet.of();
    }
    String regionsString = ofNullable(database.key(REGISTRIES_KEY).get()).orElse("");
    return Arrays.stream(regionsString.split(",")).map(String::trim)
        .filter(GeneralUtils::isNotEmpty).collect(Collectors.toSet());
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public CloudModel listDevices(String deviceRegistryId) {
    throw new RuntimeException("listDevices not yet implemented");
  }

  @Override
  public CloudModel modelResource(String deviceRegistryId, String deviceId, CloudModel cloudModel) {
    throw new RuntimeException("modelResource not yet implemented");
  }

  @Override
  public void sendCommandBase(String registryId, String deviceId, SubFolder folder,
      String message) {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = registryId;
    envelope.deviceId = deviceId;
    envelope.subFolder = folder;
    envelope.subType = SubType.COMMANDS;
    reflect.getDispatcher().withEnvelope(envelope).publish(asMap(message));
  }

  @Override
  public String updateConfig(String registryId, String deviceId, String config, Long version) {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = registryId;
    envelope.deviceId = deviceId;
    envelope.subType = SubType.CONFIG;
    reflect.getDispatcher().withEnvelope(envelope).publish(asMap(config));

    // TODO: Implement lease for atomic transaction.
    String prev = database.key(CONFIG_VER_KEY).forRegistry(registryId).forDevice(deviceId).get();
    if (version != null && !version.toString().equals(prev)) {
      throw new RuntimeException("Config version update mismatch");
    }

    database.key(LAST_CONFIG_KEY).forRegistry(registryId).forDevice(deviceId).put(config);
    String update = ofNullable(version).map(v -> v + 1)
        .orElseGet(() -> ofNullable(prev).map(Long::parseLong).orElse(1L)).toString();
    database.key(CONFIG_VER_KEY).forRegistry(registryId).forDevice(deviceId).put(update);

    return config;
  }
}
