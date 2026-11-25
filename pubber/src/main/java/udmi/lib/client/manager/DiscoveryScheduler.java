package udmi.lib.client.manager;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static udmi.schema.Category.DISCOVERY_FAMILY_SCAN;
import static udmi.schema.FamilyDiscoveryState.Phase.ACTIVE;
import static udmi.schema.FamilyDiscoveryState.Phase.PENDING;
import static udmi.schema.FamilyDiscoveryState.Phase.STOPPED;

import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import udmi.lib.ProtocolFamily;
import udmi.lib.intf.FamilyProvider;
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
 * Thread-safe scheduler for legacy {@code discovery.families}, named {@code discovery.scans}, and
 * device self-enumeration. Guarded by a single monitor; outbound publishes and state updates are
 * collected into a per-call outbox and flushed after releasing the monitor in causal order.
 */
public class DiscoveryScheduler {

  private record ActiveRun(
      DiscoveryScan scan,
      String family,
      Date generation,
      FamilyDiscoveryState state,
      AtomicInteger sendCount,
      FamilyProvider provider) {}

  private record ScanSchedule(Date lastRun, Date scheduledAt, ScheduledFuture<?> future) {}

  private final DiscoveryManager manager;
  private final ThreadLocal<List<Runnable>> activeOutbox = new ThreadLocal<>();
  private final Map<String, ActiveRun> activeByFamily = new HashMap<>();
  private final Map<DiscoveryScan, ScanSchedule> scanSchedules = new HashMap<>();
  private final Set<DiscoveryScan> deferredScans = new LinkedHashSet<>();

  private DiscoveryConfig discoveryConfig;
  private DiscoveryState discoveryState;
  private boolean shuttingDown;

  /**
   * Creates a scheduler bound to the given discovery manager.
   *
   * @param manager owning discovery manager
   */
  public DiscoveryScheduler(DiscoveryManager manager) {
    this.manager = manager;
  }

  /**
   * Returns the current in-memory discovery state snapshot.
   */
  public synchronized DiscoveryState getDiscoveryState() {
    return discoveryState;
  }

  /**
   * Replaces the in-memory discovery state (intended for test setup).
   */
  public synchronized void setDiscoveryState(DiscoveryState state) {
    this.discoveryState = state;
  }

  /**
   * Returns the current discovery config.
   */
  public synchronized DiscoveryConfig getDiscoveryConfig() {
    return discoveryConfig;
  }

  /**
   * Replaces the discovery config (intended for test setup).
   */
  public synchronized void setDiscoveryConfig(DiscoveryConfig config) {
    this.discoveryConfig = config;
  }

  /**
   * Computes the next scheduled start time on the fixed interval grid. If {@code configGeneration}
   * is at or after both {@code now} and any prior attempted slot, it runs at
   * {@code configGeneration}; otherwise it selects the next upcoming grid slot after them.
   *
   * @param configGeneration base generation timestamp from config
   * @param intervalSec positive interval in seconds
   * @param lastRun timestamp of the most recently attempted slot, or {@code null}
   * @param now current time
   * @return next scheduled grid timestamp
   */
  public static Date nextPeriodicStart(
      Date configGeneration, int intervalSec, Date lastRun, Instant now) {
    checkArgument(intervalSec > 0, "scan_interval_sec must be positive");
    long baseSec = configGeneration.toInstant().getEpochSecond();
    long nowSec = now.getEpochSecond();
    long earliestSec = lastRun == null
        ? nowSec
        : Math.max(nowSec, lastRun.toInstant().getEpochSecond() + 1);
    if (baseSec >= earliestSec) {
      return configGeneration;
    }
    long elapsedSec = earliestSec - baseSec;
    long steps = (elapsedSec + intervalSec - 1) / intervalSec;
    return Date.from(configGeneration.toInstant().plusSeconds(steps * intervalSec));
  }

