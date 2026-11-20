package udmi.lib.client.manager;

import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.getNow;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static java.lang.Math.floorMod;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static udmi.lib.client.manager.DiscoveryManager.SCAN_DURATION_SEC;
import static udmi.lib.client.manager.DiscoveryManager.matchesTarget;
import static udmi.lib.client.manager.DiscoveryManager.shouldEnumerate;
import static udmi.lib.client.manager.DiscoveryScan.forFamily;
import static udmi.lib.client.manager.DiscoveryScan.forScan;
import static udmi.schema.Category.DISCOVERY_FAMILY_SCAN;
import static udmi.schema.FamilyDiscoveryState.Phase.ACTIVE;
import static udmi.schema.FamilyDiscoveryState.Phase.PENDING;
import static udmi.schema.FamilyDiscoveryState.Phase.STOPPED;

import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import udmi.lib.ProtocolFamily;
import udmi.lib.intf.FamilyProvider;
import udmi.lib.intf.ManagerHost;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvents;
import udmi.schema.DiscoveryState;
import udmi.schema.Entry;
import udmi.schema.Enumerations;
import udmi.schema.Enumerations.Depth;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.Level;

/**
 * Coordinates scheduling, per-family mutual exclusion, and state reporting for discovery scans
 * across both {@code discovery.families} and named {@code discovery.scans}.
 *
 * <p>All internal state transitions synchronize on a private lock that is never held across
 * provider calls ({@link FamilyProvider#startScan}, {@link FamilyProvider#stopScan}), enumeration
 * callbacks, event publication, or {@code ManagerHost} state updates.
 */
public class DiscoveryScheduler {

  private enum RunStage {
    STARTING,
    RUNNING,
    STOPPING
  }

  private record ScanSchedule(Date lastRun, Date retryAt, ScheduledFuture<?> future) {
  }

  private record StartDecision(
      ActiveRun run,
      FamilyDiscoveryConfig config,
      int markerEventNo,
      boolean publishStateOnly) {
  }

  private static final class ActiveRun {

    final DiscoveryScan scan;
    final String family;
    final Date generation;
    final FamilyDiscoveryState state;
    final AtomicInteger sendCount;
    final FamilyProvider provider;
    RunStage stage = RunStage.STARTING;
    boolean completionRequested;

    ActiveRun(DiscoveryScan scan, String family, Date generation, FamilyDiscoveryState state,
        AtomicInteger sendCount, FamilyProvider provider) {
      this.scan = scan;
      this.family = family;
      this.generation = generation;
      this.state = state;
      this.sendCount = sendCount;
      this.provider = provider;
    }
  }

  private final DiscoveryManager manager;
  private final Object lock = new Object();
  private final Object statePublishLock = new Object();
  private final Map<String, ActiveRun> activeByFamily = new HashMap<>();
  private final Map<DiscoveryScan, ScanSchedule> scanSchedules = new HashMap<>();
  private final Set<DiscoveryScan> deferredScans = new LinkedHashSet<>();
  private final Map<String, String> configuredScanFamilies = new HashMap<>();
  private DiscoveryState discoveryState;
  private DiscoveryConfig discoveryConfig;
  private long configEpoch;
  private boolean shuttingDown;

  public DiscoveryScheduler(DiscoveryManager manager) {
    this.manager = requireNonNull(manager, "manager");
  }

  /**
   * Returns the current discovery state under the scheduler lock.
   */
  public DiscoveryState getDiscoveryState() {
    synchronized (lock) {
      return discoveryState;
    }
  }

  /**
   * Sets the current discovery state under the scheduler lock.
   */
  public void setDiscoveryState(DiscoveryState discoveryState) {
    synchronized (lock) {
      this.discoveryState = discoveryState;
    }
  }

  /**
   * Returns the current discovery config under the scheduler lock.
   */
  public DiscoveryConfig getDiscoveryConfig() {
    synchronized (lock) {
      return discoveryConfig;
    }
  }

  /**
   * Sets the current discovery config snapshot directly.
   */
  public void setDiscoveryConfig(DiscoveryConfig discoveryConfig) {
    synchronized (lock) {
      this.discoveryConfig = discoveryConfig;
      recordConfiguredScanFamiliesLocked(discoveryConfig);
    }
  }

  boolean hasActiveScans() {
    synchronized (lock) {
      return !activeByFamily.isEmpty();
    }
  }

  boolean isFamilyActive(String family) {
    synchronized (lock) {
      return activeByFamily.containsKey(family);
    }
  }

  boolean hasScanSchedules() {
    synchronized (lock) {
      return !scanSchedules.isEmpty();
    }
  }

