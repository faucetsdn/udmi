package udmi.lib.client.manager;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import udmi.lib.intf.ManagerHost;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryState;
import udmi.schema.FamilyDiscoveryConfig;

/**
 * Unit tests for DiscoveryManager scan timing.
 */
public class DiscoveryManagerTest {

  private static final String FAMILY = "bacnet";

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
    discoveryConfig.families = new HashMap<>(Map.of(FAMILY, familyConfig));

    DiscoveryManager manager = mock(DiscoveryManager.class, CALLS_REAL_METHODS);
    doReturn(discoveryConfig).when(manager).getDiscoveryConfig();
    doReturn(new DiscoveryState()).when(manager).getDiscoveryState();
    doReturn(mock(ManagerHost.class)).when(manager).getHost();
    doNothing().when(manager).info(anyString());
    doNothing().when(manager).postDiscoveryProcess(any(), any());
    doNothing().when(manager).startDiscoveryForFamily(any(), any(), any(), any());

    long startMs = System.currentTimeMillis();
    manager.startDiscoveryScan(FAMILY, new Date(startMs));

    ArgumentCaptor<Date> stopTime = ArgumentCaptor.forClass(Date.class);
    verify(manager).scheduleFuture(stopTime.capture(), any());
    return (stopTime.getValue().getTime() - startMs) / 1000;
  }
}