  /**
   * Applies an updated {@link DiscoveryConfig}, reconciling self-enumeration, legacy family scans,
   * and named scans.
   *
   * @param discovery updated discovery config, or {@code null} when unconfigured
   */
  public void updateConfig(DiscoveryConfig discovery) {
    runLocked(outbox -> {
      if (shuttingDown) {
        return;
      }
      final DiscoveryConfig previousConfig = this.discoveryConfig;
      DiscoveryState beforeState = deepCopy(discoveryState);
      this.discoveryConfig = deepCopy(discovery);

      if (discovery == null) {
        clearAllLocked();
        if (beforeState != null) {
          queueStateUpdateLocked(outbox);
        }
        return;
      }

      if (discoveryState == null) {
        discoveryState = new DiscoveryState();
      }
      final Set<String> previouslyActive = new HashSet<>(activeByFamily.keySet());
      reconcileRemovedScansLocked(previousConfig, outbox);
      if (discovery.families == null) {
        discoveryState.families = null;
      } else {
        ifNullThen(discoveryState.families, () -> discoveryState.families = new HashMap<>());
      }
      if (discovery.scans == null) {
        discoveryState.scans = null;
      } else {
        ifNullThen(discoveryState.scans, () -> discoveryState.scans = new HashMap<>());
      }

      updateDiscoveryEnumerationLocked(discovery, outbox);
      ofNullable(discovery.families).ifPresent(map ->
          map.keySet().forEach(name -> scheduleScanLocked(DiscoveryScan.forFamily(name), outbox)));
      ofNullable(discovery.scans).ifPresent(map ->
          map.keySet().forEach(name -> scheduleScanLocked(DiscoveryScan.forScan(name), outbox)));

      if (!Objects.equals(beforeState, discoveryState)) {
        queueStateUpdateLocked(outbox);
      }
      previouslyActive.stream()
          .filter(family -> !activeByFamily.containsKey(family))
          .forEach(family -> pollDeferredLocked(family, outbox));
    });
  }

  /**
   * Cancels all pending timers and stops any active scan providers.
   */
  public void shutdown() {
    runLocked(outbox -> {
      shuttingDown = true;
      scanSchedules.values().forEach(schedule -> cancelFuture(schedule.future()));
      scanSchedules.clear();
      deferredScans.clear();
      new ArrayList<>(activeByFamily.values()).forEach(run -> {
        activeByFamily.remove(run.family(), run);
        safeStopProvider(run.family(), run.provider());
      });
    });
  }

  /**
   * Executor callback when a scheduled scan slot is reached.
   *
   * @param scan scan identifier
   * @param scanGeneration scheduled slot timestamp
   */
  public void checkDiscoveryScan(DiscoveryScan scan, Date scanGeneration) {
    runLocked(outbox -> onScheduledStartLocked(scan, scanGeneration, outbox));
  }

  /**
   * Starts a discovery scan directly if it is configured, valid, and not already active.
   *
   * @param scan scan identifier
   * @param scanGeneration generation timestamp for the scan run
   */
  public void startDiscoveryScan(DiscoveryScan scan, Date scanGeneration) {
    runLocked(outbox -> {
      if (!canRunScanLocked(scan)) {
        return;
      }
      String family = configuredFamilyLocked(scan);
      if (activeByFamily.containsKey(family)) {
        deferOrSkipBusyScanLocked(scan, scanGeneration, scanSchedules.get(scan), outbox);
        return;
      }
      beginActiveRunLocked(scan, scanGeneration, outbox);
    });
  }

  synchronized boolean hasActiveScans() {
    return !activeByFamily.isEmpty();
  }

  synchronized boolean isFamilyActive(String family) {
    return activeByFamily.containsKey(family);
  }

  synchronized boolean hasScanSchedules() {
    return scanSchedules.values().stream().anyMatch(schedule -> schedule.future() != null);
  }

  private void runLocked(Consumer<List<Runnable>> action) {
    List<Runnable> current = activeOutbox.get();
    if (current != null) {
      synchronized (this) {
        action.accept(current);
      }
      return;
    }
    List<Runnable> outbox = new ArrayList<>();
    synchronized (this) {
      activeOutbox.set(outbox);
      try {
        action.accept(outbox);
      } finally {
        activeOutbox.remove();
      }
    }
    outbox.forEach(Runnable::run);
  }

  private void clearAllLocked() {
    scanSchedules.values().forEach(schedule -> cancelFuture(schedule.future()));
    scanSchedules.clear();
    deferredScans.clear();
    new ArrayList<>(activeByFamily.values()).forEach(run -> {
      activeByFamily.remove(run.family(), run);
      safeStopProvider(run.family(), run.provider());
    });
    discoveryState = null;
  }

