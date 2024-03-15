package com.google.bos.udmi.service.access;

import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import com.google.bos.udmi.service.core.DistributorPipe;
import com.google.bos.udmi.service.core.ProcessorBase.PreviousParseException;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.bos.udmi.service.pod.SimpleHandler;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;
import udmi.schema.UdmiState;

/**
 * Generic interface for accessing iot device management.
 */
public abstract class IotAccessBase extends ContainerBase implements IotAccessProvider,
    SimpleHandler {

  public static final int MAX_CONFIG_LENGTH = 262144;
  protected static final String EMPTY_JSON = "{}";
  private static final long REGISTRY_COMMAND_BACKOFF_SEC = 60;
  private static final Map<String, Instant> BACKOFF_MAP = new ConcurrentHashMap<>();
  private static final long CONFIG_UPDATE_BACKOFF_MS = 1000;
  private static final int CONFIG_UPDATE_MAX_RETRIES = 10;
  private static final Duration REGISTRY_REFRESH = Duration.ofMinutes(10);
  private static final Duration REGISTRY_BACKOFF = Duration.ofMinutes(1);
  final Map<String, Object> options;
  private final AtomicReference<Instant> lastRegistryFetch =
      new AtomicReference<>(Instant.ofEpochSecond(0));
  private CompletableFuture<Map<String, String>> registryRegions;
  private DistributorPipe distributor;

  public IotAccessBase(IotAccess iotAccess) {
    super(iotAccess.name, 0, null);
    options = parseOptions(iotAccess);
  }

  private static Instant getBackoff(String registryId, String deviceId) {
    return BACKOFF_MAP.get(getBackoffKey(registryId, deviceId));
  }

  private static String getBackoffKey(String registryId, String deviceId) {
    return format("%s/%s", registryId, deviceId);
  }

  protected Map<String, String> fetchRegistryRegions() {
    Set<String> cloudRegions = getRegistriesForRegion(null);
    if (cloudRegions == null) {
      return null;
    }
    info("Fetching registries for: " + CSV_JOINER.join(cloudRegions));
    Map<String, String> regionMap = cloudRegions.stream().flatMap(
        region -> getRegistriesForRegionLog(region).stream()
            .collect(Collectors.toMap(x -> x, x -> region)).entrySet()
            .stream()).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    debug(format("Fetched %s registry regions", regionMap.size()));
    return regionMap;
  }

  @Nullable
  protected String getProjectId(IotAccess iotAccess) {
    try {
      return variableSubstitution(iotAccess.project_id, "project id not specified");
    } catch (IllegalArgumentException e) {
      warn("Missing variable in substitution, disabling provider: " + friendlyStackTrace(e));
      return null;
    }
  }

  protected String getRefreshedRegistryRegion(String registryId) {
    populateRegistryRegions(registryId);
    try {
      return registryRegions.get().get(registryId);
    } catch (Exception e) {
      throw new RuntimeException("While getting region for registry " + registryId, e);
    }
  }

  @NotNull
  protected String getRegistryRegion(String registryId) {
    return requireNonNull(getRefreshedRegistryRegion(registryId),
        "unknown region for registry " + registryId);
  }

  protected synchronized void populateRegistryRegions() {
    populateRegistryRegions(null);
  }

  protected synchronized void populateRegistryRegions(String checkRegistryId) {
    Instant now = Instant.now();
    Instant last = lastRegistryFetch.get();
    boolean stale = last.plus(REGISTRY_REFRESH).isBefore(now);
    boolean backoff = last.plus(REGISTRY_BACKOFF).isAfter(now);
    boolean target = ifNotNullGet(checkRegistryId,
        id -> registryRegions.getNow(ImmutableMap.of()).containsKey(id), true);
    boolean update = stale || !(target || backoff);
    if (update) {
      lastRegistryFetch.set(now);
      Map<String, String> previousRegions =
          ifNotNullGet(registryRegions, regions -> regions.getNow(ImmutableMap.of()));
      registryRegions = new CompletableFuture<>();
      registryRegions.complete(fetchRegistryRegions());
      Map<String, String> currentRegions = registryRegions.getNow(ImmutableMap.of());
      ifNotNullThen(previousRegions,
          () -> disseminateDifference(previousRegions, currentRegions));
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

  private void disseminateDifference(Map<String, String> previousRegions,
      Map<String, String> currentRegions) {
    UdmiState udmiState = new UdmiState();
    udmiState.regions = currentRegions.entrySet().stream()
        .filter(entry -> !previousRegions.containsKey(entry.getKey())).collect(
            Collectors.toMap(Entry::getKey, Entry::getValue));
    Envelope envelope = new Envelope();
    ifNotNullThen(distributor, derp -> derp.publish(envelope, udmiState, containerId));
  }

  private Set<String> getRegistriesForRegionLog(String region) {
    debug("Fetching region registries for " + region);
    return getRegistriesForRegion(region);
  }

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
    if (!reflectRegistry.equals(registryId)) {
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

  @Override
  public void activate() {
    super.activate();
    if (isEnabled()) {
      distributor = UdmiServicePod.maybeGetComponent((String) options.get("distributor"));
      populateRegistryRegions();
    }
  }

  public Set<String> getRegistriesForRegion(String region) {
    throw new RuntimeException("Not implemented");
  }

  /**
   * Return a list of all the registries.
   */
  public Set<String> listRegistries() {
    try {
      populateRegistryRegions();
      return registryRegions.get().keySet();
    } catch (Exception e) {
      throw new RuntimeException("While getting list of all registries", e);
    }
  }

  /**
   * Modify a device configuration. Return the full/complete update that was actually written.
   */
  @Override
  public String modifyConfig(String registryId, String deviceId, Function<String, String> munger) {
    int retryCount = CONFIG_UPDATE_MAX_RETRIES;
    try {
      while (true) {
        try {
          Entry<Long, String> configPair = fetchConfig(registryId, deviceId);
          debug("Fetched config version %s for %s", configPair.getKey(), deviceId);
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

  @Override
  public void processMessage(Envelope envelope, Object message) {
    debug("Handing distributed update " + message.getClass().getSimpleName());
    if (message instanceof UdmiState udmiState) {
      updateRegistryRegions(udmiState.regions);
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
                backoffKey, isoConvert(until)));
      }
    } else {
      debug("Dropping message because registry backoff for %s", backoffKey);
    }
  }

  public void setProviderAffinity(String registryId, String deviceId, String providerId) {
    registryBackoffClear(registryId, deviceId);
  }

  /**
   * Update the cached registry regions with any incremental updates.
   */
  @Override
  public void updateRegistryRegions(Map<String, String> regions) {
    try {
      registryRegions.get().putAll(requireNonNull(regions, "additional regions is null"));
    } catch (Exception e) {
      throw new RuntimeException("Getting completed registry regions", e);
    }
  }

  private static class AbortLoopException extends RuntimeException {

    public AbortLoopException(String message) {
      super(message);
    }
  }

  Map<String, Object> parseOptions(IotAccess iotAccess) {
    String options = variableSubstitution(iotAccess.options);
    if (options == null) {
      return ImmutableMap.of();
    }
    String[] parts = options.split(",");
    return Arrays.stream(parts).map(String::trim).map(option -> option.split("=", 2))
        .collect(Collectors.toMap(x -> x[0], x -> x.length > 1 ? x[1] : true));
  }

}
