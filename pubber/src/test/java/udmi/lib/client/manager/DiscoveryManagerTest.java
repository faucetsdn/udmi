package udmi.lib.client.manager;

import static com.google.udmi.util.GeneralUtils.deepCopy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static udmi.lib.client.manager.DiscoveryScan.forFamily;
import static udmi.lib.client.manager.DiscoveryScan.forScan;
import static udmi.schema.Category.DISCOVERY_FAMILY_SCAN;
import static udmi.schema.FamilyDiscoveryState.Phase.ACTIVE;
import static udmi.schema.FamilyDiscoveryState.Phase.PENDING;
import static udmi.schema.FamilyDiscoveryState.Phase.STOPPED;

import daq.pubber.impl.manager.PubberDiscoveryManager;
import daq.pubber.impl.provider.PubberBacnetProvider;
import daq.pubber.impl.provider.PubberIpProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.function.BiConsumer;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import udmi.lib.ProtocolFamily;
import udmi.lib.intf.FamilyProvider;
import udmi.lib.intf.ManagerHost;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvents;
import udmi.schema.DiscoveryState;
import udmi.schema.Enumerations;
import udmi.schema.Enumerations.Depth;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.FeatureDiscovery;
import udmi.schema.Level;
import udmi.schema.PointPointsetModel;
import udmi.schema.PubberConfiguration;
import udmi.schema.ScanDiscoveryConfig;

/**
 * Unit tests for {@link DiscoveryScheduler} and {@link DiscoveryManager}.
 */
public class DiscoveryManagerTest {

  private static final String BACNET = "bacnet";
  private static final String VENDOR = "vendor";
  private static final Instant START_TIME = Instant.parse("2026-01-01T00:00:00Z");

  private final FamilyProvider bacnetProvider = mockScanProvider();
  private final FamilyProvider vendorProvider = mockScanProvider();
  private final FakeDiscoveryManager manager = new FakeDiscoveryManager(
      Map.of(BACNET, bacnetProvider, VENDOR, vendorProvider));
  private final DiscoveryScheduler scheduler = manager.getDiscoveryScheduler();

  private static FamilyProvider mockScanProvider() {
    FamilyProvider provider = mock(FamilyProvider.class);
    when(provider.supportsDiscoveryScan()).thenReturn(true);
    return provider;
  }

  @Test
  public void scanStopsAfterConfiguredDuration() {
    assertEquals(60, scheduledStopSec(60));
  }

  @Test
  public void scanStopsAfterDefaultDurationWhenUnset() {
    assertEquals(DiscoveryManager.SCAN_DURATION_SEC, scheduledStopSec(null));
  }

  private long scheduledStopSec(Integer scanDurationSec) {
    FamilyDiscoveryConfig familyConfig = new FamilyDiscoveryConfig();
    familyConfig.scan_duration_sec = scanDurationSec;
    DiscoveryConfig config = new DiscoveryConfig();
    config.families = new HashMap<>(Map.of(BACNET, familyConfig));
    scheduler.setDiscoveryConfig(config);
    scheduler.setDiscoveryState(new DiscoveryState());

    long startMs = manager.now.toEpochMilli();
    scheduler.startDiscoveryScan(forFamily(BACNET), new Date(startMs));
    return (manager.futures.get(0).time().getTime() - startMs) / 1000;
  }

  @Test
  public void namedScansOfSameFamilyScheduleIndependently() {
    manager.updateConfig(discovery(null, Map.of(
        "nightly", periodicScan(BACNET, 3600),
        "adhoc", oneShotScan(BACNET))));

    assertNull(manager.getDiscoveryState().families);
    assertEquals(PENDING, scanState("nightly").phase);
    assertEquals(PENDING, scanState("adhoc").phase);
    assertEquals(2, manager.futures.size());
  }