  private void reconcileRemovedScansLocked(
      DiscoveryConfig previousConfig, List<Runnable> outbox) {
    List<DiscoveryScan> existing = new ArrayList<>();
    if (discoveryState.families != null) {
      discoveryState.families.keySet().forEach(name -> existing.add(DiscoveryScan.forFamily(name)));
    }
    if (discoveryState.scans != null) {
      discoveryState.scans.keySet().forEach(name -> existing.add(DiscoveryScan.forScan(name)));
    }
    for (DiscoveryScan scan : existing) {
      FamilyDiscoveryConfig nextConfig = getScanConfigLocked(scan);
      FamilyDiscoveryConfig prevConfig = getConfigEntry(previousConfig, scan);
      boolean familyChanged = scan.named() && prevConfig != null && nextConfig != null
          && !Objects.equals(prevConfig.family, nextConfig.family);
      if (nextConfig == null || familyChanged) {
        removeScanLocked(scan, outbox);
      }
    }
  }

  private void removeScanLocked(DiscoveryScan scan, List<Runnable> outbox) {
    ActiveRun activeRun = findActiveRunLocked(scan);
    if (activeRun != null) {
      stopActiveRunLocked(activeRun, outbox);
    }
    deferredScans.remove(scan);
    ScanSchedule schedule = scanSchedules.remove(scan);
    if (schedule != null) {
      cancelFuture(schedule.future());
    }
    if (discoveryState != null) {
      Map<String, FamilyDiscoveryState> map =
          scan.named() ? discoveryState.scans : discoveryState.families;
      if (map != null) {
        map.remove(scan.name());
      }
    }
  }

  private void scheduleScanLocked(DiscoveryScan scan, List<Runnable> outbox) {
    FamilyDiscoveryConfig config = getScanConfigLocked(scan);
    if (config == null) {
      return;
    }
    String invalidReason = invalidScanReasonLocked(scan);
    if (invalidReason != null) {
      ActiveRun activeRun = findActiveRunLocked(scan);
      if (activeRun != null) {
        stopActiveRunLocked(activeRun, outbox);
      }
      deferredScans.remove(scan);
      ScanSchedule old = scanSchedules.remove(scan);
      if (old != null) {
        cancelFuture(old.future());
      }
      setScanErrorLocked(ensureScanStateLocked(scan), null, null, invalidReason);
      return;
    }

    if (config.generation == null) {
      removeScanLocked(scan, outbox);
      return;
    }

    if (isScanActiveLocked(scan)) {
      return;
    }

    FamilyDiscoveryState scanState = ensureScanStateLocked(scan);
    ScanSchedule schedule = scanSchedules.get(scan);
    Date recordedLastRun = schedule == null ? null : schedule.lastRun();
    int interval = getScanInterval(config);

    if (interval > 0) {
      deferredScans.remove(scan);
      Date startGen = nextPeriodicStart(
          config.generation, interval, recordedLastRun, manager.getScanNow());
      if (schedule != null && startGen.equals(schedule.scheduledAt())
          && (scanState.phase == PENDING || scanState.status != null)) {
        return;
      }
      if (schedule != null) {
        cancelFuture(schedule.future());
      }
      final ScheduledFuture<?> future = scheduleFutureLocked(
          startGen, () -> checkDiscoveryScan(scan, startGen));
      scanState.phase = PENDING;
      scanState.generation = startGen;
      scanState.status = null;
      scanSchedules.put(scan, new ScanSchedule(
          recordedLastRun, future != null ? startGen : null, future));
      return;
    }

    Date configGeneration = config.generation;
    if (configGeneration.equals(scanState.generation)
        && scanState.status == null
        && ((scanState.phase == PENDING
                && (hasArmedFuture(schedule) || deferredScans.contains(scan)))
            || scanState.phase == STOPPED)) {
      return;
    }

    Date baseline = ofNullable(recordedLastRun).orElse(manager.getHost().getStartTime());
    if (!configGeneration.after(baseline)) {
      deferredScans.remove(scan);
      if (schedule != null) {
        cancelFuture(schedule.future());
        scanSchedules.put(scan, new ScanSchedule(recordedLastRun, null, null));
      }
      if (!Objects.equals(configGeneration, scanState.generation)) {
        scanState.phase = STOPPED;
        scanState.generation = configGeneration;
        scanState.status = null;
        scanState.active_count = null;
      }
      return;
    }

    deferredScans.remove(scan);
    if (schedule != null) {
      cancelFuture(schedule.future());
    }
    final ScheduledFuture<?> future = scheduleFutureLocked(
        configGeneration, () -> checkDiscoveryScan(scan, configGeneration));
    scanState.phase = PENDING;
    scanState.generation = configGeneration;
    scanState.status = null;
    scanSchedules.put(scan, new ScanSchedule(
        recordedLastRun, future != null ? configGeneration : null, future));
  }

