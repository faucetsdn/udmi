package daq.pubber.impl.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import udmi.lib.intf.FamilyProvider;
import udmi.lib.intf.ManagerHost;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointsetConfig;
import udmi.schema.PointsetEvents;
import udmi.schema.PubberConfiguration;
import udmi.schema.PubberOptions;

/**
 * Unit tests for {@link PubberPointsetManager} coalesced fastWrite COV updates.
 */
public class PubberPointsetManagerTest {

  @Test
  public void fastWriteCoalescesMultipleModifiedPointsIntoSingleImmediateUpdate()
      throws Exception {
    final RecordingHost host = new RecordingHost(1);
    PubberConfiguration configuration = new PubberConfiguration();
    configuration.deviceId = "AHU-1";
    configuration.options = new PubberOptions();
    configuration.options.fastWrite = true;

    PubberPointsetManager manager = new PubberPointsetManager(host, configuration);
    try {
      manager.setPointsetModel(null);

      PointsetConfig pointsetConfig = new PointsetConfig();
      pointsetConfig.points = new HashMap<>();
      pointsetConfig.points.put("recalcitrant_angle", makePointConfig(45));
      pointsetConfig.points.put("faulty_finding", makePointConfig(40));

      manager.updatePointsetPointsConfig(pointsetConfig);

      assertTrue("Expected immediate coalesced PointsetEvents publish without 1s delay",
          host.publishLatch.await(500, TimeUnit.MILLISECONDS));

      // Re-applying identical set_value entries must not emit an additional COV event.
      manager.updatePointsetPointsConfig(pointsetConfig);
    } finally {
      manager.shutdown();
    }

    assertEquals("Multiple modified points in one config update must coalesce to 1 publish",
        1, host.publishedEvents.size());
    PointsetEvents published = host.publishedEvents.get(0);
    assertEquals(45, published.points.get("recalcitrant_angle").present_value);
    assertEquals(40, published.points.get("faulty_finding").present_value);
  }

  private static PointPointsetConfig makePointConfig(Object setValue) {
    PointPointsetConfig config = new PointPointsetConfig();
    config.set_value = setValue;
    return config;
  }

  private static class RecordingHost implements ManagerHost {

    final List<PointsetEvents> publishedEvents = new CopyOnWriteArrayList<>();
    final CountDownLatch publishLatch;

    RecordingHost(int expectedPublishes) {
      this.publishLatch = new CountDownLatch(expectedPublishes);
    }

    @Override
    public void update(Object update) {
    }

    @Override
    public void publish(String targetId, Object message) {
      if (message instanceof PointsetEvents) {
        publishedEvents.add((PointsetEvents) message);
        publishLatch.countDown();
      }
    }

    @Override
    public FamilyProvider getLocalnetProvider(String family) {
      return null;
    }

    @Override
    public Date getStartTime() {
      return new Date();
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
    public void error(String message, Throwable e) {
    }
  }
}