  @Test
  public void namedAndFamilyScanEventAttribution() {
    FamilyDiscoveryConfig vendorCfg = oneShotFamily();
    vendorCfg.generation = Date.from(START_TIME.plusSeconds(40));
    ScanDiscoveryConfig filtered = oneShotScan(BACNET);
    filtered.addrs = List.of("1042");
    manager.updateConfig(discovery(
        Map.of(VENDOR, vendorCfg),
        Map.of("adhoc", filtered)));

    manager.runFuture(findFutureIndex(filtered.generation)); // adhoc starts
    int adhocStop = manager.futures.size() - 1;
    BiConsumer<String, DiscoveryEvents> publisher = providerPublisher(bacnetProvider);
    publisher.accept("AHU-1", discovered("1042"));
    publisher.accept("AHU-2", discovered("9999")); // filtered out by addrs
    manager.runFuture(adhocStop); // adhoc stops

    assertEquals(3, manager.published.size());
    assertEquals(Integer.valueOf(0), manager.published.get(0).event_no);
    assertEquals(Integer.valueOf(1), manager.published.get(1).event_no);
    assertEquals(Integer.valueOf(-2), manager.published.get(2).event_no);
    manager.published.forEach(event -> {
      assertEquals(BACNET, event.family);
      assertEquals("adhoc", event.scan);
    });

    manager.published.clear();
    manager.runFuture(findFutureIndex(vendorCfg.generation)); // legacy vendor starts
    int vendorStop = manager.futures.size() - 1;
    providerPublisher(vendorProvider).accept("VAV-1", discovered("100"));
    manager.runFuture(vendorStop);
    assertEquals(3, manager.published.size());
    manager.published.forEach(event -> {
      assertEquals(VENDOR, event.family);
      assertNull(event.scan);
    });
  }

  @Test
  public void synchronousProviderEmitsStartMarkerBeforeActiveStateAndResults() {
    doAnswer(invocation -> {
      BiConsumer<String, DiscoveryEvents> publisher = invocation.getArgument(1);
      publisher.accept("DEV-1", discovered("101"));
      return null;
    }).when(vendorProvider).startScan(any(), any());

    manager.published.clear();
    manager.updateConfig(discovery(null, Map.of("sync_scan", oneShotScan(VENDOR))));
    int startPublishedBaseline = manager.published.size();
    int stateBaseline = manager.timeline.size();

    manager.runFuture(0);

    assertEquals(List.of("publish:sync_scan:0", "state:ACTIVE", "publish:sync_scan:1",
        "state:ACTIVE"), manager.timeline.subList(stateBaseline, manager.timeline.size()));
    assertEquals(2, manager.published.size() - startPublishedBaseline);
  }

  @Test
  public void sporadicScansDeferFifoWhileFamilyBusy() {
    ScanDiscoveryConfig first = oneShotScan(BACNET);
    first.generation = Date.from(START_TIME.plusSeconds(10));
    ScanDiscoveryConfig second = oneShotScan(BACNET);
    second.generation = Date.from(START_TIME.plusSeconds(20));
    ScanDiscoveryConfig third = oneShotScan(BACNET);
    third.generation = Date.from(START_TIME.plusSeconds(30));

    manager.updateConfig(discovery(null, Map.of(
        "first", first, "second", second, "third", third)));
    manager.runFuture(findFutureIndex(first.generation));
    final int firstStopIdx = manager.futures.size() - 1;
    manager.runFuture(findFutureIndex(second.generation)); // second hits busy family -> defers
    // Third hits busy family and defers behind second.
    manager.runFuture(findFutureIndex(third.generation));

    assertEquals(ACTIVE, scanState("first").phase);
    assertEquals(PENDING, scanState("second").phase);
    assertNull(scanState("second").status);
    assertEquals(PENDING, scanState("third").phase);

    manager.runFuture(firstStopIdx); // first stops -> second starts automatically
    assertEquals(STOPPED, scanState("first").phase);
    assertEquals(ACTIVE, scanState("second").phase);
    assertEquals(PENDING, scanState("third").phase);

    // Removing active "second" frees bacnet and immediately starts "third".
    manager.updateConfig(discovery(null, Map.of("third", third)));
    assertEquals(ACTIVE, scanState("third").phase);
  }