  private void onScheduledStartLocked(
      DiscoveryScan scan, Date scanGeneration, List<Runnable> outbox) {
    if (!canRunScanLocked(scan)) {
      return;
    }
    ScanSchedule schedule = scanSchedules.get(scan);
    if (schedule == null || !scanGeneration.equals(schedule.scheduledAt())) {
      return;
    }
    String family = configuredFamilyLocked(scan);
    if (activeByFamily.containsKey(family)) {
      deferOrSkipBusyScanLocked(scan, scanGeneration, schedule, outbox);
      return;
    }
    beginActiveRunLocked(scan, scanGeneration, outbox);
  }

  private void deferOrSkipBusyScanLocked(
      DiscoveryScan scan, Date scanGeneration, ScanSchedule schedule, List<Runnable> outbox) {
    FamilyDiscoveryConfig config = getScanConfigLocked(scan);
    if (schedule != null) {
      cancelFuture(schedule.future());
    }
    if (getScanInterval(config) > 0) {
      scanSchedules.put(scan, new ScanSchedule(scanGeneration, null, null));
      scheduleScanLocked(scan, outbox);
      queueStateUpdateLocked(outbox);
      return;
    }
    Date priorLastRun = schedule == null ? null : schedule.lastRun();
    scanSchedules.put(scan, new ScanSchedule(priorLastRun, null, null));
    FamilyDiscoveryState scanState = ensureScanStateLocked(scan);
    final boolean changed = scanState.phase != PENDING
        || !Objects.equals(scanGeneration, scanState.generation)
        || scanState.status != null;
    scanState.phase = PENDING;
    scanState.generation = scanGeneration;
    scanState.status = null;
    deferredScans.add(scan);
    if (changed) {
      queueStateUpdateLocked(outbox);
    }
  }

