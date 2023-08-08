package com.google.bos.udmi.service.access;

import static com.google.bos.udmi.service.core.ProcessorBase.REFLECT_REGISTRY;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import com.google.bos.udmi.service.core.ProcessorBase.PreviousParseException;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import udmi.schema.CloudModel;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;
import udmi.schema.IotAccess.IotProvider;

/**
 * Generic interface for accessing iot device management.
 */
public abstract class IotAccessBase extends ContainerBase {

  protected static final String UDMI_REGISTRY = "UDMI-REFLECT";
  protected static final String EMPTY_JSON = "{}";
  private static final long REGISTRY_COMMAND_BACKOFF_SEC = 60;
  private static final Map<String, Instant> BACKOFF_MAP = new ConcurrentHashMap<>();
  private static final long CONFIG_UPDATE_BACKOFF_MS = 1000;
  private static final int CONFIG_UPDATE_MAX_RETRIES = 10;
  private static final Map<IotProvider, Class<? extends IotAccessBase>> PROVIDERS = ImmutableMap.of(
      IotProvider.DYNAMIC, DynamicIotAccessProvider.class,
      IotProvider.CLEARBLADE, ClearBladeIotAccessProvider.class,
      IotProvider.GCP, GcpIotAccessProvider.class,
      IotProvider.LOCAL, LocalIotAccessProvider.class
  );
  public static final int MAX_CONFIG_LENGTH = 65535;

  /**
   * Factory constructor for new instances.
   */
  public static IotAccessBase from(IotAccess iotAccess) {
    try {
      return PROVIDERS.get(iotAccess.provider).getDeclaredConstructor(IotAccess.class)
          .newInstance(iotAccess);
    } catch (Exception e) {
      throw new RuntimeException(
          format("While instantiating access provider type %s", iotAccess.provider), e);
    }
  }

  private static Instant getBackoff(String registryId, String deviceId) {
    return BACKOFF_MAP.get(getBackoffKey(registryId, deviceId));
  }

  private static String getBackoffKey(String registryId, String deviceId) {
    return format("%s/%s", registryId, deviceId);
  }

  protected abstract Entry<Long, String> fetchConfig(String registryId, String deviceId);

  protected abstract boolean isEnabled();

  protected abstract void sendCommandBase(String registryId, String deviceId, SubFolder folder,
      String message);

  protected abstract String updateConfig(String registryId, String deviceId, String config,
      Long version);

  private boolean registryBackoffCheck(String registryId, String deviceId) {
    return ifNotNullGet(getBackoff(registryId, deviceId), end -> Instant.now().isAfter(end),
        true);
  }

  private void registryBackoffClear(String registryId, String deviceId) {
    String backoffKey = getBackoffKey(registryId, deviceId);
    ifNotNullThen(BACKOFF_MAP.remove(backoffKey),
        () -> debug("Released registry backoff for " + backoffKey));
  }

  private Instant registryBackoffInhibit(String registryId, String deviceId) {
    if (!REFLECT_REGISTRY.equals(registryId)) {
      return null;
    }
    Instant until = Instant.now().plus(REGISTRY_COMMAND_BACKOFF_SEC, ChronoUnit.SECONDS);
    BACKOFF_MAP.put(getBackoffKey(registryId, deviceId), until);
    return until;
  }

  private String safeMunge(Function<String, String> munger, Entry<Long, String> configPair) {
    try {
      return munger.apply(ifNotNullGet(configPair, Entry::getValue));
    } catch (PreviousParseException e) {
      error("Exception parsing previous config: " + friendlyStackTrace(e));
      throw e;
    } catch (Exception e) {
      error("Exception munging config: " + friendlyStackTrace(e));
      return null;
    }
  }

  public abstract CloudModel fetchDevice(String deviceRegistryId, String deviceId);

  public abstract String fetchState(String deviceRegistryId, String deviceId);

  public abstract CloudModel listDevices(String deviceRegistryId);

  public abstract CloudModel modelDevice(String deviceRegistryId, String deviceId,
      CloudModel cloudModel);

  /**
   * Modify a device configuration. Return the full/complete update that was actually written.
   */
  public String modifyConfig(String registryId, String deviceId, Function<String, String> munger) {
    int retryCount = CONFIG_UPDATE_MAX_RETRIES;
    try {
      while (true) {
        try {
          Entry<Long, String> configPair = fetchConfig(registryId, deviceId);
          Long version = ifNotNullGet(configPair, Entry::getKey);
          return ifNotNullGet(safeMunge(munger, configPair),
              updated -> checkedUpdate(registryId, deviceId, version, updated));
        } catch (AbortLoopException e) {
          throw e;
        } catch (Exception e) {
          if (retryCount <= 0) {
            error("Failed modifying config for %s/%s: %s", registryId, deviceId,
                friendlyStackTrace(e));
            throw e;
          }
          warn(
              format("Error modifying config for %s/%s, remaining retries %d...", registryId,
                  deviceId, --retryCount));
          safeSleep(CONFIG_UPDATE_BACKOFF_MS);
        }
      }
    } catch (AbortLoopException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(
          format("Maximum config retry count exceeded for %s/%s, giving up.", registryId,
              deviceId), e);
    }
  }

  private String checkedUpdate(String registryId, String deviceId, Long version, String updated) {
    int configLength = updated.length();
    if (configLength > MAX_CONFIG_LENGTH) {
      throw new AbortLoopException(
          format("Config length %d exceeds maximum %d", configLength, MAX_CONFIG_LENGTH));
    }
    return updateConfig(registryId, deviceId, updated, version);
  }

  private static class AbortLoopException extends RuntimeException {
    public AbortLoopException(String message) {
      super(message);
    }
  }

  /**
   * Send a command to a device.
   */
  public final void sendCommand(String registryId, String deviceId, SubFolder folder,
      String message) {
    String backoffKey = getBackoffKey(registryId, deviceId);
    if (registryBackoffCheck(registryId, deviceId)) {
      try {
        Map<String, Object> messageMap = toMap(message);
        Object payloadSubType = messageMap.get("subType");
        Object payloadSubFolder = messageMap.get("subFolder");
        Object transactionId = messageMap.get("transactionId");
        debug("Sending command containing %s/%s to %s/%s/%s %s", payloadSubType, payloadSubFolder,
            registryId, deviceId, folder, transactionId);
        requireNonNull(registryId, "registry not defined");
        requireNonNull(deviceId, "device not defined");
        sendCommandBase(registryId, deviceId, folder, message);
      } catch (Exception e) {
        error("Exception sending command to %s: %s", backoffKey, friendlyStackTrace(e));
        ifNotNullThen(registryBackoffInhibit(registryId, deviceId),
            until -> debug("Setting registry backoff for %s until %s",
                backoffKey, getTimestamp(until)));
      }
    } else {
      debug("Dropping message because registry backoff for %s",
          backoffKey);
    }
  }

  public void setProviderAffinity(String registryId, String deviceId, String providerId) {
    registryBackoffClear(registryId, deviceId);
  }

  abstract String fetchRegistryMetadata(String registryId, String metadataKey);

}