  @Test
  public void periodicScanSkipsBusySlotAndSchedulesNextSlot() {
    Date blockerGen = Date.from(START_TIME.plusSeconds(10));
    Date nightlyBase = Date.from(START_TIME.plusSeconds(20));
    ScanDiscoveryConfig blocker = oneShotScan(BACNET);
    blocker.generation = blockerGen;
    ScanDiscoveryConfig nightly = periodicScan(BACNET, 60);
    nightly.generation = nightlyBase;

    manager.updateConfig(discovery(null, Map.of("blocker", blocker, "nightly", nightly)));
    int blockerStart = findFutureIndex(blockerGen);
    final int nightlySlot0 = findFutureIndex(nightlyBase);

    manager.now = blockerGen.toInstant();
    manager.runFuture(blockerStart);
    final int blockerStopIdx = manager.futures.size() - 1;
    assertEquals(ACTIVE, scanState("blocker").phase);

    manager.now = nightlyBase.toInstant();
    manager.runFuture(nightlySlot0);

    Date expectedNextSlot = Date.from(nightlyBase.toInstant().plusSeconds(60));
    assertEquals(PENDING, scanState("nightly").phase);
    assertNull(scanState("nightly").status);
    assertEquals(expectedNextSlot, scanState("nightly").generation);

    int nightlySlot1 = findFutureIndex(expectedNextSlot);
    manager.runFuture(blockerStopIdx); // stop blocker
    manager.now = expectedNextSlot.toInstant();
    manager.runFuture(nightlySlot1);
    assertEquals(ACTIVE, scanState("nightly").phase);
  }

  @Test
  public void periodicSchedulingSelectsNextUpcomingGridSlot() {
    Date base = Date.from(START_TIME.plusSeconds(100));
    // Future base -> N = 0
    assertEquals(base, DiscoveryScheduler.nextPeriodicStart(
        base, 60, null, base.toInstant().minusSeconds(10)));
    // Exact now -> N = 0
    assertEquals(base, DiscoveryScheduler.nextPeriodicStart(
        base, 60, null, base.toInstant()));
    // Past base (10s past) -> next upcoming slot (no past catch-up)
    assertEquals(Date.from(base.toInstant().plusSeconds(60)),
        DiscoveryScheduler.nextPeriodicStart(
            base, 60, null, base.toInstant().plusSeconds(10)));
    // Back-to-back completion at exact slot time -> advances by 1 interval
    assertEquals(Date.from(base.toInstant().plusSeconds(60)),
        DiscoveryScheduler.nextPeriodicStart(base, 60, base, base.toInstant()));
  }

  @Test
  public void removingGenerationOrIntervalTerminatesPeriodicScan() {
    ScanDiscoveryConfig nightly = periodicScan(BACNET, 3600);
    nightly.generation = Date.from(START_TIME.plusSeconds(30));
    manager.updateConfig(discovery(null, Map.of("nightly", nightly)));
    int startIdx = 0;

    manager.runFuture(startIdx);
    int stopIdx = 1;
    // Remove interval while active: current run stops cleanly without scheduling another loop.
    manager.updateConfig(discovery(null, Map.of("nightly", oneShotScan(BACNET))));
    manager.runFuture(stopIdx);
    assertEquals(STOPPED, scanState("nightly").phase);

    // Remove generation: cancels pending schedule and removes scan entry.
    ScanDiscoveryConfig noGen = new ScanDiscoveryConfig();
    noGen.family = BACNET;
    noGen.scan_interval_sec = 3600;
    manager.updateConfig(discovery(null, Map.of("nightly", noGen)));
    assertFalse(manager.getDiscoveryState().scans.containsKey("nightly"));
    assertFalse(scheduler.hasScanSchedules());
  }

  @Test
  public void invalidNamedScanReportsErrorAndRecoveryClearsStatus() {
    manager.updateConfig(discovery(null, Map.of(
        "missing_family", oneShotScan(null),
        "unsupported", oneShotScan("zigbee"),
        "good", oneShotScan(BACNET))));

    assertScanError(scanState("missing_family"), "missing required family");
    assertScanError(scanState("unsupported"), "Unsupported discovery family");
    assertEquals(PENDING, scanState("good").phase);

    manager.updateConfig(discovery(null, Map.of(
        "missing_family", oneShotScan(VENDOR),
        "good", oneShotScan(BACNET))));
    assertEquals(PENDING, scanState("missing_family").phase);
    assertNull(scanState("missing_family").status);
  }