  private void beginActiveRunLocked(
      DiscoveryScan scan, Date scanGeneration, List<Runnable> outbox) {
    FamilyDiscoveryConfig config = getScanConfigLocked(scan);
    String family = configuredFamilyLocked(scan);
    FamilyDiscoveryState scanState = ensureScanStateLocked(scan);
    int durationSec = ofNullable(config.scan_duration_sec)
        .orElse(DiscoveryManager.SCAN_DURATION_SEC);
    Date stopTime = Date.from(manager.getScanNow().plusSeconds(durationSec));
    AtomicInteger sendCount = new AtomicInteger(0);
    FamilyProvider provider = manager.discoveryProvider(family);
    ActiveRun run = new ActiveRun(scan, family, scanGeneration, scanState, sendCount, provider);

    ScheduledFuture<?> stopFuture = scheduleFutureLocked(
        stopTime, () -> runLocked(stopOutbox -> onScheduledStopLocked(run, stopOutbox)));
    if (stopFuture == null) {
      return;
    }

    deferredScans.remove(scan);
    ScanSchedule old = scanSchedules.get(scan);
    if (old != null) {
      cancelFuture(old.future());
    }
    activeByFamily.put(family, run);
    scanSchedules.put(scan, new ScanSchedule(scanGeneration, null, stopFuture));

    scanState.generation = scanGeneration;
    scanState.phase = ACTIVE;
    scanState.status = null;
    scanState.active_count = sendCount.get();

    final int outboxMark = outbox.size();
    queueMarkerEvent(outbox, scan, family, scanGeneration, sendCount);
    queueStateUpdateLocked(outbox);

    if (provider == null) {
      manager.info(format("Discovered %s has no provider, skipping provider scan", family));
      return;
    }

    FamilyDiscoveryConfig configCopy = deepCopy(config);
    configCopy.depth = ofNullable(configCopy.depth)
        .orElse(configCopy.addrs != null ? Depth.DETAILS : Depth.ENTRIES);
    Set<String> targets = configCopy.addrs == null ? null : ImmutableSet.copyOf(configCopy.addrs);

    try {
      provider.startScan(configCopy, (deviceId, event) ->
          handleDiscoveredDevice(run, targets, deviceId, event));
    } catch (Exception e) {
      manager.error(format("Error starting discovery scan %s", scan), e);
      if (outbox.size() == outboxMark + 2) {
        outbox.subList(outboxMark, outbox.size()).clear();
      } else if (activeByFamily.get(family) == run) {
        queueMarkerEvent(outbox, scan, family, scanGeneration, sendCount);
      }
      if (activeByFamily.remove(family, run)) {
        cancelFuture(stopFuture);
        if (!shuttingDown && getScanStateLocked(scan) == scanState) {
          setScanErrorLocked(scanState, scanGeneration, null,
              format("Error starting discovery scan %s: %s", scan, e.getMessage()));
          int interval = getScanInterval(config);
          if (interval > 0 && config.generation != null) {
            Date nextSlot = nextPeriodicStart(
                config.generation, interval, scanGeneration, manager.getScanNow());
            final ScheduledFuture<?> retryFuture = scheduleFutureLocked(
                nextSlot, () -> checkDiscoveryScan(scan, nextSlot));
            scanSchedules.put(scan, new ScanSchedule(
                scanGeneration, retryFuture != null ? nextSlot : null, retryFuture));
          } else {
            scanSchedules.put(scan, new ScanSchedule(scanGeneration, null, null));
          }
          queueStateUpdateLocked(outbox);
        }
        pollDeferredLocked(family, outbox);
      }
    }
  }

  private void handleDiscoveredDevice(
      ActiveRun run, Set<String> targets, String deviceId, DiscoveryEvents event) {
    String addr = event.addr;
    String network = event.network;
    if (addr == null && network == null) {
      return;
    }
    String genIso = isoConvert(run.generation());
    if (addr != null && !DiscoveryManager.matchesTarget(targets, addr)) {
      manager.info(format("Discovered %s device %s for %s skipped", run.family(), addr, genIso));
      return;
    }
    runLocked(outbox -> {
      if (shuttingDown || activeByFamily.get(run.family()) != run) {
        return;
      }
      int activeCount = run.sendCount().getAndIncrement();
      run.state().active_count = activeCount;
      manager.info(format("Discovered %s device %s %s for %s as %s",
          run.family(), network, addr, genIso, activeCount));
      event.event_no = activeCount;
      queueDiscoveryEvent(outbox, run.scan(), run.family(), run.generation(), deviceId, event);
      queueStateUpdateLocked(outbox);
    });
  }

  private void onScheduledStopLocked(ActiveRun run, List<Runnable> outbox) {
    if (activeByFamily.get(run.family()) != run) {
      return;
    }
    stopActiveRunLocked(run, outbox);
    if (!shuttingDown && getScanStateLocked(run.scan()) == run.state()) {
      run.state().phase = STOPPED;
      scheduleScanLocked(run.scan(), outbox);
      queueStateUpdateLocked(outbox);
    }
    pollDeferredLocked(run.family(), outbox);
  }

  private void stopActiveRunLocked(ActiveRun run, List<Runnable> outbox) {
    activeByFamily.remove(run.family(), run);
    ScanSchedule current = scanSchedules.get(run.scan());
    if (current != null && current.future() != null) {
      cancelFuture(current.future());
    }
    scanSchedules.put(run.scan(), new ScanSchedule(run.generation(), null, null));
    manager.info(format("Discovered %s stopping %s (=? %s)",
        run.family(), isoConvert(run.generation()), isoConvert(run.state().generation)));
    safeStopProvider(run.family(), run.provider());
    queueMarkerEvent(outbox, run.scan(), run.family(), run.generation(), run.sendCount());
  }

