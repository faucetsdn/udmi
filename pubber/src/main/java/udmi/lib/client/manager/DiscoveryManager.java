package udmi.lib.client.manager;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.JsonUtil.getNowInstant;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static java.lang.Math.floorMod;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static udmi.schema.FamilyDiscoveryState.Phase.ACTIVE;
import static udmi.schema.FamilyDiscoveryState.Phase.PENDING;
import static udmi.schema.FamilyDiscoveryState.Phase.STOPPED;

import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import udmi.lib.ProtocolFamily;
import udmi.lib.intf.FamilyProvider;
import udmi.lib.intf.SubBlockManager;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvents;
import udmi.schema.DiscoveryState;
import udmi.schema.Enumerations;
import udmi.schema.Enumerations.Depth;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.FeatureDiscovery;
import udmi.schema.PointPointsetModel;

/**
 * Discovery client.
 */
public interface DiscoveryManager extends SubBlockManager {

  int SCAN_DURATION_SEC = 10;

  /**
   * Updates discovery enumeration.
   *
   * @param config Discovery Configuration.
   */
  default void updateDiscoveryEnumeration(DiscoveryConfig config) {
    Date enumerationGeneration = config.generation;
    if (enumerationGeneration == null) {
      getDiscoveryState().generation = null;
      return;
    }
    if (getDiscoveryState().generation != null
        && !enumerationGeneration.after(getDiscoveryState().generation)) {
      return;
    }
    getDiscoveryState().generation = enumerationGeneration;
    info(format("Discovery enumeration at %s", isoConvert(enumerationGeneration)));
    DiscoveryEvents discoveryEvent = new DiscoveryEvents();
    discoveryEvent.family = ProtocolFamily.IOT;
    discoveryEvent.generation = enumerationGeneration;
    Enumerations depths = config.enumerations;
    discoveryEvent.points = maybeEnumerate(depths.points, () -> enumeratePoints(getDeviceId()));
    discoveryEvent.features = maybeEnumerate(depths.features, this::getFeatures);
    discoveryEvent.families = maybeEnumerate(depths.families,
        () -> getDeviceManager().enumerateFamilies());
    getHost().publish(discoveryEvent);
  }

  Map<String, FeatureDiscovery> getFeatures();

  Map<String, PointPointsetModel> enumeratePoints(String deviceId);

  DeviceManager getDeviceManager();

  static boolean shouldEnumerate(Depth config, Depth threshold) {
    return config != null && config.ordinal() >= threshold.ordinal();
  }

  default <K, V> Map<K, V> maybeEnumerate(Depth depth, Supplier<Map<K, V>> supplier) {
    return ifTrueGet(shouldEnumerate(depth, Depth.ENTRIES), supplier);
  }

  /**
   * Updates discovery scan.
   *
   * @param raw Map of String -> FamilyDiscoveryConfig.
   */
  default void updateDiscoveryScan(Map<String, FamilyDiscoveryConfig> raw) {
    Map<String, FamilyDiscoveryConfig> families = ofNullable(raw).orElse(Map.of());
    ifNullThen(getDiscoveryState().families, () -> getDiscoveryState().families = new HashMap<>());

    List<String> toRemove = new ArrayList<>(getDiscoveryState().families.keySet());
    toRemove.removeIf(families::containsKey);
    toRemove.forEach(this::removeDiscoveryScan);
    families.keySet().forEach(this::scheduleDiscoveryScan);

    if (raw == null) {
      getDiscoveryState().families = null;
    }
  }

  /**
   * Schedules a discovery scan.
   *
   * @param family Family string.
   */
  default void scheduleDiscoveryScan(String family) {
    FamilyDiscoveryConfig familyDiscoveryConfig = getFamilyDiscoveryConfig(family);
    Date rawGeneration = familyDiscoveryConfig.generation;
    int interval = getScanInterval(family);
    if (rawGeneration == null && interval == 0) {
      cancelDiscoveryScan(family, null);
      removeDiscoveryScan(family);
      return;
    }

    Date startTime = getHost().getStartTime();
    Date configGeneration = ofNullable(rawGeneration).orElse(startTime);
    FamilyDiscoveryState familyDiscoveryState = ensureFamilyDiscoveryState(family);
    Date baseGeneration = ofNullable(familyDiscoveryState.generation).orElse(startTime);

    final Date startGeneration;
    if (interval > 0) {
      Instant now = Instant.now();
      long deltaSec = floorMod(configGeneration.getTime() / 1000 - now.getEpochSecond(), interval);
      startGeneration = Date.from(now.plusSeconds(deltaSec));
    } else if (configGeneration.before(baseGeneration)) {
      cancelDiscoveryScan(family, configGeneration);
      return;
    } else {
      startGeneration = configGeneration;
    }

    if (startGeneration.equals(baseGeneration)) {
      return;
    }

    info(format("Discovery scan generation %s pending at %s", family, isoConvert(startGeneration)));
    familyDiscoveryState.generation = startGeneration;
    familyDiscoveryState.phase = PENDING;
    updateState();

    scheduleFuture(startGeneration, () -> checkDiscoveryScan(family, startGeneration));
  }