  @Test
  public void legacyFamiliesValidationAndCompatibility() {
    // Separate namespaces allow the same key name ("bacnet") in families and scans.
    FamilyDiscoveryConfig zigbeeCfg = oneShotFamily();
    zigbeeCfg.generation = Date.from(START_TIME.plusSeconds(40));
    manager.updateConfig(discovery(
        Map.of(BACNET, oneShotFamily(), "zigbee", zigbeeCfg),
        Map.of(BACNET, oneShotScan(VENDOR))));

    // Legacy unsupported family does not error and still transitions cleanly.
    FamilyDiscoveryState legacyZigbee = manager.getDiscoveryState().families.get("zigbee");
    assertEquals(PENDING, legacyZigbee.phase);
    assertNull(legacyZigbee.status);
    manager.runFuture(findFutureIndex(zigbeeCfg.generation));
    assertEquals(ACTIVE, manager.getDiscoveryState().families.get("zigbee").phase);
    assertNotNull(manager.getDiscoveryState().families.get(BACNET));

  }

  @Test
  public void negativeTimersAndNullAddrRejected() {
    ScanDiscoveryConfig negInterval = oneShotScan(BACNET);
    negInterval.scan_interval_sec = -1;
    ScanDiscoveryConfig negDuration = oneShotScan(BACNET);
    negDuration.scan_duration_sec = -5;
    ScanDiscoveryConfig nullAddr = oneShotScan(BACNET);
    nullAddr.addrs = Arrays.asList("1000", null);

    manager.updateConfig(discovery(null, Map.of(
        "neg_interval", negInterval,
        "neg_duration", negDuration,
        "null_addr", nullAddr)));

    assertScanError(scanState("neg_interval"), "negative scan_interval_sec");
    assertScanError(scanState("neg_duration"), "negative scan_duration_sec");
    assertScanError(scanState("null_addr"), "null target address");
    assertFalse(DiscoveryManager.matchesTarget(Set.of("1000"), null));
    assertTrue(DiscoveryManager.matchesTarget(null, null));
  }