  private void pollDeferredLocked(String family, List<Runnable> outbox) {
    while (!shuttingDown && !activeByFamily.containsKey(family)) {
      DiscoveryScan next = deferredScans.stream()
          .filter(scan -> family.equals(configuredFamilyLocked(scan)))
          .findFirst()
          .orElse(null);
      if (next == null) {
        return;
      }
      deferredScans.remove(next);
      if (!canRunScanLocked(next)) {
        continue;
      }
      FamilyDiscoveryConfig config = getScanConfigLocked(next);
      if (config.generation == null || getScanInterval(config) > 0) {
        continue;
      }
      FamilyDiscoveryState scanState = getScanStateLocked(next);
      Date scanGeneration = scanState != null && scanState.generation != null
          ? scanState.generation
          : config.generation;
      beginActiveRunLocked(next, scanGeneration, outbox);
    }
  }

  private void updateDiscoveryEnumerationLocked(DiscoveryConfig config, List<Runnable> outbox) {
    Date enumerationGeneration = config.generation;
    if (enumerationGeneration == null) {
      discoveryState.generation = null;
      return;
    }
    if (discoveryState.generation != null
        && !enumerationGeneration.after(discoveryState.generation)) {
      return;
    }
    discoveryState.generation = enumerationGeneration;
    Enumerations depths = ofNullable(config.enumerations).orElseGet(Enumerations::new);
    outbox.add(() -> {
      manager.info(format("Discovery enumeration at %s", isoConvert(enumerationGeneration)));
      DiscoveryEvents discoveryEvent = new DiscoveryEvents();
      discoveryEvent.family = ProtocolFamily.IOT;
      discoveryEvent.generation = enumerationGeneration;
      discoveryEvent.points = maybeEnumerate(depths.points,
          () -> manager.enumeratePoints(manager.getDeviceId()));
      discoveryEvent.features = maybeEnumerate(depths.features, manager::getFeatures);
      discoveryEvent.families = maybeEnumerate(depths.families,
          () -> manager.getDeviceManager().enumerateFamilies());
      manager.getHost().publish(discoveryEvent);
    });
  }

  private <K, V> Map<K, V> maybeEnumerate(Depth depth, Supplier<Map<K, V>> supplier) {
    return ifTrueGet(DiscoveryManager.shouldEnumerate(depth, Depth.ENTRIES), supplier);
  }

  private void queueStateUpdateLocked(List<Runnable> outbox) {
    Object snapshot = ofNullable((Object) deepCopy(discoveryState)).orElse(DiscoveryState.class);
    outbox.add(() -> manager.updateState(snapshot));
  }

  private void queueMarkerEvent(List<Runnable> outbox, DiscoveryScan scan, String family,
      Date scanGeneration, AtomicInteger sendCount) {
    DiscoveryEvents event = new DiscoveryEvents();
    event.event_no = -sendCount.getAndIncrement();
    manager.info(format("Discovered %s active %d for %s",
        family, event.event_no, isoConvert(scanGeneration)));
    queueDiscoveryEvent(outbox, scan, family, scanGeneration, null, event);
  }

  private void queueDiscoveryEvent(List<Runnable> outbox, DiscoveryScan scan, String family,
      Date generation, String deviceId, DiscoveryEvents event) {
    event.family = family;
    event.scan = scan.named() ? scan.name() : null;
    event.generation = generation;
    outbox.add(() -> {
      manager.postDiscoveryProcess(deviceId, event);
      manager.getHost().publish(event);
    });
  }

  private boolean canRunScanLocked(DiscoveryScan scan) {
    return !shuttingDown
        && discoveryConfig != null
        && discoveryState != null
        && getScanConfigLocked(scan) != null
        && invalidScanReasonLocked(scan) == null
        && !isScanActiveLocked(scan);
  }

  private String invalidScanReasonLocked(DiscoveryScan scan) {
    FamilyDiscoveryConfig config = getScanConfigLocked(scan);
    if (config == null) {
      return null;
    }
    if (!scan.named() && config.family != null) {
      return format("Entry in discovery.families %s must not specify family", scan.name());
    }
    if (scan.named()) {
      if (config.family == null || config.family.isEmpty()) {
        return format("Named discovery scan %s missing required family", scan.name());
      }
      if (manager.discoveryProvider(config.family) == null) {
        return format("Unsupported discovery family %s for scan %s", config.family, scan.name());
      }
    }
    if (config.scan_interval_sec != null && config.scan_interval_sec < 0) {
      return format("Scan %s has negative scan_interval_sec %d",
          scan.name(), config.scan_interval_sec);
    }
    if (config.scan_duration_sec != null && config.scan_duration_sec < 0) {
      return format("Scan %s has negative scan_duration_sec %d",
          scan.name(), config.scan_duration_sec);
    }
    if (config.addrs != null && config.addrs.stream().anyMatch(Objects::isNull)) {
      return format("Scan %s contains null target address", scan.name());
    }
    return null;
  }

