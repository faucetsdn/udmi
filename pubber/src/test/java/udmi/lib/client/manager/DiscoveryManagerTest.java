package udmi.lib.client.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static udmi.lib.client.manager.DiscoveryScan.forFamily;
import static udmi.schema.Category.DISCOVERY_FAMILY_SCAN;
import static udmi.schema.FamilyDiscoveryState.Phase.ACTIVE;
import static udmi.schema.FamilyDiscoveryState.Phase.PENDING;
import static udmi.schema.FamilyDiscoveryState.Phase.STOPPED;

import daq.pubber.impl.manager.PubberDiscoveryManager;
import daq.pubber.impl.provider.PubberBacnetProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import udmi.lib.intf.FamilyProvider;
import udmi.lib.intf.ManagerHost;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvents;
import udmi.schema.DiscoveryState;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.FeatureDiscovery;
import udmi.schema.Level;
import udmi.schema.PointPointsetModel;
import udmi.schema.PubberConfiguration;

/**
 * Unit tests for DiscoveryManager scan scheduling, including named scans.
 */
public class DiscoveryManagerTest {

  private static final String BACNET = "bacnet";
  private static final String VENDOR = "vendor";
  private static final Instant START_TIME = Instant.now().minusSeconds(60);

  private final FamilyProvider bacnetProvider = mock(FamilyProvider.class);
  private final FamilyProvider vendorProvider = mock(FamilyProvider.class);
  private final FakeDiscoveryManager manager = new FakeDiscoveryManager(
      Map.of(BACNET, bacnetProvider, VENDOR, vendorProvider));

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
    DiscoveryConfig discoveryConfig = new DiscoveryConfig();
    discoveryConfig.families = new HashMap<>(Map.of(BACNET, familyConfig));
    manager.setDiscoveryConfig(discoveryConfig);
    manager.setDiscoveryState(new DiscoveryState());

