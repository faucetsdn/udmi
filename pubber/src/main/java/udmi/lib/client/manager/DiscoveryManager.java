package udmi.lib.client.manager;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.getNow;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.instantNow;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static java.lang.Math.floorMod;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static udmi.lib.client.manager.DiscoveryScan.forFamily;
import static udmi.lib.client.manager.DiscoveryScan.forScan;
import static udmi.schema.Category.DISCOVERY_FAMILY_SCAN;
import static udmi.schema.FamilyDiscoveryState.Phase.ACTIVE;
import static udmi.schema.FamilyDiscoveryState.Phase.PENDING;
import static udmi.schema.FamilyDiscoveryState.Phase.STOPPED;

import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import udmi.lib.ProtocolFamily;
import udmi.lib.intf.FamilyProvider;
import udmi.lib.intf.SubBlockManager;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvents;
import udmi.schema.DiscoveryState;
import udmi.schema.Entry;
import udmi.schema.Enumerations;
import udmi.schema.Enumerations.Depth;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.FeatureDiscovery;
import udmi.schema.Level;
import udmi.schema.PointPointsetModel;

/**
 * Discovery client.
 */
public interface DiscoveryManager extends SubBlockManager {

  int SCAN_DURATION_SEC = 10;

  /**
   * Recorded timing state for a scan: the last slot it started (or failed busy at), any pending
   * busy-retry slot waiting in the future, and the currently scheduled timer task for this scan.
   */
  record ScanSchedule(Date lastRun, Date retryAt, ScheduledFuture<?> future) {
  }

  private static void cancelFuture(ScanSchedule schedule) {
    ifNotNullThen(schedule, s -> ifNotNullThen(s.future(), future -> future.cancel(false)));
  }