  /**
   * Applies an updated discovery configuration, stopping removed or replaced scans outside the
   * scheduler lock before scheduling new scans and publishing state.
   */
  public void updateConfig(DiscoveryConfig discovery) {
    List<ActiveRun> runsToStop = new ArrayList<>();
    Set<String> affectedFamilies = new LinkedHashSet<>();
    final long myEpoch;
    final boolean scheduledInFirstPass;

    synchronized (lock) {
      if (shuttingDown) {
        return;
      }
      this.discoveryConfig = discovery;
      myEpoch = ++this.configEpoch;
      if (discovery == null) {
        detachAllActiveScansLocked(runsToStop);
        deferredScans.clear();
        configuredScanFamilies.clear();
        clearAllScanSchedulesLocked();
        this.discoveryState = null;
        scheduledInFirstPass = true;
      } else {
        if (this.discoveryState == null) {
          this.discoveryState = new DiscoveryState();
        }
        preStopObsoleteScansLocked(discovery, runsToStop, affectedFamilies);
        recordConfiguredScanFamiliesLocked(discovery);
        if (runsToStop.isEmpty()) {
          reconcileSchedulesLocked(discovery);
          scheduledInFirstPass = true;
        } else {
          scheduledInFirstPass = false;
        }
      }
    }

    runsToStop.forEach(run -> safeStopProvider(run.family, run.provider));

    if (!runsToStop.isEmpty() || !affectedFamilies.isEmpty()) {
      synchronized (lock) {
        runsToStop.forEach(run -> activeByFamily.remove(run.family, run));
        if (!scheduledInFirstPass && !shuttingDown && this.configEpoch == myEpoch
            && this.discoveryConfig != null) {
          reconcileSchedulesLocked(this.discoveryConfig);
        }
      }
    }

    if (discovery != null) {
      updateDiscoveryEnumeration(discovery, myEpoch);
    }
    updateState();
    affectedFamilies.forEach(this::startDeferredScan);
  }

  /**
   * Stops all active scans and cancels scheduled timers during manager shutdown.
   */
  public void shutdown() {
    List<ActiveRun> runsToStop = new ArrayList<>();
    synchronized (lock) {
      shuttingDown = true;
      deferredScans.clear();
      detachAllActiveScansLocked(runsToStop);
      clearAllScanSchedulesLocked();
    }
    runsToStop.forEach(run -> safeStopProvider(run.family, run.provider));
    synchronized (lock) {
      runsToStop.forEach(run -> activeByFamily.remove(run.family, run));
    }
  }

  /**
   * Publishes an isolated snapshot of the current discovery state while holding the host state
   * lock, ensuring callers observe updated discovery state synchronously and older snapshots
   * never overwrite newer state.
   */
  public void updateState() {
    ManagerHost host = manager.getHost();
    if (host == null) {
      publishStateLocked();
    } else {
      host.withStateLock(this::publishStateLocked);
    }
  }

  private void updateStateNonBlocking() {
    ManagerHost host = manager.getHost();
    if (host == null) {
      publishStateLocked();
      return;
    }
    if (!host.tryWithStateLock(this::publishStateLocked)) {
      synchronized (lock) {
        tryScheduleFutureLocked(Date.from(manager.getScanNow()), this::updateState);
      }
    }
  }

  private void publishStateLocked() {
    synchronized (statePublishLock) {
      final Object snapshot;
      synchronized (lock) {
        snapshot = ofNullable((Object) deepCopy(discoveryState)).orElse(DiscoveryState.class);
      }
      manager.updateState(snapshot);
    }
  }

  /**
   * Starts a pending scan if it is still pending for the given generation.
   */
  public void checkDiscoveryScan(DiscoveryScan scan, Date scanGeneration) {
    try {
      executeStartDecision(prepareScanStart(scan, scanGeneration, true));
    } catch (Exception e) {
      throw new RuntimeException("While checking for discovery scan start", e);
    }
  }

  /**
   * Starts a discovery scan, deferring sporadic scans or failing periodic slots if its family is
   * already active.
   */
  public void startDiscoveryScan(DiscoveryScan scan, Date scanGeneration) {
    executeStartDecision(prepareScanStart(scan, scanGeneration, false));
  }