    long startMs = manager.now.toEpochMilli();
    manager.startDiscoveryScan(forFamily(BACNET), new Date(startMs));

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
  public void namedScanEventsCarryScanName() {
    manager.updateConfig(discovery(null, Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(0);

    assertEquals(ACTIVE, scanState("adhoc").phase);
    providerPublisher(bacnetProvider).accept("AHU-1", discovered("1042"));

    assertEquals(2, manager.published.size());
    manager.published.forEach(event -> {
      assertEquals(BACNET, event.family);
      assertEquals("adhoc", event.scan);
    });
  }

  @Test
  public void familyScanEventsHaveNoScanName() {
    manager.updateConfig(discovery(Map.of(BACNET, oneShotScan(null)), null));
    manager.runFuture(0);
    providerPublisher(bacnetProvider).accept("AHU-1", discovered("1042"));

    assertEquals(2, manager.published.size());
    manager.published.forEach(event -> {
      assertEquals(BACNET, event.family);
      assertNull(event.scan);
    });
  }

  @Test
  public void namedScanWithoutFamilyIsRejected() {
    manager.updateConfig(discovery(null, Map.of(
        "broken", oneShotScan(null),
        "adhoc", oneShotScan(BACNET))));

    assertScanError(scanState("broken"), "missing required family");
    assertEquals(PENDING, scanState("adhoc").phase);
  }

  @Test
  public void familiesEntryWithFamilyIsRejected() {
    manager.updateConfig(discovery(Map.of(BACNET, oneShotScan(BACNET)), null));

    assertScanError(manager.getDiscoveryState().families.get(BACNET), "must not specify family");
    assertTrue(manager.futures.isEmpty());
  }

  @Test
  public void namedScanWithUnsupportedFamilyIsRejected() {
    manager.updateConfig(discovery(null, Map.of("modbus", oneShotScan("modbus"))));

    assertScanError(scanState("modbus"), "not supported");
    assertTrue(manager.futures.isEmpty());
  }

  @Test
  public void secondScanOfBusyFamilyFails() {
    manager.updateConfig(discovery(Map.of(BACNET, oneShotScan(null)),
        Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(0);
    manager.runFuture(1);

    assertEquals(ACTIVE, manager.getDiscoveryState().families.get(BACNET).phase);
    assertScanError(scanState("adhoc"), "busy");
  }

  @Test
  public void busyPeriodicScanRetriesAtNextInterval() {
    manager.updateConfig(discovery(null, Map.of(
        "adhoc", oneShotScan(BACNET),
        "nightly", periodicScan(BACNET, 3600))));
    manager.runFuture(manager.futureIndexFor("adhoc"));
    final int adhocCompletion = manager.futures.size() - 1;
    final Date failedGeneration = scanState("nightly").generation;
    manager.now = failedGeneration.toInstant();
    manager.runFuture(manager.futureIndexFor("nightly"));

    assertScanError(scanState("nightly"), "busy");
    Date retryGeneration = Date.from(failedGeneration.toInstant().plusSeconds(3600));
    ScheduledEntry retry = manager.futures.get(manager.futures.size() - 1);
    assertEquals(retryGeneration, retry.time());

    manager.runFuture(adhocCompletion);
    manager.now = retryGeneration.toInstant().minusMillis(1);
    retry.task().run();

    assertEquals(PENDING, scanState("nightly").phase);
    assertEquals(retryGeneration, scanState("nightly").generation);
    assertNull(scanState("nightly").status);
    manager.runFuture(manager.futures.size() - 1);
    assertEquals(ACTIVE, scanState("nightly").phase);
  }

  @Test
  public void familyScanFailsWhenNamedScanHoldsFamily() {
    manager.updateConfig(discovery(Map.of(BACNET, oneShotScan(null)),
        Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(1);
    manager.runFuture(0);

    assertEquals(ACTIVE, scanState("adhoc").phase);
    assertScanError(manager.getDiscoveryState().families.get(BACNET), "busy");
  }

  @Test
  public void familiesEntryWithUnsupportedFamilyDoesNotErrorAndLeavesScansNull() {
    manager.updateConfig(discovery(Map.of("modbus", oneShotScan(null)), null));

    FamilyDiscoveryState modbusState = manager.getDiscoveryState().families.get("modbus");
    assertEquals(PENDING, modbusState.phase);
    assertNull(modbusState.status);
    assertNull(manager.getDiscoveryState().scans);
    assertFalse(manager.sawEmptyScansMap);

    manager.runFuture(0);
    assertEquals(ACTIVE, modbusState.phase);
    assertNull(modbusState.status);

    manager.runFuture(1);
    assertEquals(STOPPED, modbusState.phase);
    assertNull(modbusState.status);
  }

  @Test
  public void nullDiscoveryConfigStopsActiveScansAndClearsState() {
    manager.updateConfig(discovery(Map.of(VENDOR, oneShotScan(null)),
        Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(0);
    manager.runFuture(1);
    assertEquals(ACTIVE, manager.getDiscoveryState().families.get(VENDOR).phase);
    assertEquals(ACTIVE, scanState("adhoc").phase);

    manager.updateConfig(null);

    verify(vendorProvider).stopScan();
    verify(bacnetProvider).stopScan();
    assertTrue(manager.getActiveScanFamilies().isEmpty());
    assertTrue(manager.getScanSchedules().isEmpty());
    assertNull(manager.getDiscoveryState());
  }

  @Test
  public void nullMapEntryValuesHandledSafely() {
    Map<String, FamilyDiscoveryConfig> families = new HashMap<>();
    families.put(BACNET, null);
    Map<String, FamilyDiscoveryConfig> scans = new HashMap<>();
    scans.put("adhoc", null);
    scans.put("ok", oneShotScan(BACNET));

    manager.updateConfig(discovery(families, scans));

    assertFalse(manager.getDiscoveryState().families.containsKey(BACNET));
    assertScanError(scanState("adhoc"), "missing required family");
    assertEquals(PENDING, scanState("ok").phase);
  }

  @Test
  public void nextPeriodicStartNeverReturnsLastRunWhenNowBeforeLastRun() {
    Date generation = Date.from(START_TIME);
    Date lastRun = Date.from(START_TIME.plusSeconds(60));
    Instant slightlyBeforeLastRun = lastRun.toInstant().minusMillis(5);
    assertEquals(Date.from(START_TIME.plusSeconds(120)), DiscoveryManager.nextPeriodicStart(
        generation, 60, lastRun, slightlyBeforeLastRun));
  }

  @Test
  public void cancellingOrFixingErroredScanClearsStaleErrorStatus() {
    manager.updateConfig(discovery(Map.of(BACNET, oneShotScan(BACNET)),
        Map.of("adhoc", oneShotScan(null))));
    assertScanError(manager.getDiscoveryState().families.get(BACNET), "must not specify family");
    assertScanError(scanState("adhoc"), "missing required family");

    FamilyDiscoveryConfig cancelled = oneShotScan(BACNET);
    cancelled.generation = Date.from(START_TIME.minusSeconds(10));
    manager.updateConfig(discovery(Map.of(BACNET, oneShotScan(null)),
        Map.of("adhoc", cancelled)));

    FamilyDiscoveryState fixedFamily = manager.getDiscoveryState().families.get(BACNET);
    assertEquals(PENDING, fixedFamily.phase);
    assertNull(fixedFamily.status);

    FamilyDiscoveryState cancelledScan = scanState("adhoc");
    assertEquals(STOPPED, cancelledScan.phase);
    assertEquals(cancelled.generation, cancelledScan.generation);
    assertNull(cancelledScan.status);
  }

  @Test
  public void futurePeriodicGenerationStartsAtGeneration() {
    FamilyDiscoveryConfig nightly = periodicScan(BACNET, 3600);
    nightly.generation = Date.from(manager.now.plusSeconds(9000));
    manager.updateConfig(discovery(null, Map.of("nightly", nightly)));

    assertEquals(PENDING, scanState("nightly").phase);
    assertEquals(nightly.generation, scanState("nightly").generation);
    assertEquals(nightly.generation, manager.futures.get(0).time());
  }

  @Test
  public void activePeriodicScanIgnoresReappliedConfig() {
    manager.updateConfig(discovery(null, Map.of("nightly", periodicScan(BACNET, 3600))));
    Date slot = scanState("nightly").generation;
    manager.now = slot.toInstant();
    manager.runFuture(0);
    assertEquals(ACTIVE, scanState("nightly").phase);
    final int scheduled = manager.futures.size();

    manager.updateConfig(discovery(null, Map.of("nightly", periodicScan(BACNET, 3600))));

    assertEquals(ACTIVE, scanState("nightly").phase);
    assertEquals(slot, scanState("nightly").generation);
    assertEquals(scheduled, manager.futures.size());

    manager.now = slot.toInstant().plusSeconds(DiscoveryManager.SCAN_DURATION_SEC);
    manager.runFuture(scheduled - 1);
    verify(bacnetProvider).stopScan();
    assertEquals(2, manager.published.size());
    assertEquals(PENDING, scanState("nightly").phase);
    assertEquals(slot.toInstant().plusSeconds(3600), scanState("nightly").generation.toInstant());
  }

  @Test
  public void periodicStartRunsAtGenerationWhenAfterStartTime() {
    Date generation = Date.from(START_TIME.plusSeconds(10));
    Instant now = START_TIME.plusSeconds(12);
    assertEquals(generation, DiscoveryManager.nextPeriodicStart(
        generation, 60, Date.from(START_TIME), now));
  }

  @Test
  public void periodicStartWaitsForNextSlotWhenGenerationAtOrBeforeStartTime() {
    Date generation = Date.from(START_TIME);
    Instant now = START_TIME.plusSeconds(10);
    assertEquals(Date.from(START_TIME.plusSeconds(60)), DiscoveryManager.nextPeriodicStart(
        generation, 60, Date.from(START_TIME), now));
  }

  @Test
  public void periodicStartCatchesUpSlotAfterLateCompletion() {
    Date generation = Date.from(START_TIME);
    Instant lateCompletion = START_TIME.plusSeconds(60).plusMillis(300);
    assertEquals(Date.from(START_TIME.plusSeconds(60)), DiscoveryManager.nextPeriodicStart(
        generation, 60, generation, lateCompletion));
  }

  @Test
  public void periodicPendingSlotStaysNoOpPastSlotBoundary() {
    FamilyDiscoveryConfig nightly = periodicScan(BACNET, 60);
    nightly.generation = Date.from(START_TIME.plusSeconds(10));
    manager.now = START_TIME.plusSeconds(5);
    manager.updateConfig(discovery(null, Map.of("nightly", nightly)));
    assertEquals(nightly.generation, scanState("nightly").generation);

    manager.now = START_TIME.plusSeconds(10).plusMillis(20);
    manager.updateConfig(discovery(null, Map.of("nightly", nightly)));

    assertEquals(1, manager.futures.size());
    assertEquals(PENDING, scanState("nightly").phase);
    assertEquals(nightly.generation, scanState("nightly").generation);
  }

  @Test
  public void activeFamilyScanBecomingInvalidStopsProviderAndReleasesFamily() {
    manager.updateConfig(discovery(Map.of(BACNET, oneShotScan(null)), null));
    manager.runFuture(0);
    assertEquals(ACTIVE, manager.getDiscoveryState().families.get(BACNET).phase);

    manager.updateConfig(discovery(Map.of(BACNET, oneShotScan(BACNET)),
        Map.of("adhoc", oneShotScan(BACNET))));

    assertScanError(manager.getDiscoveryState().families.get(BACNET), "must not specify family");
    verify(bacnetProvider).stopScan();
    assertTrue(manager.getActiveScanFamilies().isEmpty());
    manager.runFuture(manager.futures.size() - 1);
    assertEquals(ACTIVE, scanState("adhoc").phase);
  }

  @Test
  public void lateProviderCallbackAfterRemovalOrCompletionIsIgnored() {
    manager.updateConfig(discovery(null, Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(0);
    BiConsumer<String, DiscoveryEvents> publisher = providerPublisher(bacnetProvider);
    assertEquals(1, manager.published.size());

    manager.updateConfig(discovery(null, Map.of()));
    publisher.accept("AHU-1", discovered("1042"));

    assertEquals(1, manager.published.size());
    assertFalse(manager.getDiscoveryState().scans.containsKey("adhoc"));
  }

  @Test
  public void busyErrorPreservedAcrossUnrelatedConfigUpdateUntilRetryOrReschedule() {
    manager.updateConfig(discovery(null, Map.of(
        "adhoc", oneShotScan(BACNET),
        "nightly", periodicScan(BACNET, 3600))));
    manager.runFuture(manager.futureIndexFor("adhoc"));
    final Date failedGeneration = scanState("nightly").generation;
    manager.now = failedGeneration.toInstant();
    manager.runFuture(manager.futureIndexFor("nightly"));
    assertScanError(scanState("nightly"), "busy");
    final int futuresAfterBusy = manager.futures.size();

    manager.now = failedGeneration.toInstant().plusSeconds(5);
    manager.updateConfig(discovery(null, Map.of(
        "adhoc", oneShotScan(BACNET),
        "nightly", periodicScan(BACNET, 3600),
        "vendor_scan", oneShotScan(VENDOR))));

    assertScanError(scanState("nightly"), "busy");
    assertEquals(failedGeneration, scanState("nightly").generation);
    assertEquals(futuresAfterBusy + 1, manager.futures.size());

    manager.updateConfig(discovery(null, Map.of(
        "adhoc", oneShotScan(BACNET),
        "nightly", periodicScan(BACNET, 1800))));
    assertEquals(PENDING, scanState("nightly").phase);
    assertNull(scanState("nightly").status);
    assertEquals(Date.from(failedGeneration.toInstant().plusSeconds(1800)),
        scanState("nightly").generation);
  }

  @Test
  public void removingActiveScanStopsItAndRemovesState() {
    manager.updateConfig(discovery(null, Map.of(
        "adhoc", oneShotScan(BACNET),
        "nightly", periodicScan(VENDOR, 3600))));
    manager.runFuture(manager.futureIndexFor("adhoc"));

    manager.updateConfig(discovery(null, Map.of("nightly", periodicScan(VENDOR, 3600))));

    assertFalse(manager.getDiscoveryState().scans.containsKey("adhoc"));
    assertEquals(PENDING, scanState("nightly").phase);
    verify(bacnetProvider).stopScan();
  }

  @Test
  public void changingScanFamilyRestartsScan() {
    manager.updateConfig(discovery(null, Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(0);
    final FamilyDiscoveryState original = scanState("adhoc");

    FamilyDiscoveryConfig changed = oneShotScan(VENDOR);
    changed.generation = manager.getDiscoveryConfig().scans.get("adhoc").generation;
    manager.updateConfig(discovery(null, Map.of("adhoc", changed)));

    verify(bacnetProvider).stopScan();
    assertNotSame(original, scanState("adhoc"));
    assertEquals(PENDING, scanState("adhoc").phase);
    manager.runFuture(manager.futures.size() - 1);
    verify(vendorProvider).startScan(any(), any());
  }

  @Test
  public void staleCompletionOfReplacedScanIsIgnored() {
    manager.updateConfig(discovery(null, Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(0);
    final int completion = manager.futures.size() - 1;

    FamilyDiscoveryConfig changed = oneShotScan(VENDOR);
    changed.generation = manager.getDiscoveryConfig().scans.get("adhoc").generation;
    manager.updateConfig(discovery(null, Map.of("adhoc", changed)));
    assertTrue(manager.futures.get(completion).isCancelled());
    manager.runFuture(manager.futures.size() - 1);
    manager.futures.get(completion).task().run();

    assertEquals(ACTIVE, scanState("adhoc").phase);
    verify(vendorProvider, never()).stopScan();
  }

  @Test
  public void identicalConfigIsNoOp() {
    manager.updateConfig(discovery(null, Map.of("nightly", periodicScan(BACNET, 3600))));
    Date generation = scanState("nightly").generation;

    manager.updateConfig(discovery(null, Map.of("nightly", periodicScan(BACNET, 3600))));

    assertEquals(1, manager.futures.size());
    assertEquals(generation, scanState("nightly").generation);
  }

  @Test
  public void reschedulingPendingOneShotToEarlierPostStartGenerationRunsIt() {
    FamilyDiscoveryConfig distant = oneShotScan(BACNET);
    distant.generation = Date.from(START_TIME.plusSeconds(3600));
    manager.updateConfig(discovery(null, Map.of("adhoc", distant)));
    assertEquals(PENDING, scanState("adhoc").phase);
    assertEquals(distant.generation, scanState("adhoc").generation);

    FamilyDiscoveryConfig sooner = oneShotScan(BACNET);
    sooner.generation = Date.from(START_TIME.plusSeconds(60));
    manager.updateConfig(discovery(null, Map.of("adhoc", sooner)));

    assertTrue(manager.futures.get(0).isCancelled());
    assertEquals(PENDING, scanState("adhoc").phase);
    assertEquals(sooner.generation, scanState("adhoc").generation);
    manager.runFuture(manager.futureIndexFor("adhoc"));
    assertEquals(ACTIVE, scanState("adhoc").phase);
  }

  @Test
  public void providerStartAndStopExceptionsAreIsolatedAndReleaseFamily() {
    doThrow(new RuntimeException("boom start")).when(bacnetProvider).startScan(any(), any());
    doThrow(new RuntimeException("boom stop")).when(vendorProvider).stopScan();

    FamilyDiscoveryConfig vendorScan = oneShotScan(VENDOR);
    vendorScan.generation = Date.from(START_TIME.plusSeconds(45));
    manager.updateConfig(discovery(null, Map.of(
        "bad_bacnet", oneShotScan(BACNET),
        "vendor_scan", vendorScan)));
    assertEquals(1, manager.stateUpdates);

    manager.runFuture(manager.futureIndexFor("bad_bacnet"));
    assertScanError(scanState("bad_bacnet"), "failed to start");
    assertFalse(manager.getActiveScanFamilies().containsValue(BACNET));

    manager.runFuture(manager.futureIndexFor("vendor_scan"));
    assertEquals(ACTIVE, scanState("vendor_scan").phase);
    manager.updateConfig(null);
    assertNull(manager.getDiscoveryState());
    assertTrue(manager.getActiveScanFamilies().isEmpty());
  }

  @Test
  public void pubberDiscoveryManagerRejectsLocalnetOnlyProviderAndStopsOnShutdown() {
    FamilyProvider ipOnlyProvider = new FamilyProvider() {};
    PubberBacnetProvider scanCapableProvider = mock(PubberBacnetProvider.class);
    ManagerHost host = mock(ManagerHost.class, invocation -> switch (invocation.getMethod()
        .getName()) {
      case "getLocalnetProvider" -> switch ((String) invocation.getArgument(0)) {
        case "ipv4" -> ipOnlyProvider;
        case BACNET -> scanCapableProvider;
        default -> null;
      };
      case "getStartTime" -> Date.from(START_TIME);
      default -> null;
    });
    PubberConfiguration pubberConfig = new PubberConfiguration();
    pubberConfig.deviceId = "AHU-1";
    PubberDiscoveryManager pubberManager = new PubberDiscoveryManager(host, pubberConfig, null);
    FamilyDiscoveryConfig adhocScan = oneShotScan(BACNET);
    adhocScan.generation = Date.from(Instant.now().plusSeconds(30));
    try {
      assertNull(pubberManager.discoveryProvider("ipv4"));
      assertEquals(scanCapableProvider, pubberManager.discoveryProvider(BACNET));

      pubberManager.updateConfig(discovery(null, Map.of(
          "ip_scan", oneShotScan("ipv4"),
          "adhoc", adhocScan)));
      assertScanError(pubberManager.getDiscoveryState().scans.get("ip_scan"), "not supported");
      assertEquals(PENDING, pubberManager.getDiscoveryState().scans.get("adhoc").phase);

      pubberManager.startDiscoveryScan(DiscoveryScan.forScan("adhoc"), adhocScan.generation);
      assertEquals(ACTIVE, pubberManager.getDiscoveryState().scans.get("adhoc").phase);
      verify(scanCapableProvider).startScan(any(), any());
    } finally {
      pubberManager.shutdown();
    }
    verify(scanCapableProvider).stopScan();
    assertTrue(pubberManager.getActiveScanFamilies().isEmpty());
    assertTrue(pubberManager.getScanSchedules().isEmpty());
  }

  @Test
  public void updateStateRecoversAfterException() {
    manager.throwOnNextUpdateState = true;
    try {
      manager.updateConfig(discovery(null, Map.of("adhoc", oneShotScan(BACNET))));
    } catch (RuntimeException expected) {
      assertEquals("simulated state failure", expected.getMessage());
    }
    assertEquals(0, manager.getStateUpdateCount().get());

    final int before = manager.stateUpdates;
    manager.updateState();
    assertEquals(before + 1, manager.stateUpdates);
  }

  @Test
  public void removingIntervalOrRevertingPendingToLastRunCancelsPendingFuture() {
    Date t0 = Date.from(START_TIME.plusSeconds(30));
    FamilyDiscoveryConfig nightly = periodicScan(BACNET, 3600);
    nightly.generation = t0;
    manager.now = START_TIME;
    manager.updateConfig(discovery(null, Map.of("nightly", nightly)));

    manager.now = t0.toInstant();
    manager.runFuture(0);
    BiConsumer<String, DiscoveryEvents> publisher = providerPublisher(bacnetProvider);
    publisher.accept("dev-1", discovered("1001"));
    assertEquals(Integer.valueOf(1), scanState("nightly").active_count);

    manager.now = t0.toInstant().plusSeconds(DiscoveryManager.SCAN_DURATION_SEC);
    manager.runFuture(1);
    assertEquals(PENDING, scanState("nightly").phase);
    final int nextLoopFuture = manager.futures.size() - 1;
    assertFalse(manager.futures.get(nextLoopFuture).isCancelled());

    FamilyDiscoveryConfig stoppedLoop = oneShotScan(BACNET);
    stoppedLoop.generation = t0;
    manager.updateConfig(discovery(null, Map.of("nightly", stoppedLoop)));

    assertTrue(manager.futures.get(nextLoopFuture).isCancelled());
    assertEquals(STOPPED, scanState("nightly").phase);
    assertEquals(t0, scanState("nightly").generation);
    assertNull(scanState("nightly").active_count);

    FamilyDiscoveryConfig futureScan = oneShotScan(VENDOR);
    futureScan.generation = Date.from(START_TIME.plusSeconds(120));
    manager.updateConfig(discovery(null, Map.of("adhoc", futureScan)));
    final int adhocFuture = manager.futureIndexFor("adhoc");
    FamilyDiscoveryConfig reverted = oneShotScan(VENDOR);
    reverted.generation = Date.from(START_TIME);
    manager.updateConfig(discovery(null, Map.of("adhoc", reverted)));

    assertTrue(manager.futures.get(adhocFuture).isCancelled());
    assertEquals(STOPPED, scanState("adhoc").phase);
    assertEquals(reverted.generation, scanState("adhoc").generation);
  }

  @Test
  public void removingIntervalCancelsBusyRetryAndErrorClearsStaleActiveCount() {
    Date t0 = Date.from(START_TIME.plusSeconds(30));
    FamilyDiscoveryConfig nightly = periodicScan(BACNET, 60);
    nightly.generation = t0;
    manager.now = START_TIME;
    manager.updateConfig(discovery(null, Map.of("nightly", nightly)));

    manager.now = t0.toInstant();
    manager.runFuture(0);
    providerPublisher(bacnetProvider).accept("dev-1", discovered("1001"));
    manager.now = t0.toInstant().plusSeconds(DiscoveryManager.SCAN_DURATION_SEC);
    manager.runFuture(1);
    assertEquals(Integer.valueOf(1), scanState("nightly").active_count);
    final int secondSlotStart = manager.futures.size() - 1;

    FamilyDiscoveryConfig blocker = oneShotScan(BACNET);
    blocker.generation = Date.from(START_TIME.plusSeconds(85));
    manager.updateConfig(discovery(null, Map.of("nightly", nightly, "blocker", blocker)));
    manager.now = blocker.generation.toInstant();
    manager.runFuture(manager.futureIndexFor("blocker"));

    Date secondSlot = Date.from(t0.toInstant().plusSeconds(60));
    manager.now = secondSlot.toInstant();
    manager.runFuture(secondSlotStart);
    assertScanError(scanState("nightly"), "busy");
    assertNull(scanState("nightly").active_count);
    final int busyRetryFuture = manager.futures.size() - 1;
    assertFalse(manager.futures.get(busyRetryFuture).isCancelled());

    FamilyDiscoveryConfig noInterval = oneShotScan(BACNET);
    noInterval.generation = secondSlot;
    manager.updateConfig(discovery(null, Map.of("nightly", noInterval, "blocker", blocker)));
    assertTrue(manager.futures.get(busyRetryFuture).isCancelled());
    assertScanError(scanState("nightly"), "busy");
  }

  @Test
  public void staleCheckOrDuplicateStartDoesNotLeakActiveFamily() {
    Date generation = Date.from(START_TIME.plusSeconds(30));
    manager.updateConfig(discovery(Map.of(BACNET, oneShotScan(null)), null));
    manager.updateConfig(null);

    manager.checkDiscoveryScan(forFamily(BACNET), generation);
    manager.startDiscoveryScan(forFamily(BACNET), generation);
    assertTrue(manager.getActiveScanFamilies().isEmpty());
    assertTrue(manager.published.isEmpty());

    manager.updateConfig(discovery(null, Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(manager.futureIndexFor("adhoc"));
    assertEquals(1, manager.published.size());

    manager.startDiscoveryScan(DiscoveryScan.forScan("adhoc"), generation);
    assertEquals(1, manager.published.size());
  }

  @Test
  public void startScanRemovalOrReplacementDoesNotResurrectOrStopReplacement() {
    doAnswer(invocation -> {
      manager.updateConfig(discovery(null, null));
      throw new RuntimeException("aborted on stop");
    }).when(bacnetProvider).startScan(any(), any());

    manager.updateConfig(discovery(null, Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(manager.futureIndexFor("adhoc"));

    assertNull(manager.getDiscoveryState().scans);
    assertTrue(manager.getActiveScanFamilies().isEmpty());

    FamilyDiscoveryConfig replacement = oneShotScan(VENDOR);
    replacement.generation = Date.from(START_TIME.plusSeconds(60));
    AtomicInteger starts = new AtomicInteger();
    doAnswer(invocation -> {
      if (starts.getAndIncrement() == 0) {
        manager.updateConfig(discovery(null, Map.of("other", replacement)));
        manager.runFuture(manager.futureIndexFor("other"));
      }
      return null;
    }).when(vendorProvider).startScan(any(), any());

    FamilyDiscoveryConfig first = oneShotScan(VENDOR);
    manager.updateConfig(discovery(null, Map.of("adhoc", first)));
    manager.runFuture(manager.futureIndexFor("adhoc"));

    assertFalse(manager.getDiscoveryState().scans.containsKey("adhoc"));
    assertEquals(ACTIVE, scanState("other").phase);
    assertEquals(2, starts.get());
    verify(vendorProvider).stopScan();
  }

  @Test
  public void periodicProviderStartFailurePreservesErrorOnReapplyAndSeparateNamespacesWork() {
    doThrow(new RuntimeException("boom")).when(bacnetProvider).startScan(any(), any());
    Date t0 = Date.from(START_TIME.plusSeconds(30));
    FamilyDiscoveryConfig nightly = periodicScan(BACNET, 3600);
    nightly.generation = t0;
    manager.now = START_TIME;
    manager.updateConfig(discovery(null, Map.of("nightly", nightly)));

    manager.now = t0.toInstant();
    manager.runFuture(0);
    assertScanError(scanState("nightly"), "failed to start");
    final int retryIdx = manager.futures.size() - 1;

    manager.now = t0.toInstant().plusSeconds(1);
    manager.updateConfig(discovery(null, Map.of("nightly", nightly)));
    assertScanError(scanState("nightly"), "failed to start");
    assertFalse(manager.futures.get(retryIdx).isCancelled());

    Date retryAt = Date.from(t0.toInstant().plusSeconds(3600));
    manager.now = retryAt.toInstant();
    manager.runFuture(retryIdx);
    assertEquals(PENDING, scanState("nightly").phase);
    assertEquals(retryAt, scanState("nightly").generation);

    // A named scan may share its key name ("bacnet") with families.bacnet.
    manager.updateConfig(discovery(
        Map.of(BACNET, oneShotScan(null)),
        Map.of(BACNET, oneShotScan(VENDOR))));
    assertEquals(PENDING, manager.getDiscoveryState().families.get(BACNET).phase);
    assertEquals(PENDING, scanState(BACNET).phase);
  }

  private FamilyDiscoveryState scanState(String name) {
    return manager.getDiscoveryState().scans.get(name);
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
    ArgumentCaptor<BiConsumer<String, DiscoveryEvents>> publisher =
        ArgumentCaptor.forClass(BiConsumer.class);
    verify(provider).startScan(any(), publisher.capture());
    return publisher.getValue();
  }

  private static DiscoveryEvents discovered(String addr) {
    DiscoveryEvents event = new DiscoveryEvents();
    event.addr = addr;
    return event;
  }

  private static DiscoveryConfig discovery(Map<String, FamilyDiscoveryConfig> families,
      Map<String, FamilyDiscoveryConfig> scans) {
    DiscoveryConfig config = new DiscoveryConfig();
    config.families = families == null ? null : new HashMap<>(families);
    config.scans = scans == null ? null : new HashMap<>(scans);
    return config;
  }

  private static FamilyDiscoveryConfig oneShotScan(String family) {
    FamilyDiscoveryConfig config = new FamilyDiscoveryConfig();
    config.family = family;
    config.generation = Date.from(START_TIME.plusSeconds(30));
    return config;
  }

  private static FamilyDiscoveryConfig periodicScan(String family, int intervalSec) {
    FamilyDiscoveryConfig config = new FamilyDiscoveryConfig();
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

  /**
   * Minimal in-memory manager that records scheduled futures instead of running them.
   */
  private static class FakeDiscoveryManager implements DiscoveryManager {

    final List<ScheduledEntry> futures = new ArrayList<>();
    final List<DiscoveryEvents> published = new ArrayList<>();
    private final Map<DiscoveryScan, String> activeScanFamilies = new HashMap<>();
    private final Map<DiscoveryScan, ScanSchedule> scanSchedules = new ConcurrentHashMap<>();
    private final AtomicInteger stateUpdateCount = new AtomicInteger();
    private final ManagerHost host;
    Instant now = Instant.now();
    boolean sawEmptyScansMap;
    boolean throwOnNextUpdateState;
    int stateUpdates;
    private volatile DiscoveryState discoveryState;
    private volatile DiscoveryConfig discoveryConfig;

    FakeDiscoveryManager(Map<String, FamilyProvider> providers) {
      host = mock(ManagerHost.class, invocation -> switch (invocation.getMethod().getName()) {
        case "getLocalnetProvider" -> providers.get((String) invocation.getArgument(0));
        case "getStartTime" -> Date.from(START_TIME);
        case "publish" -> {
          if (invocation.getArguments().length == 1) {
            published.add(invocation.getArgument(0));
          }
          yield null;
        }
        default -> null;
      });
    }

    void runFuture(int index) {
      ScheduledEntry entry = futures.get(index);
      assertFalse("Scheduled future was cancelled", entry.isCancelled());
      entry.task().run();
      assertFalse("Scheduled future cancelled itself while firing", entry.isCancelled());
    }

    int futureIndexFor(String scanName) {
      Date generation = getDiscoveryState().scans.get(scanName).generation;
      for (int i = 0; i < futures.size(); i++) {
        if (!futures.get(i).isCancelled() && futures.get(i).time().equals(generation)) {
          return i;
        }
      }
      throw new IllegalStateException("No future scheduled for scan " + scanName);
    }

    @Override
    public Instant getScanNow() {
      return now;
    }

    @Override
    public Map<DiscoveryScan, String> getActiveScanFamilies() {
      return activeScanFamilies;
    }

    @Override
    public Map<DiscoveryScan, ScanSchedule> getScanSchedules() {
      return scanSchedules;
    }

    @Override
    public AtomicInteger getStateUpdateCount() {
      return stateUpdateCount;
    }

    @Override
    public ScheduledFuture<?> scheduleFuture(Date startGeneration, Runnable runnable) {
      ScheduledEntry entry = new ScheduledEntry(startGeneration, runnable);
      futures.add(entry);
      return entry.future();
    }

    @Override
    public DiscoveryState getDiscoveryState() {
      return discoveryState;
    }

    @Override
    public void setDiscoveryState(DiscoveryState discoveryState) {
      this.discoveryState = discoveryState;
    }

    @Override
    public DiscoveryConfig getDiscoveryConfig() {
      return discoveryConfig;
    }

    @Override
    public void setDiscoveryConfig(DiscoveryConfig discoveryConfig) {
      this.discoveryConfig = discoveryConfig;
    }

    @Override
    public ManagerHost getHost() {
      return host;
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
      return null;
    }

    @Override
    public void postDiscoveryProcess(String deviceId, DiscoveryEvents discoveryEvents) {
    }

    @Override
    public void updateState(Object state) {
      if (throwOnNextUpdateState) {
        throwOnNextUpdateState = false;
        throw new RuntimeException("simulated state failure");
      }
      stateUpdates++;
      if (state instanceof DiscoveryState ds && ds.scans != null && ds.scans.isEmpty()
          && (discoveryConfig == null || discoveryConfig.scans == null)) {
        sawEmptyScansMap = true;
      }
    }

    @Override
    public void error(String message) {
    }

    @Override
    public void error(String message, Throwable e) {
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
    public String getDeviceId() {
      return "AHU-1";
    }

    @Override
    public int incrementEventCount() {
      return 0;
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

  }
}