  @Test
  public void changingScanFamilyRestartsOnNewProvider() {
    manager.updateConfig(discovery(null, Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(0);
    assertEquals(ACTIVE, scanState("adhoc").phase);

    ScanDiscoveryConfig switched = oneShotScan(VENDOR);
    switched.generation = Date.from(START_TIME.plusSeconds(60));
    manager.updateConfig(discovery(null, Map.of("adhoc", switched)));

    verify(bacnetProvider, times(1)).stopScan();
    assertFalse(scheduler.isFamilyActive(BACNET));
    assertEquals(PENDING, scanState("adhoc").phase);
    manager.runFuture(manager.futures.size() - 1);
    assertEquals(ACTIVE, scanState("adhoc").phase);
    verify(vendorProvider, times(1)).startScan(any(), any());
  }

  @Test
  public void removingScanOrClearingDiscoveryStopsProviderAndCleansState() {
    FamilyDiscoveryConfig vendorCfg = oneShotFamily();
    vendorCfg.generation = Date.from(START_TIME.plusSeconds(40));
    ScanDiscoveryConfig adhocCfg = oneShotScan(BACNET);
    manager.updateConfig(discovery(
        Map.of(VENDOR, vendorCfg),
        Map.of("adhoc", adhocCfg)));
    manager.runFuture(findFutureIndex(adhocCfg.generation));
    manager.runFuture(findFutureIndex(vendorCfg.generation));

    manager.updateConfig(discovery(Map.of(VENDOR, oneShotFamily()), null));
    verify(bacnetProvider).stopScan();
    assertNull(manager.getDiscoveryState().scans);
    assertFalse(manager.sawEmptyScansMap);

    manager.updateConfig(null);
    verify(vendorProvider).stopScan();
    assertNull(manager.getDiscoveryState());
    assertTrue(manager.sawDiscoveryStateMarker);
    assertFalse(scheduler.hasActiveScans());

    manager.updateConfig(discovery(Map.of(), Map.of()));
    assertEquals(Map.of(), manager.getDiscoveryState().families);
    assertEquals(Map.of(), manager.getDiscoveryState().scans);
  }

  @Test
  public void identicalConfigIsNoOp() {
    ScanDiscoveryConfig active = oneShotScan(VENDOR);
    active.generation = Date.from(START_TIME.plusSeconds(10));
    ScanDiscoveryConfig nightly = periodicScan(BACNET, 3600);
    nightly.generation = Date.from(START_TIME.plusSeconds(60));
    DiscoveryConfig cfg = discovery(null, Map.of(
        "active", active,
        "nightly", nightly,
        "broken", oneShotScan(null)));
    manager.updateConfig(cfg);
    manager.now = active.generation.toInstant();
    manager.runFuture(findFutureIndex(active.generation));

    int updates = manager.stateUpdates;
    final int futureCount = manager.futures.size();

    manager.now = START_TIME.plusSeconds(15);
    manager.updateConfig(deepCopy(cfg));

    assertEquals(updates, manager.stateUpdates);
    assertEquals(futureCount, manager.futures.size());
    assertFalse(manager.futures.get(findFutureIndex(nightly.generation)).isCancelled());
  }

  @Test
  public void providerExceptionsAreIsolatedAndPeriodicRetriesNextSlot() {
    doThrow(new RuntimeException("boom start")).when(bacnetProvider).startScan(any(), any());
    doThrow(new RuntimeException("boom stop")).when(vendorProvider).stopScan();

    Date nightlyGen = Date.from(START_TIME.plusSeconds(30));
    ScanDiscoveryConfig nightly = periodicScan(BACNET, 3600);
    nightly.generation = nightlyGen;
    ScanDiscoveryConfig vendorScan = oneShotScan(VENDOR);
    vendorScan.generation = Date.from(START_TIME.plusSeconds(45));

    manager.updateConfig(discovery(null, Map.of(
        "nightly", nightly,
        "vendor_scan", vendorScan)));

    manager.now = nightlyGen.toInstant();
    manager.published.clear();
    manager.runFuture(findFutureIndex(nightlyGen));
    assertScanError(scanState("nightly"), "boom start");
    // No unclosed start marker emitted on startScan failure.
    assertTrue(manager.published.isEmpty());
    assertFalse(scheduler.isFamilyActive(BACNET));

    Date retrySlot = Date.from(nightlyGen.toInstant().plusSeconds(3600));
    int retryIdx = findFutureIndex(retrySlot);
    doAnswer(invocation -> null).when(bacnetProvider).startScan(any(), any());
    manager.now = retrySlot.toInstant();
    manager.runFuture(retryIdx);
    assertEquals(ACTIVE, scanState("nightly").phase);
    assertNull(scanState("nightly").status);

    manager.runFuture(findFutureIndex(vendorScan.generation));
    assertEquals(ACTIVE, scanState("vendor_scan").phase);
    manager.updateConfig(null); // stopScan exception is isolated
    assertNull(manager.getDiscoveryState());
    assertFalse(scheduler.hasActiveScans());
  }

  @Test
  public void lateCallbacksIgnoredAndPubberDiscoveryManagerFiltersProviders() {
    manager.updateConfig(discovery(null, Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(0);
    BiConsumer<String, DiscoveryEvents> publisher = providerPublisher(bacnetProvider);
    manager.runFuture(1); // stop adhoc
    int publishedAfterStop = manager.published.size();
    publisher.accept("LATE-1", discovered("9001"));
    assertEquals(publishedAfterStop, manager.published.size());

    PubberIpProvider ipOnly = mock(PubberIpProvider.class);
    when(ipOnly.supportsDiscoveryScan()).thenCallRealMethod();
    PubberBacnetProvider bacnetRealCapable = mock(PubberBacnetProvider.class);
    when(bacnetRealCapable.supportsDiscoveryScan()).thenReturn(true);
    ManagerHost host = mock(ManagerHost.class, invocation -> switch (invocation.getMethod()
        .getName()) {
      case "getLocalnetProvider" -> switch ((String) invocation.getArgument(0)) {
        case "ipv4" -> ipOnly;
        case BACNET -> bacnetRealCapable;
        default -> null;
      };
      case "getStartTime" -> Date.from(START_TIME);
      default -> null;
    });
    PubberConfiguration pubberConfig = new PubberConfiguration();
    pubberConfig.deviceId = "AHU-1";
    PubberDiscoveryManager pubberManager = new PubberDiscoveryManager(host, pubberConfig, null);
    try {
      assertNull(pubberManager.discoveryProvider("ipv4"));
      assertEquals(bacnetRealCapable, pubberManager.discoveryProvider(BACNET));

      ScanDiscoveryConfig scan = oneShotScan(BACNET);
      pubberManager.updateConfig(discovery(null, Map.of("adhoc", scan)));
      pubberManager.getDiscoveryScheduler().startDiscoveryScan(forScan("adhoc"), scan.generation);
      assertEquals(ACTIVE, pubberManager.getDiscoveryState().scans.get("adhoc").phase);
    } finally {
      pubberManager.shutdown();
    }
    verify(bacnetRealCapable).stopScan();
    assertFalse(pubberManager.getDiscoveryScheduler().hasActiveScans());
  }

  @Test
  public void selfEnumerationPublishesOncePerGeneration() {
    DiscoveryConfig cfg = discovery(null, null);
    cfg.generation = Date.from(START_TIME.plusSeconds(10));
    Enumerations enums = new Enumerations();
    enums.families = Depth.ENTRIES;
    cfg.enumerations = enums;

    manager.updateConfig(cfg);
    assertEquals(1, manager.published.size());
    assertEquals(ProtocolFamily.IOT, manager.published.get(0).family);
    assertEquals(cfg.generation, manager.published.get(0).generation);

    cfg.generation = null;
    manager.updateConfig(cfg);
    assertNull(manager.getDiscoveryState().generation);
  }

  private FamilyDiscoveryState scanState(String name) {
    return manager.getDiscoveryState().scans.get(name);
  }

  private int findFutureIndex(Date targetTime) {
    for (int i = 0; i < manager.futures.size(); i++) {
      ScheduledEntry entry = manager.futures.get(i);
      if (!entry.isCancelled() && targetTime.equals(entry.time())) {
        return i;
      }
    }
    throw new AssertionError("No active future found for " + targetTime);
  }

  private static void assertScanError(FamilyDiscoveryState state, String messagePart) {
    assertEquals(STOPPED, state.phase);
    assertEquals(DISCOVERY_FAMILY_SCAN, state.status.category);
    assertEquals(Level.ERROR.value(), (int) state.status.level);
    assertNotNull(state.status.timestamp);
    assertTrue(state.status.message, state.status.message.contains(messagePart));
  }

  @SuppressWarnings("unchecked")
  private static BiConsumer<String, DiscoveryEvents> providerPublisher(FamilyProvider provider) {
    ArgumentCaptor<BiConsumer<String, DiscoveryEvents>> captor =
        ArgumentCaptor.forClass(BiConsumer.class);
    verify(provider).startScan(any(), captor.capture());
    return captor.getValue();
  }

  private static DiscoveryEvents discovered(String addr) {
    DiscoveryEvents event = new DiscoveryEvents();
    event.addr = addr;
    return event;
  }

  private static DiscoveryConfig discovery(
      Map<String, FamilyDiscoveryConfig> families,
      Map<String, ScanDiscoveryConfig> scans) {
    DiscoveryConfig config = new DiscoveryConfig();
    config.families = families == null ? null : new HashMap<>(families);
    config.scans = scans == null ? null : new HashMap<>(scans);
    return config;
  }

  private static FamilyDiscoveryConfig oneShotFamily() {
    FamilyDiscoveryConfig config = new FamilyDiscoveryConfig();
    config.generation = Date.from(START_TIME.plusSeconds(30));
    return config;
  }

  private static ScanDiscoveryConfig oneShotScan(String family) {
    ScanDiscoveryConfig config = new ScanDiscoveryConfig();
    config.family = family;
    config.generation = Date.from(START_TIME.plusSeconds(30));
    return config;
  }

  private static ScanDiscoveryConfig periodicScan(String family, int intervalSec) {
    ScanDiscoveryConfig config = new ScanDiscoveryConfig();
    config.family = family;
    config.generation = Date.from(START_TIME);
    config.scan_interval_sec = intervalSec;
    return config;
  }

  private static final class ScheduledEntry {

    private final Date time;
    private final Runnable task;
    private final ScheduledFuture<?> future;
    private boolean cancelled;

    ScheduledEntry(Date time, Runnable task) {
      this.time = time;
      this.task = task;
      this.future = mock(ScheduledFuture.class, invocation -> switch (invocation.getMethod()
          .getName()) {
        case "cancel" -> {
          cancelled = true;
          yield true;
        }
        case "isCancelled" -> cancelled;
        default -> null;
      });
    }

    Date time() {
      return time;
    }

    Runnable task() {
      return task;
    }

    boolean isCancelled() {
      return cancelled;
    }

    ScheduledFuture<?> future() {
      return future;
    }
  }

  private static final class FakeDiscoveryManager implements DiscoveryManager {

    private final DiscoveryScheduler scheduler = new DiscoveryScheduler(this);
    private final Map<String, FamilyProvider> providers;
    private final ManagerHost host;
    private final DeviceManager deviceManager = mock(DeviceManager.class);
    private final List<ScheduledEntry> futures = new ArrayList<>();
    private final List<DiscoveryEvents> published = new ArrayList<>();
    private final List<String> timeline = new ArrayList<>();
    private Instant now = START_TIME;
    private int stateUpdates;
    private boolean sawDiscoveryStateMarker;
    private boolean sawEmptyScansMap;

    FakeDiscoveryManager(Map<String, FamilyProvider> providers) {
      this.providers = providers;
      this.host = mock(ManagerHost.class, invocation -> switch (invocation.getMethod().getName()) {
        case "getStartTime" -> Date.from(START_TIME);
        case "getLocalnetProvider" -> providers.get((String) invocation.getArgument(0));
        case "publish" -> {
          DiscoveryEvents event = (DiscoveryEvents) invocation.getArgument(
              invocation.getArguments().length - 1);
          published.add(event);
          timeline.add("publish:" + event.scan + ":" + event.event_no);
          yield null;
        }
        default -> null;
      });
    }

    void runFuture(int index) {
      ScheduledEntry entry = futures.get(index);
      if (!entry.isCancelled()) {
        entry.task().run();
      }
    }

    @Override
    public DiscoveryScheduler getDiscoveryScheduler() {
      return scheduler;
    }

    @Override
    public Instant getScanNow() {
      return now;
    }

    @Override
    public FamilyProvider discoveryProvider(String family) {
      return providers.get(family);
    }

    @Override
    public void updateState(Object state) {
      stateUpdates++;
      if (state == DiscoveryState.class) {
        sawDiscoveryStateMarker = true;
        return;
      }
      if (state instanceof DiscoveryState ds) {
        if (ds.scans != null && ds.scans.isEmpty()) {
          sawEmptyScansMap = true;
        }
        String activePhase = ds.scans == null ? "NONE"
            : ds.scans.values().stream().map(s -> s.phase.name()).findFirst().orElse("NONE");
        timeline.add("state:" + activePhase);
      }
    }

    @Override
    public ScheduledFuture<?> scheduleFuture(Date futureTime, Runnable futureTask) {
      if (futureTime == null) {
        throw new RejectedExecutionException("null time");
      }
      ScheduledEntry entry = new ScheduledEntry(futureTime, futureTask);
      futures.add(entry);
      return entry.future();
    }

    @Override
    public Map<String, FeatureDiscovery> getFeatures() {
      return Map.of();
    }

    @Override
    public Map<String, PointPointsetModel> enumeratePoints(String deviceId) {
      return Map.of();
    }

    @Override
    public DeviceManager getDeviceManager() {
      return deviceManager;
    }

    @Override
    public void postDiscoveryProcess(String deviceId, DiscoveryEvents discoveryEvents) {
    }

    @Override
    public ManagerHost getHost() {
      return host;
    }

    @Override
    public String getDeviceId() {
      return "AHU-1";
    }

    @Override
    public int incrementEventCount() {
      return 0;
    }

    @Override
    public void updateInterval(Integer sampleRateSec) {
    }

    @Override
    public void periodicUpdate() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void shutdown() {
    }

    @Override
    public void debug(String message) {
    }

    @Override
    public void info(String message) {
    }

    @Override
    public void warn(String message) {
    }

    @Override
    public void error(String message) {
    }

    @Override
    public void error(String message, Throwable e) {
    }
  }
}
