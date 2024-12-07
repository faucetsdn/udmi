package udmi.lib.client;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNullElse;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static java.lang.Math.floorMod;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static udmi.schema.FamilyDiscoveryState.Phase.PENDING;
import static udmi.schema.FamilyDiscoveryState.Phase.STOPPED;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;
import udmi.schema.Depths;
import udmi.schema.Depths.Depth;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryState;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.PointPointsetModel;
import udmi.schema.RefDiscovery;

/**
 * Discovery client.
 */
public interface DiscoveryManager extends SubblockManager {


  static String getVendorRefKey(Map.Entry<String, PointPointsetModel> entry) {
    return ofNullable(entry.getValue().ref).orElse(entry.getKey());
  }

  /**
   * Get vendor ref value.
   */
  static RefDiscovery getVendorRefValue(Map.Entry<String, PointPointsetModel> entry) {
    RefDiscovery refDiscovery = new RefDiscovery();
    refDiscovery.possible_values = null;
    PointPointsetModel model = entry.getValue();
    refDiscovery.writable = model.writable;
    refDiscovery.units = model.units;
    refDiscovery.point = ifNotNullGet(model.ref, entry::getKey);
    return refDiscovery;
  }

  /**
   * Determines whether enumeration to a specific depth level is required.
   *
   * @param depth The depth level for which to determine if enumeration should occur.
   * @return True if enumeration is required at the specified depth level, false otherwise.
   */
  private static boolean shouldEnumerateTo(Depth depth) {
    return ifNullElse(depth, false, d -> switch (d) {
      case ENTRIES, DETAILS -> true;
      default -> false;
    });
  }

  default <K, V> Map<K, V> maybeEnumerate(Depths.Depth depth, Supplier<Map<K, V>> supplier) {
    return ifTrueGet(shouldEnumerateTo(depth), supplier);
  }

  /**
   * Updates discovery scan.
   *
   * @param raw Map of String -> FamilyDiscoveryConfig.
   */
  default void updateDiscoveryScan(Map<String, FamilyDiscoveryConfig> raw) {
    Map<String, FamilyDiscoveryConfig> families = ofNullable(raw).orElse(Map.of());
    ifNullThen(getDiscoveryState().families, () -> getDiscoveryState().families = new HashMap<>());

    List<String> toRemove = getDiscoveryState().families.keySet().stream()
        .filter(not(families::containsKey)).toList();
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
      return;
    }

    Date configGeneration = ofNullable(rawGeneration).orElse(getDeviceStartTime());
    FamilyDiscoveryState familyDiscoveryState = ensureFamilyDiscoveryState(family);
    Date baseGeneration = ofNullable(familyDiscoveryState.generation).orElse(getDeviceStartTime());

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

    info("Discovery scan generation " + family + " pending at " + isoConvert(startGeneration));
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
    return getDiscoveryState().families.computeIfAbsent(
        family, key -> new FamilyDiscoveryState());
  }

  /**
   * Checks and scan for discovery.
   *
   * @param family         family
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


  default boolean shouldEnumerate(String family) {
    return shouldEnumerateTo(getFamilyDiscoveryConfig(family).depth);
  }

  Date getDeviceStartTime();

  DiscoveryState getDiscoveryState();

  void setDiscoveryState(DiscoveryState discoveryState);

  DiscoveryConfig getDiscoveryConfig();

  void setDiscoveryConfig(DiscoveryConfig discoveryConfig);

  ScheduledFuture<?> scheduleFuture(Date startGeneration, Runnable runnable);

  void startDiscoveryScan(String family, Date scanGeneration);

  void updateDiscoveryEnumeration(DiscoveryConfig config);

}