  default FamilyDiscoveryConfig getFamilyDiscoveryConfig(String family) {
    return getDiscoveryConfig().families.get(family);
  }

  default void removeDiscoveryScan(String family) {
    FamilyDiscoveryState removed = getDiscoveryState().families.remove(family);
    ifNotNullThen(removed, was -> cancelDiscoveryScan(family, was.generation));
  }

  /**
   * Cancels discovery scan.
   *
   * @param family           family string.
   * @param configGeneration Config generation.
   */
  default void cancelDiscoveryScan(String family, Date configGeneration) {
    FamilyDiscoveryState familyDiscoveryState = getFamilyDiscoveryState(family);
    if (familyDiscoveryState != null) {
      info(format("Discovery scan %s phase %s as %s", family, STOPPED,
          isoConvert(configGeneration)));
      familyDiscoveryState.phase = STOPPED;
      familyDiscoveryState.generation = configGeneration;
      updateState();
    }
  }

  default FamilyDiscoveryState getFamilyDiscoveryState(String family) {
    return getDiscoveryState().families.get(family);
  }

  /**
   * Ensures family discovery state is set.
   *
   * @param family family string.
   * @return Family discovery state.
   */
  default FamilyDiscoveryState ensureFamilyDiscoveryState(String family) {
    if (getDiscoveryState().families == null) {
      // If there is no need for family state, then return a floating bucket for results.
      return new FamilyDiscoveryState();
    }
    return getDiscoveryState().families.computeIfAbsent(family, key -> new FamilyDiscoveryState());
  }

  /**
   * Checks and scan for discovery.
   *
   * @param family         protocol family.
   * @param scanGeneration scan generation.
   */
  default void checkDiscoveryScan(String family, Date scanGeneration) {
    try {
      FamilyDiscoveryState familyDiscoveryState = ensureFamilyDiscoveryState(family);
      ifTrueThen(familyDiscoveryState.phase == PENDING,
          () -> startDiscoveryScan(family, scanGeneration));
    } catch (Exception e) {
      throw new RuntimeException("While checking for discovery scan start", e);
    }
  }

  default void updateState() {
    updateState(ofNullable((Object) getDiscoveryState()).orElse(DiscoveryState.class));
  }

  /**
   * Update the discovery config.
   *
   * @param discovery Discovery config.
   */
  default void updateConfig(DiscoveryConfig discovery) {
    setDiscoveryConfig(discovery);
    if (discovery == null) {
      setDiscoveryState(null);
      updateState();
      return;
    }
    if (getDiscoveryState() == null) {
      setDiscoveryState(new DiscoveryState());
    }
    updateDiscoveryEnumeration(discovery);
    updateDiscoveryScan(discovery.families);
    updateState();
  }

  default int getScanInterval(String family) {
    return ofNullable(
        catchToNull(() -> getFamilyDiscoveryConfig(family).scan_interval_sec)).orElse(0);
  }

  /**
   * Retrieves the scan duration (in seconds) for the specified device family.
   *
   * @param family the name of the device family
   * @return the scan duration in seconds. either from the configuration or the default value
   */
  default int getScanDuration(String family) {
    return ofNullable(
        catchToNull(
            () -> getFamilyDiscoveryConfig(family).scan_duration_sec)
    ).orElse(SCAN_DURATION_SEC);
  }

  DiscoveryState getDiscoveryState();

  void setDiscoveryState(DiscoveryState discoveryState);

  DiscoveryConfig getDiscoveryConfig();

  void setDiscoveryConfig(DiscoveryConfig discoveryConfig);

  ScheduledFuture<?> scheduleFuture(Date startGeneration, Runnable runnable);