  private void setScanErrorLocked(
      FamilyDiscoveryState state, Date generation, Integer activeCount, String message) {
    boolean unchanged = state.phase == STOPPED
        && Objects.equals(generation, state.generation)
        && Objects.equals(activeCount, state.active_count)
        && state.status != null
        && DISCOVERY_FAMILY_SCAN.equals(state.status.category)
        && Objects.equals(message, state.status.message);
    if (unchanged) {
      return;
    }
    state.phase = STOPPED;
    state.generation = generation;
    state.active_count = activeCount;
    Entry status = new Entry();
    status.category = DISCOVERY_FAMILY_SCAN;
    status.level = Level.ERROR.value();
    status.timestamp = Date.from(manager.getScanNow());
    status.message = message;
    state.status = status;
  }

  private ScheduledFuture<?> scheduleFutureLocked(Date time, Runnable task) {
    if (shuttingDown) {
      return null;
    }
    try {
      return manager.scheduleFuture(time, task);
    } catch (RuntimeException e) {
      if (shuttingDown || e instanceof RejectedExecutionException
          || (e.getMessage() != null && e.getMessage().contains("shutdown"))) {
        manager.warn("Discovery executor unavailable, skipping schedule: " + e.getMessage());
        return null;
      }
      throw e;
    }
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

  private FamilyDiscoveryConfig getScanConfigLocked(DiscoveryScan scan) {
    return getConfigEntry(discoveryConfig, scan);
  }

  private static FamilyDiscoveryConfig getConfigEntry(
      DiscoveryConfig config, DiscoveryScan scan) {
    if (config == null) {
      return null;
    }
    Map<String, FamilyDiscoveryConfig> map = scan.named() ? config.scans : config.families;
    return map == null ? null : map.get(scan.name());
  }

  private String configuredFamilyLocked(DiscoveryScan scan) {
    FamilyDiscoveryConfig config = getScanConfigLocked(scan);
    return scan.named() ? (config == null ? null : config.family) : scan.name();
  }

  private FamilyDiscoveryState ensureScanStateLocked(DiscoveryScan scan) {
    if (discoveryState == null) {
      discoveryState = new DiscoveryState();
    }
    if (scan.named()) {
      ifNullThen(discoveryState.scans, () -> discoveryState.scans = new HashMap<>());
      return discoveryState.scans.computeIfAbsent(scan.name(), k -> new FamilyDiscoveryState());
    }
    ifNullThen(discoveryState.families, () -> discoveryState.families = new HashMap<>());
    return discoveryState.families.computeIfAbsent(scan.name(), k -> new FamilyDiscoveryState());
  }

  private FamilyDiscoveryState getScanStateLocked(DiscoveryScan scan) {
    if (discoveryState == null) {
      return null;
    }
    Map<String, FamilyDiscoveryState> map =
        scan.named() ? discoveryState.scans : discoveryState.families;
    return map == null ? null : map.get(scan.name());
  }

  private ActiveRun findActiveRunLocked(DiscoveryScan scan) {
    return activeByFamily.values().stream()
        .filter(run -> run.scan().equals(scan))
        .findFirst()
        .orElse(null);
  }

  private boolean isScanActiveLocked(DiscoveryScan scan) {
    return findActiveRunLocked(scan) != null;
  }

  private static int getScanInterval(FamilyDiscoveryConfig config) {
    return config == null ? 0 : ofNullable(config.scan_interval_sec).orElse(0);
  }

  private static boolean hasArmedFuture(ScanSchedule schedule) {
    return schedule != null && schedule.future() != null && !schedule.future().isCancelled();
  }

  private static void cancelFuture(ScheduledFuture<?> future) {
    if (future != null) {
      future.cancel(false);
    }
  }
}