  /**
   * Updates discovery enumeration.
   *
   * @param config Discovery Configuration.
   */
  default void updateDiscoveryEnumeration(DiscoveryConfig config) {
    final DiscoveryEvents discoveryEvent;
    synchronized (getActiveScanFamilies()) {
      DiscoveryState state = getDiscoveryState();
      if (state == null) {
        return;
      }
      Date enumerationGeneration = config.generation;
      if (enumerationGeneration == null) {
        state.generation = null;
        return;
      }
      if (state.generation != null && !enumerationGeneration.after(state.generation)) {
        return;
      }
      state.generation = enumerationGeneration;
      info(format("Discovery enumeration at %s", isoConvert(enumerationGeneration)));
      discoveryEvent = new DiscoveryEvents();
      discoveryEvent.family = ProtocolFamily.IOT;
      discoveryEvent.generation = enumerationGeneration;
      Enumerations depths = config.enumerations;
      discoveryEvent.points = maybeEnumerate(depths.points, () -> enumeratePoints(getDeviceId()));
      discoveryEvent.features = maybeEnumerate(depths.features, this::getFeatures);
      discoveryEvent.families = maybeEnumerate(depths.families,
          () -> getDeviceManager().enumerateFamilies());
    }
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
   * Reconciles the scheduled scans with the {@code families} and {@code scans} config maps. Removed
   * scans, and named scans whose family changed, are stopped before anything is (re)scheduled.
   *
   * @param previous  Discovery config prior to this update, or null.
   * @param discovery Discovery config being applied.
   */
  default void updateDiscoveryScans(DiscoveryConfig previous, DiscoveryConfig discovery) {
    synchronized (getActiveScanFamilies()) {
      DiscoveryState state = getDiscoveryState();
      Map<String, FamilyDiscoveryConfig> families =
          ofNullable(discovery.families).orElse(Map.of());
      Map<String, FamilyDiscoveryConfig> scans = ofNullable(discovery.scans).orElse(Map.of());
      Map<String, FamilyDiscoveryConfig> previousScans =
          ofNullable(previous).map(config -> config.scans).orElse(Map.of());

      if (state.families != null) {
        state.families.keySet().stream().filter(not(families::containsKey)).toList()
            .forEach(family -> removeDiscoveryScan(forFamily(family)));
      }
      if (state.scans != null) {
        state.scans.keySet().stream()
            .filter(name -> !scans.containsKey(name)
                || familyChanged(previousScans.get(name), scans.get(name)))
            .toList()
            .forEach(name -> removeDiscoveryScan(forScan(name)));
      }

      state.families = discovery.families == null ? null
          : ofNullable(state.families).orElseGet(HashMap::new);
      state.scans = discovery.scans == null ? null
          : ofNullable(state.scans).orElseGet(HashMap::new);

      families.keySet().forEach(family -> scheduleDiscoveryScan(forFamily(family)));
      scans.keySet().forEach(name -> scheduleDiscoveryScan(forScan(name)));
    }
  }

  private static boolean familyChanged(FamilyDiscoveryConfig previous,
      FamilyDiscoveryConfig current) {
    return previous != null
        && (current == null || !Objects.equals(previous.family, current.family));
  }

  private boolean isScanConfigured(DiscoveryScan scan) {
    Map<String, FamilyDiscoveryConfig> scanMap = ifNotNullGet(getDiscoveryConfig(),
        config -> scan.named() ? config.scans : config.families);
    return scanMap != null && scanMap.containsKey(scan.name());
  }

  /**
   * Schedules a discovery scan.
   *
   * @param scan Scan to schedule.
   */
  default void scheduleDiscoveryScan(DiscoveryScan scan) {
    synchronized (getActiveScanFamilies()) {
      if (!isScanConfigured(scan)) {
        return;
      }
      String invalidReason = invalidScanReason(scan);
      if (invalidReason != null) {
        clearScanSchedule(scan);
        recordScanFailure(scan, null, invalidReason);
        return;
      }

      FamilyDiscoveryConfig scanConfig = getScanConfig(scan);
      Date rawGeneration = ifNotNullGet(scanConfig, config -> config.generation);
      int interval = getScanInterval(scan);
      if (rawGeneration == null && interval == 0) {
        removeDiscoveryScan(scan);
        return;
      }

      FamilyDiscoveryState currentState = getScanState(scan);
      if (currentState != null && currentState.phase == ACTIVE) {
        return;
      }

      Date startTime = getHost().getStartTime();
      Date configGeneration = ofNullable(rawGeneration).orElse(startTime);
      FamilyDiscoveryState scanState = ensureScanState(scan);
      ScanSchedule schedule = getScanSchedules().get(scan);
      Date recordedLastRun = ifNotNullGet(schedule, ScanSchedule::lastRun);
      Date lastRun = ofNullable(recordedLastRun).orElse(startTime);
      Instant now = getScanNow();

      final Date startGeneration;
      if (interval > 0) {
        startGeneration = nextPeriodicStart(configGeneration, interval, lastRun, now);
        if (schedule != null && startGeneration.equals(schedule.retryAt())) {
          return;
        }
        schedule = clearRetrySchedule(scan, schedule);
        if (scanState.phase == PENDING && startGeneration.equals(scanState.generation)
            && scanState.status == null) {
          return;
        }
      } else {
        schedule = clearRetrySchedule(scan, schedule);
        if (configGeneration.equals(scanState.generation) && scanState.status == null) {
          return;
        }
        if (!configGeneration.after(lastRun)) {
          if (!configGeneration.equals(scanState.generation)) {
            cancelDiscoveryScan(scan, configGeneration);
          }
          return;
        }
        startGeneration = configGeneration;
      }

      cancelFuture(schedule);
      info(format("Discovery scan generation %s pending at %s", scan, isoConvert(startGeneration)));
      scanState.generation = startGeneration;
      scanState.phase = PENDING;
      scanState.status = null;
      // Retain active_count across PENDING so periodic scan completion observers can read the
      // just-completed iteration's count until the next iteration starts.

      ScheduledFuture<?> future =
          scheduleFuture(startGeneration, () -> checkDiscoveryScan(scan, startGeneration));
      getScanSchedules().put(scan, new ScanSchedule(recordedLastRun, null, future));
    }
  }

  private ScanSchedule clearRetrySchedule(DiscoveryScan scan, ScanSchedule schedule) {
    if (schedule == null || schedule.retryAt() == null) {
      return schedule;
    }
    cancelFuture(schedule);
    ScanSchedule updated =
        schedule.lastRun() == null ? null : new ScanSchedule(schedule.lastRun(), null, null);
    if (updated == null) {
      getScanSchedules().remove(scan);
    } else {
      getScanSchedules().put(scan, updated);
    }
    return updated;
  }

  /**
   * Computes the start of the next periodic scan on the grid {@code generation + N * interval}.
   * Uses {@code max(now, lastRun)} so a slight early wakeup or backward clock step never returns
   * an already-executed slot. A future generation is the first slot; otherwise the latest grid
   * slot up to {@code refMs} runs if it is strictly after {@code lastRun} (which defaults to
   * device start time before any run, skipping pre-boot slots while running a fresh post-boot
   * generation at {@code N = 0}), and otherwise the next grid slot is scheduled.
   */
  static Date nextPeriodicStart(Date configGeneration, int intervalSec, Date lastRun, Instant now) {
    long generationMs = configGeneration.getTime();
    long lastRunMs = lastRun.getTime();
    long refMs = Math.max(now.toEpochMilli(), lastRunMs);
    if (generationMs > refMs) {
      return configGeneration;
    }
    long slotMs = intervalSec * 1000L;
    long lastSlotMs = refMs - floorMod(refMs - generationMs, slotMs);
    return new Date(lastSlotMs > lastRunMs ? lastSlotMs : lastSlotMs + slotMs);
  }

  default Instant getScanNow() {
    return instantNow();
  }

  /**
   * Returns why a scan entry can not be run, or null if it is valid. A {@code families} entry must
   * not name a family (the map key is the family), while a named scan must name a supported one.
   */
  default String invalidScanReason(DiscoveryScan scan) {
    FamilyDiscoveryConfig config = getScanConfig(scan);
    if (!scan.named()) {
      return config == null ? null : ifNotNullGet(config.family,
          value -> format("Discovery %s must not specify family %s", scan, value));
    }
    if (config == null || config.family == null) {
      return format("Discovery %s missing required family", scan);
    }
    if (discoveryProvider(config.family) == null) {
      return format("Discovery %s family %s is not supported", scan, config.family);
    }
    return null;
  }

  private boolean recordScanFailure(DiscoveryScan scan, Date generation, String message) {
    synchronized (getActiveScanFamilies()) {
      boolean wasActive = stopActiveScan(scan);
      cancelScanFuture(scan);
      FamilyDiscoveryState scanState = ensureScanState(scan);
      if (generation == null || !wasActive) {
        scanState.active_count = null;
      }
      if (scanState.phase == STOPPED && Objects.equals(scanState.generation, generation)
          && scanState.status != null && message.equals(scanState.status.message)) {
        return false;
      }
      info(format("Discovery scan %s failed: %s", scan, message));
      scanState.phase = STOPPED;
      scanState.generation = generation;
      scanState.status = new Entry();
      scanState.status.category = DISCOVERY_FAMILY_SCAN;
      scanState.status.level = Level.ERROR.value();
      scanState.status.message = message;
      scanState.status.timestamp = getNow();
      return true;
    }
  }

  /**
   * Returns the configured scan entry for the given scan, or null if absent.
   */
  default FamilyDiscoveryConfig getScanConfig(DiscoveryScan scan) {
    return ifNotNullGet(getDiscoveryConfig(), config ->
        ifNotNullGet(scan.named() ? config.scans : config.families,
            map -> map.get(scan.name())));
  }

  default String getScanFamily(DiscoveryScan scan) {
    return scan.named() ? ifNotNullGet(getScanConfig(scan), config -> config.family) : scan.name();
  }

  /**
   * Removes a scan's state entry, stopping its provider if the scan was active.
   *
   * @param scan Scan to remove.
   */
  default void removeDiscoveryScan(DiscoveryScan scan) {
    synchronized (getActiveScanFamilies()) {
      stopActiveScan(scan);
      clearScanSchedule(scan);
      FamilyDiscoveryState removed =
          ifNotNullGet(getScanStates(scan), states -> states.remove(scan.name()));
      ifNotNullThen(removed, was -> info(format("Discovery scan %s removed", scan)));
    }
  }

  private void clearScanSchedule(DiscoveryScan scan) {
    cancelFuture(getScanSchedules().remove(scan));
  }

  private void cancelScanFuture(DiscoveryScan scan) {
    getScanSchedules().computeIfPresent(scan, (key, cur) -> {
      cancelFuture(cur);
      return cur.lastRun() == null && cur.retryAt() == null
          ? null : new ScanSchedule(cur.lastRun(), cur.retryAt(), null);
    });
  }

  private void detachScanFuture(DiscoveryScan scan) {
    getScanSchedules().computeIfPresent(scan, (key, cur) ->
        cur.lastRun() == null && cur.retryAt() == null
            ? null : new ScanSchedule(cur.lastRun(), cur.retryAt(), null));
  }

  private void clearAllScanSchedules() {
    Map<DiscoveryScan, ScanSchedule> schedules = getScanSchedules();
    List<ScanSchedule> current = List.copyOf(schedules.values());
    schedules.clear();
    current.forEach(DiscoveryManager::cancelFuture);
  }

  private boolean stopActiveScan(DiscoveryScan scan) {
    Map<DiscoveryScan, String> active = getActiveScanFamilies();
    synchronized (active) {
      String removedFamily = active.remove(scan);
      ifNotNullThen(removedFamily, this::safeStopScan);
      return removedFamily != null;
    }
  }

  private void stopAllActiveScans() {
    Map<DiscoveryScan, String> active = getActiveScanFamilies();
    synchronized (active) {
      List<String> families = List.copyOf(active.values());
      active.clear();
      families.forEach(this::safeStopScan);
    }
  }

  private void safeStopScan(String family) {
    safeStopProvider(family, discoveryProvider(family));
  }

  private void safeStopProvider(String family, FamilyProvider provider) {
    if (provider == null) {
      return;
    }
    try {
      provider.stopScan();
    } catch (Exception e) {
      error(format("Error stopping discovery provider %s", family), e);
    }
  }

  /**
   * Stops all active scans and cancels scheduled scan futures during manager shutdown.
   */
  default void shutdownDiscoveryScans() {
    synchronized (getActiveScanFamilies()) {
      stopAllActiveScans();
      clearAllScanSchedules();
    }
  }

  /**
   * Families of the currently active scans, recorded when each scan starts. All discovery state
   * and active-scan transitions synchronize on the returned map instance (never held across
   * {@code getHost().publish} or {@code ManagerHost.update}).
   */
  Map<DiscoveryScan, String> getActiveScanFamilies();

  /**
   * Recorded per-scan timing state (last run slot and any pending busy retry). Must be safe for
   * concurrent access.
   */
  Map<DiscoveryScan, ScanSchedule> getScanSchedules();

  /**
   * Counter used to coalesce and serialize concurrent {@link #updateState()} snapshots without
   * blocking threads or inverting host locks.
   */
  AtomicInteger getStateUpdateCount();

  /**
   * Cancels discovery scan.
   *
   * @param scan             Scan to cancel.
   * @param configGeneration Config generation.
   */
  default void cancelDiscoveryScan(DiscoveryScan scan, Date configGeneration) {
    synchronized (getActiveScanFamilies()) {
      cancelScanFuture(scan);
      FamilyDiscoveryState scanState = getScanState(scan);
      if (scanState != null) {
        info(format("Discovery scan %s phase %s as %s", scan, STOPPED,
            isoConvert(configGeneration)));
        scanState.phase = STOPPED;
        scanState.generation = configGeneration;
        scanState.status = null;
        scanState.active_count = null;
      }
    }
  }

  default Map<String, FamilyDiscoveryState> getScanStates(DiscoveryScan scan) {
    return ifNotNullGet(getDiscoveryState(), state -> scan.named() ? state.scans : state.families);
  }

  default FamilyDiscoveryState getScanState(DiscoveryScan scan) {
    return ifNotNullGet(getScanStates(scan), states -> states.get(scan.name()));
  }

  /**
   * Ensures scan discovery state is set.
   *
   * @param scan Scan to look up.
   * @return Scan discovery state.
   */
  default FamilyDiscoveryState ensureScanState(DiscoveryScan scan) {
    DiscoveryState discoveryState = getDiscoveryState();
    if (discoveryState == null) {
      // If there is no need for scan state, then return a floating bucket for results.
      return new FamilyDiscoveryState();
    }
    Map<String, FamilyDiscoveryState> states = scan.named()
        ? (discoveryState.scans != null ? discoveryState.scans
            : (discoveryState.scans = new HashMap<>()))
        : (discoveryState.families != null ? discoveryState.families
            : (discoveryState.families = new HashMap<>()));
    return states.computeIfAbsent(scan.name(), key -> new FamilyDiscoveryState());
  }

  /**
   * Starts a pending scan if it is still pending for the given generation.
   *
   * @param scan           Scan to check.
   * @param scanGeneration scan generation.
   */
  default void checkDiscoveryScan(DiscoveryScan scan, Date scanGeneration) {
    try {
      startDiscoveryScan(scan, scanGeneration, true);
    } catch (Exception e) {
      throw new RuntimeException("While checking for discovery scan start", e);
    }
  }

  /**
   * Publishes an isolated snapshot of the current discovery state, coalescing concurrent updates
   * so an older snapshot can never overwrite a newer state.
   */
  default void updateState() {
    AtomicInteger pending = getStateUpdateCount();
    if (pending.getAndIncrement() != 0) {
      return;
    }
    boolean completed = false;
    try {
      do {
        pending.set(1);
        final Object snapshot;
        synchronized (getActiveScanFamilies()) {
          snapshot = ofNullable((Object) deepCopy(getDiscoveryState()))
              .orElse(DiscoveryState.class);
        }
        updateState(snapshot);
      } while (!pending.compareAndSet(1, 0));
      completed = true;
    } finally {
      if (!completed) {
        pending.set(0);
      }
    }
  }

  /**
   * Update the discovery config.
   *
   * @param discovery Discovery config.
   */
  default void updateConfig(DiscoveryConfig discovery) {
    synchronized (getActiveScanFamilies()) {
      final DiscoveryConfig previous = getDiscoveryConfig();
      setDiscoveryConfig(discovery);
      if (discovery == null) {
        stopAllActiveScans();
        clearAllScanSchedules();
        setDiscoveryState(null);
      } else {
        if (getDiscoveryState() == null) {
          setDiscoveryState(new DiscoveryState());
        }
        updateDiscoveryScans(previous, discovery);
      }
    }
    if (discovery != null) {
      updateDiscoveryEnumeration(discovery);
    }
    updateState();
  }

  default int getScanInterval(DiscoveryScan scan) {
    return ofNullable(ifNotNullGet(getScanConfig(scan), c -> c.scan_interval_sec)).orElse(0);
  }

  /**
   * Retrieves the scan duration (in seconds) for the specified scan.
   *
   * @param scan the scan to look up
   * @return the scan duration in seconds. either from the configuration or the default value
   */
  default int getScanDuration(DiscoveryScan scan) {
    return ofNullable(ifNotNullGet(getScanConfig(scan), c -> c.scan_duration_sec))
        .orElse(SCAN_DURATION_SEC);
  }

  DiscoveryState getDiscoveryState();

  void setDiscoveryState(DiscoveryState discoveryState);

  DiscoveryConfig getDiscoveryConfig();

  void setDiscoveryConfig(DiscoveryConfig discoveryConfig);

  ScheduledFuture<?> scheduleFuture(Date startGeneration, Runnable runnable);

  /**
   * Starts a discovery scan, unless another scan is already active for the same family.
   *
   * @param scan           Discovery scan.
   * @param scanGeneration Scan generation.
   */
  default void startDiscoveryScan(DiscoveryScan scan, Date scanGeneration) {
    startDiscoveryScan(scan, scanGeneration, false);
  }

  private void startDiscoveryScan(DiscoveryScan scan, Date scanGeneration, boolean requirePending) {
    final String family;
    final FamilyDiscoveryState scanState;
    final AtomicInteger sendCount = new AtomicInteger();
    final int markerEventNo;
    final boolean failedBusy;
    final boolean stateChanged;
    synchronized (getActiveScanFamilies()) {
      if (!isScanConfigured(scan) || invalidScanReason(scan) != null
          || getDiscoveryState() == null || getActiveScanFamilies().containsKey(scan)) {
        return;
      }
      FamilyDiscoveryState currentState = getScanState(scan);
      if (requirePending) {
        if (currentState == null || currentState.phase != PENDING
            || !scanGeneration.equals(currentState.generation)) {
          return;
        }
        detachScanFuture(scan);
      } else if (currentState != null && currentState.phase == ACTIVE) {
        return;
      }
      family = getScanFamily(scan);
      if (family == null) {
        return;
      }
      Optional<DiscoveryScan> busy = claimFamily(scan, family);
      if (busy.isPresent()) {
        failedBusy = true;
        stateChanged = failRetryableDiscoveryScan(scan, scanGeneration,
            format("Discovery family %s busy with %s", family, busy.get()));
        scanState = null;
        markerEventNo = 0;
      } else {
        failedBusy = false;
        stateChanged = true;
        cancelFuture(getScanSchedules().get(scan));
        info(format("Discovery scan starting %s generation %s", scan, isoConvert(scanGeneration)));
        Date stopTime = Date.from(getScanNow().plusSeconds(getScanDuration(scan)));
        scanState = ensureScanState(scan);
        ScheduledFuture<?> stopFuture = scheduleFuture(stopTime,
            () -> discoveryScanComplete(scan, family, scanGeneration, scanState, sendCount));
        getScanSchedules().put(scan, new ScanSchedule(scanGeneration, null, stopFuture));
        scanState.generation = scanGeneration;
        scanState.phase = ACTIVE;
        scanState.status = null;
        scanState.active_count = sendCount.get();
        markerEventNo = -sendCount.getAndIncrement();
      }
    }
    if (failedBusy) {
      ifTrueThen(stateChanged, this::updateState);
      return;
    }
    publishMarkerDiscoveryEvent(scan, family, scanGeneration, markerEventNo);
    updateState();
    startDiscoveryForFamily(scan, family, scanGeneration, scanState, sendCount);
  }

  /**
   * Records the scan as active for its family, unless a scan already holds that family.
   *
   * @return the scan holding the family, if busy.
   */
  private Optional<DiscoveryScan> claimFamily(DiscoveryScan scan, String family) {
    Map<DiscoveryScan, String> active = getActiveScanFamilies();
    synchronized (active) {
      Optional<DiscoveryScan> busy = active.entrySet().stream()
          .filter(entry -> entry.getValue().equals(family))
          .map(Map.Entry::getKey)
          .findFirst();
      ifTrueThen(busy.isEmpty(), () -> active.put(scan, family));
      return busy;
    }
  }

  private boolean failRetryableDiscoveryScan(DiscoveryScan scan, Date scanGeneration,
      String message) {
    int interval = getScanInterval(scan);
    Date retryGeneration = interval > 0
        ? Date.from(scanGeneration.toInstant().plusSeconds(interval))
        : null;
    boolean changed = recordScanFailure(scan, scanGeneration, message);
    ScheduledFuture<?> retryFuture = retryGeneration == null ? null : scheduleFuture(
        retryGeneration, () -> {
          final boolean shouldRetry;
          synchronized (getActiveScanFamilies()) {
            ScanSchedule current = getScanSchedules().get(scan);
            if (current != null && getScanState(scan) != null
                && retryGeneration.equals(current.retryAt())) {
              Date previousRun = current.lastRun();
              if (previousRun == null) {
                getScanSchedules().remove(scan);
              } else {
                getScanSchedules().put(scan, new ScanSchedule(previousRun, null, null));
              }
              scheduleDiscoveryScan(scan);
              shouldRetry = true;
            } else {
              shouldRetry = false;
            }
          }
          ifTrueThen(shouldRetry, this::updateState);
        });
    getScanSchedules().put(scan, new ScanSchedule(scanGeneration, retryGeneration, retryFuture));
    return changed;
  }

  private void publishMarkerDiscoveryEvent(DiscoveryScan scan, String family, Date scanGeneration,
      int markerEventNo) {
    DiscoveryEvents discoveryEvent = new DiscoveryEvents();
    discoveryEvent.event_no = markerEventNo;
    info(format("Discovered %s active %d for %s", scan, discoveryEvent.event_no,
        isoConvert(scanGeneration)));
    publishDiscoveryEvent(scan, family, scanGeneration, null, discoveryEvent);
  }

  private boolean isActiveRun(DiscoveryScan scan, String family, Date scanGeneration,
      FamilyDiscoveryState scanState) {
    return family.equals(getActiveScanFamilies().get(scan)) && getScanState(scan) == scanState
        && scanState.phase == ACTIVE && scanGeneration.equals(scanState.generation);
  }

  /**
   * Starts a discovery scan for given family.
   */
  default void startDiscoveryForFamily(DiscoveryScan scan, String family, Date scanGeneration,
      FamilyDiscoveryState scanState, AtomicInteger sendCount) {
    String generation = isoConvert(scanGeneration);
    final FamilyDiscoveryConfig config;
    synchronized (getActiveScanFamilies()) {
      FamilyDiscoveryConfig rawConfig = getScanConfig(scan);
      if (rawConfig == null) {
        return;
      }
      config = deepCopy(rawConfig);
    }
    final Set<String> targets = ofNullable(config.addrs).map(ImmutableSet::copyOf).orElse(null);
    info(format("Discovered %s starting %s (=? %s)", scan, generation,
        isoConvert(scanState.generation)));

    config.depth = Optional.ofNullable(config.depth)
        .orElse(config.addrs != null ? Depth.DETAILS : Depth.ENTRIES);

    FamilyProvider provider = discoveryProvider(family);
    if (provider == null) {
      info(format("Discovered %s has no provider, skipping provider scan", scan));
      return;
    }

    Map<DiscoveryScan, String> active = getActiveScanFamilies();
    BiConsumer<String, DiscoveryEvents> publisher = (deviceId, discoveryEvent) -> {
      String addr = discoveryEvent.addr;
      String network = discoveryEvent.network;
      if (addr == null && network == null) {
        return;
      }
      if (addr != null && !matchesTarget(targets, addr)) {
        info(format("Discovered %s device %s for %s skipped", scan, addr, generation));
        return;
      }
      final int activeCount;
      synchronized (active) {
        if (!isActiveRun(scan, family, scanGeneration, scanState)) {
          return;
        }
        activeCount = sendCount.getAndIncrement();
        scanState.active_count = activeCount;
      }
      info(format("Discovered %s device %s %s for %s as %s", scan, network, addr, generation,
          activeCount));
      discoveryEvent.event_no = activeCount;
      publishDiscoveryEvent(scan, family, scanGeneration, deviceId, discoveryEvent);
      updateState();
    };

    final boolean shouldStartProvider;
    synchronized (active) {
      shouldStartProvider = isActiveRun(scan, family, scanGeneration, scanState);
    }
    if (!shouldStartProvider) {
      return;
    }
    try {
      provider.startScan(config, publisher);
    } catch (Exception e) {
      error(format("Error starting discovery scan %s", scan), e);
      final boolean failedCurrent;
      synchronized (active) {
        if (isActiveRun(scan, family, scanGeneration, scanState)) {
          failedCurrent = failRetryableDiscoveryScan(scan, scanGeneration,
              format("Discovery %s failed to start: %s", scan, e.getMessage()));
        } else {
          failedCurrent = false;
          if (!active.containsValue(family)) {
            safeStopProvider(family, provider);
          }
        }
      }
      ifTrueThen(failedCurrent, this::updateState);
      return;
    }
    synchronized (active) {
      if (!isActiveRun(scan, family, scanGeneration, scanState) && !active.containsValue(family)) {
        safeStopProvider(family, provider);
      }
    }
  }

  /**
   * Checks whether a discovered address matches the configured target addresses or ranges.
   */
  static boolean matchesTarget(Set<String> targets, String addr) {
    if (targets == null) {
      return true;
    }
    if (targets.contains(addr)) {
      return true;
    }
    Long addrVal = catchToNull(() -> Long.parseLong(addr));
    if (addrVal == null) {
      return false;
    }
    return targets.stream().anyMatch(target -> {
      String[] parts = target.split("-", 2);
      if (parts.length == 2) {
        Long low = catchToNull(() -> Long.parseLong(parts[0]));
        Long high = catchToNull(() -> Long.parseLong(parts[1]));
        return low != null && high != null && addrVal >= low && addrVal <= high;
      }
      return false;
    });
  }

  /**
   * Publish a discovery event with some basic augmentation. Results of named scans carry the scan
   * name so that scans of the same family can be told apart.
   */
  default void publishDiscoveryEvent(DiscoveryScan scan, String family, Date scanGeneration,
      String deviceId, DiscoveryEvents discoveryEvent) {
    discoveryEvent.family = family;
    discoveryEvent.scan = scan.named() ? scan.name() : null;
    discoveryEvent.generation = scanGeneration;
    postDiscoveryProcess(deviceId, discoveryEvent);
    getHost().publish(discoveryEvent);
  }

  /**
   * Discovery scan completed. Ignored if the scan has since been removed or replaced, or has moved
   * on to another generation.
   */
  default void discoveryScanComplete(DiscoveryScan scan, String family, Date scanGeneration,
      FamilyDiscoveryState startedState, AtomicInteger sendCount) {
    try {
      final Integer endEventNo;
      Map<DiscoveryScan, String> active = getActiveScanFamilies();
      synchronized (active) {
        FamilyDiscoveryState scanState = getScanState(scan);
        info(format("Discovered %s stopping %s (=? %s)", scan, isoConvert(scanGeneration),
            ifNotNullGet(scanState, state -> isoConvert(state.generation))));
        if (scanState != startedState || scanState.phase != ACTIVE
            || !scanGeneration.equals(scanState.generation)) {
          return;
        }
        if (active.remove(scan, family)) {
          safeStopScan(family);
        }
        endEventNo = -sendCount.getAndIncrement();
      }
      publishMarkerDiscoveryEvent(scan, family, scanGeneration, endEventNo);
      final boolean completed;
      synchronized (active) {
        FamilyDiscoveryState scanState = getScanState(scan);
        completed = scanState == startedState && scanState.phase == ACTIVE
            && scanGeneration.equals(scanState.generation);
        if (completed) {
          detachScanFuture(scan);
          scanState.phase = STOPPED;
          scheduleDiscoveryScan(scan);
        }
      }
      ifTrueThen(completed, this::updateState);
    } catch (Exception e) {
      throw new RuntimeException(format("While completing discovery scan %s", scan), e);
    }
  }

  default FamilyProvider discoveryProvider(String family) {
    return getHost().getLocalnetProvider(family);
  }

  void postDiscoveryProcess(String deviceId, DiscoveryEvents discoveryEvents);
}