  /**
   * Computes the start of the next periodic scan on the grid {@code generation + N * interval}.
   * Uses {@code max(now, lastRun)} so a slight early wakeup or backward clock step never returns
   * an already-executed slot. A future generation is the first slot; otherwise the latest grid
   * slot up to {@code refMs} runs if it is strictly after {@code lastRun} (which defaults to
   * device start time before any run, skipping pre-boot slots while running a fresh post-boot
   * generation at {@code N = 0}), and otherwise the next grid slot is scheduled.
   */
  public static Date nextPeriodicStart(
      Date configGeneration, int intervalSec, Date lastRun, Instant now) {
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

  private void updateDiscoveryEnumeration(DiscoveryConfig config, long epoch) {
    final Date enumerationGeneration;
    final Enumerations depths;
    synchronized (lock) {
      if (shuttingDown || discoveryState == null || this.configEpoch != epoch) {
        return;
      }
      enumerationGeneration = config.generation;
      if (enumerationGeneration == null) {
        discoveryState.generation = null;
        return;
      }
      if (discoveryState.generation != null
          && !enumerationGeneration.after(discoveryState.generation)) {
        return;
      }
      discoveryState.generation = enumerationGeneration;
      depths = config.enumerations;
    }
    manager.info(format("Discovery enumeration at %s", isoConvert(enumerationGeneration)));
    DiscoveryEvents discoveryEvent = new DiscoveryEvents();
    discoveryEvent.family = ProtocolFamily.IOT;
    discoveryEvent.generation = enumerationGeneration;
    if (depths != null) {
      discoveryEvent.points = maybeEnumerate(depths.points,
          () -> manager.enumeratePoints(manager.getDeviceId()));
      discoveryEvent.features = maybeEnumerate(depths.features, manager::getFeatures);
      discoveryEvent.families = maybeEnumerate(depths.families,
          () -> manager.getDeviceManager().enumerateFamilies());
    }
    manager.getHost().publish(discoveryEvent);
  }

  private static <K, V> Map<K, V> maybeEnumerate(Depth depth, Supplier<Map<K, V>> supplier) {
    return ifTrueGet(shouldEnumerate(depth, Depth.ENTRIES), supplier);
  }

  private void preStopObsoleteScansLocked(DiscoveryConfig discovery, List<ActiveRun> runsToStop,
      Set<String> affectedFamilies) {
    Map<String, FamilyDiscoveryConfig> families =
        ofNullable(discovery.families).orElse(Map.of());
    Map<String, FamilyDiscoveryConfig> scans = ofNullable(discovery.scans).orElse(Map.of());

    if (discoveryState.families != null) {
      discoveryState.families.keySet().stream()
          .filter(not(families::containsKey))
          .toList()
          .forEach(family -> removeDiscoveryScanLocked(
              forFamily(family), runsToStop, affectedFamilies));
    }
    if (discoveryState.scans != null) {
      discoveryState.scans.keySet().stream()
          .filter(name -> !scans.containsKey(name)
              || scanFamilyChangedLocked(name, scans.get(name)))
          .toList()
          .forEach(name -> removeDiscoveryScanLocked(
              forScan(name), runsToStop, affectedFamilies));
    }
    if (discovery.families == null) {
      discoveryState.families = null;
    }
    if (discovery.scans == null) {
      discoveryState.scans = null;
    }

    families.keySet().forEach(family -> preStopIfUnrunnableLocked(
        forFamily(family), runsToStop, affectedFamilies));
    scans.keySet().forEach(name -> preStopIfUnrunnableLocked(
        forScan(name), runsToStop, affectedFamilies));
  }

  private void preStopIfUnrunnableLocked(DiscoveryScan scan, List<ActiveRun> runsToStop,
      Set<String> affectedFamilies) {
    if (!isScanActiveLocked(scan)) {
      return;
    }
    FamilyDiscoveryConfig scanConfig = getScanConfigLocked(scan);
    Date rawGeneration = ifNotNullGet(scanConfig, config -> config.generation);
    if (invalidScanReasonLocked(scan) != null || rawGeneration == null) {
      detachActiveScanLocked(scan, runsToStop, affectedFamilies);
    }
  }

  private void reconcileSchedulesLocked(DiscoveryConfig discovery) {
    Map<String, FamilyDiscoveryConfig> families =
        ofNullable(discovery.families).orElse(Map.of());
    Map<String, FamilyDiscoveryConfig> scans = ofNullable(discovery.scans).orElse(Map.of());

    discoveryState.families = discovery.families == null ? null
        : ofNullable(discoveryState.families).orElseGet(HashMap::new);
    discoveryState.scans = discovery.scans == null ? null
        : ofNullable(discoveryState.scans).orElseGet(HashMap::new);

    families.keySet().forEach(family -> scheduleDiscoveryScanLocked(forFamily(family)));
    scans.keySet().forEach(name -> scheduleDiscoveryScanLocked(forScan(name)));
  }

  private boolean scanFamilyChangedLocked(String name, FamilyDiscoveryConfig current) {
    if (!configuredScanFamilies.containsKey(name)) {
      return false;
    }
    String currentFamily = ifNotNullGet(current, config -> config.family);
    return !Objects.equals(configuredScanFamilies.get(name), currentFamily);
  }

  private void recordConfiguredScanFamiliesLocked(DiscoveryConfig discovery) {
    configuredScanFamilies.clear();
    if (discovery != null && discovery.scans != null) {
      discovery.scans.forEach((name, config) ->
          configuredScanFamilies.put(name, ifNotNullGet(config, c -> c.family)));
    }
  }

  private void scheduleDiscoveryScanLocked(DiscoveryScan scan) {
    if (shuttingDown || !isScanConfiguredLocked(scan)) {
      return;
    }
    String invalidReason = invalidScanReasonLocked(scan);
    if (invalidReason != null) {
      deferredScans.remove(scan);
      clearScanScheduleLocked(scan);
      recordScanFailureLocked(scan, null, invalidReason);
      return;
    }

    FamilyDiscoveryConfig scanConfig = getScanConfigLocked(scan);
    Date configGeneration = ifNotNullGet(scanConfig, config -> config.generation);
    if (configGeneration == null) {
      removeDiscoveryScanLocked(scan, null, null);
      return;
    }

    FamilyDiscoveryState currentState = getScanStateLocked(scan);
    if (currentState != null && currentState.phase == ACTIVE) {
      return;
    }

    int interval = getScanIntervalLocked(scan);
    Date startTime = manager.getHost().getStartTime();
    FamilyDiscoveryState scanState = ensureScanStateLocked(scan);
    ScanSchedule schedule = scanSchedules.get(scan);
    Date recordedLastRun = ifNotNullGet(schedule, s -> s.lastRun());
    Date lastRun = ofNullable(recordedLastRun).orElse(startTime);
    Instant now = manager.getScanNow();

    final Date startGeneration;
    if (interval > 0) {
      deferredScans.remove(scan);
      startGeneration = nextPeriodicStart(configGeneration, interval, lastRun, now);
      if (schedule != null && startGeneration.equals(schedule.retryAt())
          && scanState.generation != null && !configGeneration.after(scanState.generation)) {
        return;
      }
      schedule = clearRetryScheduleLocked(scan, schedule);
      if (scanState.phase == PENDING && hasArmedFuture(schedule)
          && startGeneration.equals(scanState.generation) && scanState.status == null) {
        return;
      }
    } else {
      schedule = clearRetryScheduleLocked(scan, schedule);
      if (configGeneration.equals(scanState.generation) && scanState.status == null
          && (scanState.phase != PENDING || hasArmedFuture(schedule)
              || deferredScans.contains(scan))) {
        return;
      }
      if (!configGeneration.after(lastRun)) {
        if (!configGeneration.equals(scanState.generation)) {
          cancelDiscoveryScanLocked(scan, configGeneration);
        }
        return;
      }
      startGeneration = configGeneration;
    }

    Optional<ScheduledFuture<?>> future = tryScheduleFutureLocked(
        startGeneration, () -> checkDiscoveryScan(scan, startGeneration));
    if (future.isEmpty()) {
      return;
    }

    deferredScans.remove(scan);
    cancelFuture(schedule);
    manager.info(format("Discovery scan generation %s pending at %s", scan,
        isoConvert(startGeneration)));
    scanState.generation = startGeneration;
    scanState.phase = PENDING;
    scanState.status = null;
    scanSchedules.put(scan, new ScanSchedule(recordedLastRun, null, future.get()));
  }

  private Optional<ScheduledFuture<?>> tryScheduleFutureLocked(Date when, Runnable task) {
    if (shuttingDown) {
      return Optional.empty();
    }
    try {
      return Optional.of(requireNonNull(manager.scheduleFuture(when, task)));
    } catch (RejectedExecutionException e) {
      manager.debug(format("Skipping scheduled discovery task at %s after shutdown",
          isoConvert(when)));
      return Optional.empty();
    }
  }

  private ScanSchedule clearRetryScheduleLocked(DiscoveryScan scan, ScanSchedule schedule) {
    if (schedule == null || schedule.retryAt() == null) {
      return schedule;
    }
    cancelFuture(schedule);
    ScanSchedule updated =
        schedule.lastRun() == null ? null : new ScanSchedule(schedule.lastRun(), null, null);
    if (updated == null) {
      scanSchedules.remove(scan);
    } else {
      scanSchedules.put(scan, updated);
    }
    return updated;
  }

  private String invalidScanReasonLocked(DiscoveryScan scan) {
    FamilyDiscoveryConfig config = getScanConfigLocked(scan);
    if (!scan.named()) {
      if (config == null) {
        return null;
      }
      if (config.family != null) {
        return format("Discovery %s must not specify family %s", scan, config.family);
      }
    } else {
      if (config == null || config.family == null) {
        return format("Discovery %s missing required family", scan);
      }
      if (manager.discoveryProvider(config.family) == null) {
        return format("Discovery %s family %s is not supported", scan, config.family);
      }
    }
    if (config.scan_interval_sec != null && config.scan_interval_sec < 0) {
      return format("Discovery %s has negative scan_interval_sec %d", scan,
          config.scan_interval_sec);
    }
    if (config.scan_duration_sec != null && config.scan_duration_sec < 0) {
      return format("Discovery %s has negative scan_duration_sec %d", scan,
          config.scan_duration_sec);
    }
    if (config.addrs != null && config.addrs.stream().anyMatch(Objects::isNull)) {
      return format("Discovery %s has null target address", scan);
    }
    return null;
  }

  private boolean recordScanFailureLocked(DiscoveryScan scan, Date generation, String message) {
    boolean wasActive = detachActiveScanLocked(scan, null, null);
    deferredScans.remove(scan);
    cancelScanFutureLocked(scan);
    FamilyDiscoveryState scanState = ensureScanStateLocked(scan);
    if (generation == null || !wasActive) {
      scanState.active_count = null;
    }
    if (scanState.phase == STOPPED && Objects.equals(scanState.generation, generation)
        && scanState.status != null && message.equals(scanState.status.message)) {
      return false;
    }
    manager.info(format("Discovery scan %s failed: %s", scan, message));
    scanState.phase = STOPPED;
    scanState.generation = generation;
    scanState.status = new Entry();
    scanState.status.category = DISCOVERY_FAMILY_SCAN;
    scanState.status.level = Level.ERROR.value();
    scanState.status.message = message;
    scanState.status.timestamp = getNow();
    return true;
  }

  private void cancelDiscoveryScanLocked(DiscoveryScan scan, Date configGeneration) {
    deferredScans.remove(scan);
    cancelScanFutureLocked(scan);
    FamilyDiscoveryState scanState = getScanStateLocked(scan);
    if (scanState != null) {
      manager.info(format("Discovery scan %s phase %s as %s", scan, STOPPED,
          isoConvert(configGeneration)));
      scanState.phase = STOPPED;
      scanState.generation = configGeneration;
      scanState.status = null;
      scanState.active_count = null;
    }
  }

  private void removeDiscoveryScanLocked(DiscoveryScan scan, List<ActiveRun> runsToStop,
      Set<String> affectedFamilies) {
    detachActiveScanLocked(scan, runsToStop, affectedFamilies);
    deferredScans.remove(scan);
    clearScanScheduleLocked(scan);
    FamilyDiscoveryState removed =
        ifNotNullGet(getScanStatesLocked(scan), states -> states.remove(scan.name()));
    ifNotNullThen(removed, was -> manager.info(format("Discovery scan %s removed", scan)));
  }

  private boolean detachActiveScanLocked(DiscoveryScan scan, List<ActiveRun> runsToStop,
      Set<String> affectedFamilies) {
    Optional<ActiveRun> match = activeByFamily.values().stream()
        .filter(run -> run.scan.equals(scan))
        .findFirst();
    if (match.isEmpty()) {
      return false;
    }
    ActiveRun run = match.get();
    ifNotNullThen(affectedFamilies, families -> families.add(run.family));
    if (run.stage == RunStage.RUNNING) {
      run.stage = RunStage.STOPPING;
      if (runsToStop != null) {
        runsToStop.add(run);
      } else {
        activeByFamily.remove(run.family, run);
      }
    } else if (run.stage == RunStage.STARTING) {
      run.stage = RunStage.STOPPING;
    }
    return true;
  }

  private void detachAllActiveScansLocked(List<ActiveRun> runsToStop) {
    for (ActiveRun run : activeByFamily.values()) {
      if (run.stage == RunStage.RUNNING) {
        run.stage = RunStage.STOPPING;
        runsToStop.add(run);
      } else if (run.stage == RunStage.STARTING) {
        run.stage = RunStage.STOPPING;
      }
    }
  }

  private boolean isScanActiveLocked(DiscoveryScan scan) {
    return activeByFamily.values().stream().anyMatch(run -> run.scan.equals(scan));
  }

  private StartDecision prepareScanStart(DiscoveryScan scan, Date scanGeneration,
      boolean requirePending) {
    synchronized (lock) {
      if (shuttingDown || !isScanConfiguredLocked(scan) || invalidScanReasonLocked(scan) != null
          || discoveryState == null || isScanActiveLocked(scan)) {
        return null;
      }
      FamilyDiscoveryState currentState = getScanStateLocked(scan);
      if (requirePending) {
        if (currentState == null || currentState.phase != PENDING
            || !scanGeneration.equals(currentState.generation)) {
          return null;
        }
        detachScanFutureLocked(scan);
      } else if (currentState != null && currentState.phase == ACTIVE) {
        return null;
      }
      String family = getScanFamilyLocked(scan);
      if (family == null) {
        return null;
      }
      ActiveRun busy = activeByFamily.get(family);
      if (busy != null) {
        if (getScanIntervalLocked(scan) <= 0) {
          FamilyDiscoveryState scanState = ensureScanStateLocked(scan);
          cancelScanFutureLocked(scan);
          final boolean changed = scanState.phase != PENDING
              || !Objects.equals(scanState.generation, scanGeneration)
              || scanState.status != null;
          scanState.generation = scanGeneration;
          scanState.phase = PENDING;
          scanState.status = null;
          if (deferredScans.add(scan)) {
            manager.info(format("Discovery scan %s deferred while family %s busy with %s",
                scan, family, busy.scan));
          }
          return new StartDecision(null, null, 0, changed);
        }
        boolean changed = failRetryablePeriodicScanLocked(scan, scanGeneration,
            format("Discovery family %s busy with %s", family, busy.scan));
        return new StartDecision(null, null, 0, changed);
      }
      return claimAndPrepareRunLocked(scan, family, scanGeneration);
    }
  }

  private StartDecision claimAndPrepareRunLocked(DiscoveryScan scan, String family,
      Date scanGeneration) {
    FamilyDiscoveryConfig rawConfig = getScanConfigLocked(scan);
    if (rawConfig == null) {
      return null;
    }
    FamilyProvider provider = manager.discoveryProvider(family);
    Date stopTime = Date.from(manager.getScanNow().plusSeconds(getScanDurationLocked(scan)));
    FamilyDiscoveryState scanState = ensureScanStateLocked(scan);
    AtomicInteger sendCount = new AtomicInteger();
    ActiveRun run = new ActiveRun(scan, family, scanGeneration, scanState, sendCount, provider);
    Optional<ScheduledFuture<?>> stopFuture =
        tryScheduleFutureLocked(stopTime, () -> discoveryScanComplete(run));
    if (stopFuture.isEmpty()) {
      return null;
    }
    deferredScans.remove(scan);
    cancelFuture(scanSchedules.get(scan));
    manager.info(format("Discovery scan starting %s generation %s", scan,
        isoConvert(scanGeneration)));
    activeByFamily.put(family, run);
    scanSchedules.put(scan, new ScanSchedule(scanGeneration, null, stopFuture.get()));
    scanState.generation = scanGeneration;
    scanState.phase = ACTIVE;
    scanState.status = null;
    scanState.active_count = sendCount.get();
    int markerEventNo = -sendCount.getAndIncrement();
    return new StartDecision(run, deepCopy(rawConfig), markerEventNo, false);
  }

  private StartDecision pollDeferredStartLocked(String family) {
    if (shuttingDown || discoveryConfig == null || discoveryState == null
        || activeByFamily.containsKey(family)) {
      return null;
    }
    Iterator<DiscoveryScan> iterator = deferredScans.iterator();
    while (iterator.hasNext()) {
      DiscoveryScan candidate = iterator.next();
      if (!isScanConfiguredLocked(candidate) || invalidScanReasonLocked(candidate) != null
          || getScanIntervalLocked(candidate) > 0) {
        iterator.remove();
        continue;
      }
      if (!family.equals(getScanFamilyLocked(candidate))) {
        continue;
      }
      FamilyDiscoveryState state = getScanStateLocked(candidate);
      if (state == null || state.phase != PENDING || state.generation == null) {
        iterator.remove();
        continue;
      }
      iterator.remove();
      return claimAndPrepareRunLocked(candidate, family, state.generation);
    }
    return null;
  }

  private void startDeferredScan(String family) {
    final StartDecision next;
    synchronized (lock) {
      next = pollDeferredStartLocked(family);
    }
    executeStartDecision(next);
  }

  private void executeStartDecision(StartDecision decision) {
    if (decision == null) {
      return;
    }
    if (decision.run() == null) {
      ifTrueThen(decision.publishStateOnly(), this::updateState);
      return;
    }
    ActiveRun run = decision.run();
    try {
      publishMarkerDiscoveryEvent(run.scan, run.family, run.generation, decision.markerEventNo());
      updateState();
      startProviderForRun(run, decision.config());
    } catch (RuntimeException | Error e) {
      abortStartingRunAfterException(run, e);
      throw e;
    }
  }

  private void abortStartingRunAfterException(ActiveRun run, Throwable cause) {
    final boolean aborted;
    final boolean stateChanged;
    synchronized (lock) {
      if (activeByFamily.get(run.family) == run && run.stage == RunStage.STARTING) {
        aborted = true;
        run.stage = RunStage.STOPPING;
        activeByFamily.remove(run.family, run);
        if (isActiveStateLocked(run)) {
          stateChanged = failScanStartErrorLocked(run.scan, run.generation,
              format("Discovery %s failed to start: %s", run.scan, cause.getMessage()));
        } else {
          cancelScanFutureLocked(run.scan);
          stateChanged = false;
        }
      } else {
        aborted = false;
        stateChanged = false;
      }
    }
    if (!aborted) {
      return;
    }
    try {
      ifTrueThen(stateChanged, this::updateState);
    } catch (RuntimeException | Error suppressed) {
      cause.addSuppressed(suppressed);
    }
    try {
      startDeferredScan(run.family);
    } catch (RuntimeException | Error suppressed) {
      cause.addSuppressed(suppressed);
    }
  }

  private void startProviderForRun(ActiveRun run, FamilyDiscoveryConfig config) {
    String generation = isoConvert(run.generation);
    final Set<String> targets = ofNullable(config.addrs).map(ImmutableSet::copyOf).orElse(null);
    manager.info(format("Discovered %s starting %s (=? %s)", run.scan, generation,
        isoConvert(run.state.generation)));

    config.depth = Optional.ofNullable(config.depth)
        .orElse(config.addrs != null ? Depth.DETAILS : Depth.ENTRIES);

    if (run.provider == null) {
      manager.info(format("Discovered %s has no provider, skipping provider scan", run.scan));
      final boolean completeNow;
      final boolean checkDeferred;
      synchronized (lock) {
        if (activeByFamily.get(run.family) == run) {
          if (run.stage == RunStage.STARTING && isActiveStateLocked(run)) {
            run.stage = RunStage.RUNNING;
            completeNow = run.completionRequested;
            checkDeferred = false;
          } else {
            completeNow = false;
            activeByFamily.remove(run.family, run);
            checkDeferred = true;
          }
        } else {
          completeNow = false;
          checkDeferred = false;
        }
      }
      if (completeNow) {
        discoveryScanComplete(run);
      } else if (checkDeferred) {
        startDeferredScan(run.family);
      }
      return;
    }

    BiConsumer<String, DiscoveryEvents> publisher = (deviceId, discoveryEvent) -> {
      String addr = discoveryEvent.addr;
      String network = discoveryEvent.network;
      if (addr == null && network == null) {
        return;
      }
      if (addr != null && !matchesTarget(targets, addr)) {
        manager.info(format("Discovered %s device %s for %s skipped", run.scan, addr, generation));
        return;
      }
      final int activeCount;
      synchronized (lock) {
        if (!isActiveRunLocked(run)) {
          return;
        }
        activeCount = run.sendCount.getAndIncrement();
        run.state.active_count = activeCount;
      }
      manager.info(format("Discovered %s device %s %s for %s as %s", run.scan, network, addr,
          generation, activeCount));
      discoveryEvent.event_no = activeCount;
      publishDiscoveryEvent(run.scan, run.family, run.generation, deviceId, discoveryEvent);
      updateStateNonBlocking();
    };

    final boolean shouldStart;
    final boolean checkDeferredOnAbort;
    synchronized (lock) {
      if (activeByFamily.get(run.family) == run && run.stage == RunStage.STARTING
          && isActiveStateLocked(run)) {
        shouldStart = true;
        checkDeferredOnAbort = false;
      } else {
        shouldStart = false;
        checkDeferredOnAbort = activeByFamily.remove(run.family, run);
      }
    }
    if (!shouldStart) {
      if (checkDeferredOnAbort) {
        startDeferredScan(run.family);
      }
      return;
    }

    try {
      run.provider.startScan(config, publisher);
    } catch (Exception e) {
      manager.error(format("Error starting discovery scan %s", run.scan), e);
      final boolean failedCurrent;
      synchronized (lock) {
        if (activeByFamily.get(run.family) == run && run.stage == RunStage.STARTING
            && isActiveStateLocked(run)) {
          run.stage = RunStage.STOPPING;
          activeByFamily.remove(run.family, run);
          failedCurrent = failScanStartErrorLocked(run.scan, run.generation,
              format("Discovery %s failed to start: %s", run.scan, e.getMessage()));
        } else {
          failedCurrent = false;
          activeByFamily.remove(run.family, run);
        }
      }
      ifTrueThen(failedCurrent, this::updateState);
      startDeferredScan(run.family);
      return;
    }

    final boolean stopAfterStart;
    final boolean completeNow;
    synchronized (lock) {
      if (activeByFamily.get(run.family) == run && run.stage == RunStage.STARTING
          && isActiveStateLocked(run)) {
        run.stage = RunStage.RUNNING;
        stopAfterStart = false;
        completeNow = run.completionRequested;
      } else {
        run.stage = RunStage.STOPPING;
        stopAfterStart = true;
        completeNow = false;
      }
    }
    if (stopAfterStart) {
      safeStopProvider(run.family, run.provider);
      synchronized (lock) {
        activeByFamily.remove(run.family, run);
      }
      startDeferredScan(run.family);
    } else if (completeNow) {
      discoveryScanComplete(run);
    }
  }

  private void discoveryScanComplete(ActiveRun run) {
    final int endEventNo;
    synchronized (lock) {
      if (!isActiveRunLocked(run)) {
        return;
      }
      if (run.stage == RunStage.STARTING) {
        run.completionRequested = true;
        return;
      }
      FamilyDiscoveryState scanState = getScanStateLocked(run.scan);
      manager.info(format("Discovered %s stopping %s (=? %s)", run.scan,
          isoConvert(run.generation),
          ifNotNullGet(scanState, state -> isoConvert(state.generation))));
      run.stage = RunStage.STOPPING;
      endEventNo = -run.sendCount.getAndIncrement();
    }

    Throwable primaryError = null;
    try {
      safeStopProvider(run.family, run.provider);
      publishMarkerDiscoveryEvent(run.scan, run.family, run.generation, endEventNo);
    } catch (RuntimeException | Error e) {
      primaryError = e;
    }

    final boolean completed;
    synchronized (lock) {
      activeByFamily.remove(run.family, run);
      completed = !shuttingDown && isActiveStateLocked(run);
      if (completed) {
        detachScanFutureLocked(run.scan);
        run.state.phase = STOPPED;
        scheduleDiscoveryScanLocked(run.scan);
      }
    }
    primaryError = runCompletionStep(primaryError, () -> ifTrueThen(completed, this::updateState));
    primaryError = runCompletionStep(primaryError, () -> startDeferredScan(run.family));
    if (primaryError instanceof Error err) {
      throw err;
    }
    if (primaryError != null) {
      throw new RuntimeException(
          format("While completing discovery scan %s", run.scan), primaryError);
    }
  }

  private static Throwable runCompletionStep(Throwable primaryError, Runnable step) {
    try {
      step.run();
      return primaryError;
    } catch (RuntimeException | Error e) {
      if (primaryError == null) {
        return e;
      }
      primaryError.addSuppressed(e);
      return primaryError;
    }
  }

  private boolean failScanStartErrorLocked(DiscoveryScan scan, Date scanGeneration,
      String message) {
    if (getScanIntervalLocked(scan) > 0) {
      return failRetryablePeriodicScanLocked(scan, scanGeneration, message);
    }
    boolean changed = recordScanFailureLocked(scan, scanGeneration, message);
    scanSchedules.put(scan, new ScanSchedule(scanGeneration, null, null));
    return changed;
  }

  private boolean failRetryablePeriodicScanLocked(DiscoveryScan scan, Date scanGeneration,
      String message) {
    int interval = getScanIntervalLocked(scan);
    Date retryGeneration = Date.from(scanGeneration.toInstant().plusSeconds(interval));
    boolean changed = recordScanFailureLocked(scan, scanGeneration, message);
    Optional<ScheduledFuture<?>> retryFuture = tryScheduleFutureLocked(
        retryGeneration, () -> retryPeriodicScan(scan, retryGeneration));
    if (retryFuture.isPresent()) {
      scanSchedules.put(scan, new ScanSchedule(scanGeneration, retryGeneration, retryFuture.get()));
    } else {
      scanSchedules.put(scan, new ScanSchedule(scanGeneration, null, null));
    }
    return changed;
  }

  private void retryPeriodicScan(DiscoveryScan scan, Date retryGeneration) {
    final boolean shouldRetry;
    synchronized (lock) {
      ScanSchedule current = scanSchedules.get(scan);
      if (!shuttingDown && current != null && getScanStateLocked(scan) != null
          && retryGeneration.equals(current.retryAt())) {
        Date previousRun = current.lastRun();
        if (previousRun == null) {
          scanSchedules.remove(scan);
        } else {
          scanSchedules.put(scan, new ScanSchedule(previousRun, null, null));
        }
        scheduleDiscoveryScanLocked(scan);
        shouldRetry = true;
      } else {
        shouldRetry = false;
      }
    }
    ifTrueThen(shouldRetry, this::updateState);
  }

  private boolean isActiveRunLocked(ActiveRun run) {
    return activeByFamily.get(run.family) == run && run.stage != RunStage.STOPPING
        && isActiveStateLocked(run);
  }

  private boolean isActiveStateLocked(ActiveRun run) {
    return getScanStateLocked(run.scan) == run.state && run.state.phase == ACTIVE
        && run.generation.equals(run.state.generation);
  }

  private void publishMarkerDiscoveryEvent(DiscoveryScan scan, String family, Date scanGeneration,
      int markerEventNo) {
    DiscoveryEvents discoveryEvent = new DiscoveryEvents();
    discoveryEvent.event_no = markerEventNo;
    manager.info(format("Discovered %s active %d for %s", scan, discoveryEvent.event_no,
        isoConvert(scanGeneration)));
    publishDiscoveryEvent(scan, family, scanGeneration, null, discoveryEvent);
  }

  private void publishDiscoveryEvent(DiscoveryScan scan, String family, Date scanGeneration,
      String deviceId, DiscoveryEvents discoveryEvent) {
    discoveryEvent.family = family;
    discoveryEvent.scan = scan.named() ? scan.name() : null;
    discoveryEvent.generation = scanGeneration;
    manager.postDiscoveryProcess(deviceId, discoveryEvent);
    manager.getHost().publish(discoveryEvent);
  }

  private void safeStopProvider(String family, FamilyProvider provider) {
    if (provider == null) {
      return;
    }
    try {
      provider.stopScan();
    } catch (Exception e) {
      manager.error(format("Error stopping discovery provider %s", family), e);
    }
  }

  private boolean isScanConfiguredLocked(DiscoveryScan scan) {
    Map<String, FamilyDiscoveryConfig> scanMap = ifNotNullGet(discoveryConfig,
        config -> scan.named() ? config.scans : config.families);
    return scanMap != null && scanMap.containsKey(scan.name());
  }

  private FamilyDiscoveryConfig getScanConfigLocked(DiscoveryScan scan) {
    return ifNotNullGet(discoveryConfig, config ->
        ifNotNullGet(scan.named() ? config.scans : config.families,
            map -> map.get(scan.name())));
  }

  private String getScanFamilyLocked(DiscoveryScan scan) {
    return scan.named() ? ifNotNullGet(getScanConfigLocked(scan), config -> config.family)
        : scan.name();
  }

  private int getScanIntervalLocked(DiscoveryScan scan) {
    return ofNullable(ifNotNullGet(getScanConfigLocked(scan), c -> c.scan_interval_sec)).orElse(0);
  }

  private int getScanDurationLocked(DiscoveryScan scan) {
    return ofNullable(ifNotNullGet(getScanConfigLocked(scan), c -> c.scan_duration_sec))
        .orElse(SCAN_DURATION_SEC);
  }

  private Map<String, FamilyDiscoveryState> getScanStatesLocked(DiscoveryScan scan) {
    return ifNotNullGet(discoveryState, state -> scan.named() ? state.scans : state.families);
  }

  private FamilyDiscoveryState getScanStateLocked(DiscoveryScan scan) {
    return ifNotNullGet(getScanStatesLocked(scan), states -> states.get(scan.name()));
  }

  private FamilyDiscoveryState ensureScanStateLocked(DiscoveryScan scan) {
    if (discoveryState == null) {
      return new FamilyDiscoveryState();
    }
    Map<String, FamilyDiscoveryState> states = scan.named()
        ? (discoveryState.scans != null ? discoveryState.scans
            : (discoveryState.scans = new HashMap<>()))
        : (discoveryState.families != null ? discoveryState.families
            : (discoveryState.families = new HashMap<>()));
    return states.computeIfAbsent(scan.name(), key -> new FamilyDiscoveryState());
  }

  private void clearScanScheduleLocked(DiscoveryScan scan) {
    cancelFuture(scanSchedules.remove(scan));
  }

  private void cancelScanFutureLocked(DiscoveryScan scan) {
    scanSchedules.computeIfPresent(scan, (key, cur) -> {
      cancelFuture(cur);
      return cur.lastRun() == null && cur.retryAt() == null
          ? null : new ScanSchedule(cur.lastRun(), cur.retryAt(), null);
    });
  }

  private void detachScanFutureLocked(DiscoveryScan scan) {
    scanSchedules.computeIfPresent(scan, (key, cur) ->
        cur.lastRun() == null && cur.retryAt() == null
            ? null : new ScanSchedule(cur.lastRun(), cur.retryAt(), null));
  }

  private void clearAllScanSchedulesLocked() {
    List<ScanSchedule> current = List.copyOf(scanSchedules.values());
    scanSchedules.clear();
    current.forEach(DiscoveryScheduler::cancelFuture);
  }

  private static boolean hasArmedFuture(ScanSchedule schedule) {
    return schedule != null && schedule.future() != null;
  }

  private static void cancelFuture(ScanSchedule schedule) {
    ifNotNullThen(schedule, s -> ifNotNullThen(s.future(), future -> future.cancel(false)));
  }
}