  /**
   * Starts a discovery scan.
   *
   * @param family         Discovery scan family.
   * @param scanGeneration Scan generation.
   */
  default void startDiscoveryScan(String family, Date scanGeneration) {
    info(format("Discovery scan starting %s generation %s", family, isoConvert(scanGeneration)));
    Date stopTime = Date.from(getNowInstant().plusSeconds(SCAN_DURATION_SEC));
    final FamilyDiscoveryState familyDiscoveryState = ensureFamilyDiscoveryState(family);
    AtomicInteger sendCount = new AtomicInteger();
    scheduleFuture(stopTime, () -> discoveryScanComplete(family, scanGeneration, sendCount));
    familyDiscoveryState.generation = scanGeneration;
    familyDiscoveryState.phase = ACTIVE;
    familyDiscoveryState.active_count = sendCount.get();
    sendMarkerDiscoveryEvent(family, scanGeneration, sendCount);
    updateState();
    startDiscoveryForFamily(family, scanGeneration, familyDiscoveryState, sendCount);
  }

  private void sendMarkerDiscoveryEvent(String family, Date scanGeneration,
      AtomicInteger sendCount) {
    DiscoveryEvents discoveryEvent = new DiscoveryEvents();
    discoveryEvent.event_no = -sendCount.getAndIncrement();
    info(format("Discovered %s active %d for %s", family, discoveryEvent.event_no,
        isoConvert(scanGeneration)));
    publishDiscoveryEvent(family, scanGeneration, null, discoveryEvent);
  }

  /**
   * Starts a discovery scan for given family.
   */
  default void startDiscoveryForFamily(String family, Date scanGeneration,
      FamilyDiscoveryState familyDiscoveryState, AtomicInteger sendCount) {
    String generation = isoConvert(scanGeneration);
    final Set<String> targets = ofNullable(
        catchToNull(() -> getDiscoveryConfig().families.get(family).addrs))
        .map(ImmutableSet::copyOf).orElse(null);
    info(format("Discovered %s starting %s (=? %s)", family, generation,
        isoConvert(familyDiscoveryState.generation)));

    FamilyDiscoveryConfig config = getFamilyDiscoveryConfig(family);
    config.depth = Optional.ofNullable(config.depth)
        .orElse(config.addrs != null ? Depth.DETAILS : Depth.ENTRIES);

    discoveryProvider(family).startScan(config, (deviceId, discoveryEvent) ->
        ifNotNullThen(discoveryEvent.addr, addr -> {
          if (ifNotNullGet(targets, t -> !t.contains(addr), false)) {
            info(format("Discovered %s device %s for %s skipped", family, addr, generation));
            return;
          }
          int activeCount = sendCount.getAndIncrement();
          familyDiscoveryState.active_count = activeCount;
          String network = discoveryEvent.network;
          info(format("Discovered %s device %s %s for %s as %s", family, network, addr, generation,
              activeCount));
          discoveryEvent.event_no = activeCount;
          publishDiscoveryEvent(family, scanGeneration, deviceId, discoveryEvent);
          updateState();
        }));
  }

  /**
   * Publish a discovery event with some basic augmentation.
   */
  default void publishDiscoveryEvent(String family, Date scanGeneration, String deviceId,
      DiscoveryEvents discoveryEvent) {
    discoveryEvent.family = family;
    discoveryEvent.generation = scanGeneration;
    postDiscoveryProcess(deviceId, discoveryEvent);
    getHost().publish(discoveryEvent);
  }

  /**
   * Discovery scan completed.
   */
  default void discoveryScanComplete(String family, Date scanGeneration, AtomicInteger sendCount) {
    try {
      FamilyDiscoveryState familyDiscoveryState = ensureFamilyDiscoveryState(family);
      info(format("Discovered %s stopping %s (=? %s)", family, isoConvert(scanGeneration),
          isoConvert(familyDiscoveryState.generation)));
      ifTrueThen(scanGeneration.equals(familyDiscoveryState.generation), () -> {
        discoveryProvider(family).stopScan();
        sendMarkerDiscoveryEvent(family, scanGeneration, sendCount);
        familyDiscoveryState.phase = STOPPED;
        updateState();
        scheduleDiscoveryScan(family);
      });
    } catch (Exception e) {
      throw new RuntimeException(format("While completing discovery scan %s", family), e);
    }
  }

  default FamilyProvider discoveryProvider(String family) {
    return getHost().getLocalnetProvider(family);
  }

  void postDiscoveryProcess(String deviceId, DiscoveryEvents discoveryEvents);
}
