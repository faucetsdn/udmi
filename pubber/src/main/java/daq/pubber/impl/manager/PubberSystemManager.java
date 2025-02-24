package daq.pubber.impl.manager;

import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static java.lang.String.format;

import daq.pubber.impl.PubberManager;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import udmi.lib.client.host.PublisherHost;
import udmi.lib.client.manager.SystemManager;
import udmi.lib.intf.ManagerHost;
import udmi.schema.Entry;
import udmi.schema.Metadata;
import udmi.schema.Operation.SystemMode;
import udmi.schema.PubberConfiguration;
import udmi.schema.SystemConfig;

/**
 * Support manager for system stuff.
 */
public class PubberSystemManager extends PubberManager implements SystemManager {

  private final List<Entry> logs = new ArrayList<>();
  private final AtomicBoolean publishLock = new AtomicBoolean(false);
  private final ExtraSystemState systemState = new ExtraSystemState();

  private SystemConfig systemConfig;

  /**
   * New instance.
   */
  public PubberSystemManager(ManagerHost host, PubberConfiguration configuration) {
    super(host, configuration);
    initialize(host, configuration.serialNo);
  }

  @Override
  public ExtraSystemState getSystemState() {
    return systemState;
  }

  @Override
  public SystemConfig getSystemConfig() {
    return systemConfig;
  }

  @Override
  public void setSystemConfig(SystemConfig systemConfig) {
    this.systemConfig = systemConfig;
  }

  @Override
  public List<Entry> getLogs() {
    return logs;
  }

  @Override
  public AtomicBoolean getPublishLock() {
    return publishLock;
  }

  @Override
  public void shutdown() {
    super.shutdown();
    SystemManager.super.shutdown();
  }

  @Override
  public void periodicUpdate() {
    SystemManager.super.periodicUpdate();
  }

  @Override
  public void systemLifecycle(SystemMode mode) {
    systemState.operation.mode = mode;
    try {
      host.update(null);
    } catch (Exception e) {
      error("Squashing error publishing state while shutting down", e);
    }
    int exitCode = EXIT_CODE_MAP.getOrDefault(mode, UNKNOWN_MODE_EXIT_CODE);
    error(
        format("Stopping system with extreme prejudice, restart %s with code %s", mode, exitCode));
    System.exit(exitCode);
  }

  @Override
  public void updateConfig(SystemConfig system, Date timestamp) {
    ((SystemManager) this).updateConfig(system, timestamp);

    // Hack here for testing. This just indicates that it's "ok" to do some wonky stuff a bit
    // after initial startup (after initial config synchronization).
    configSynchronized = true;
  }

  @Override
  public void setHardwareSoftware(Metadata metadata) {
    SystemManager.super.setHardwareSoftware(metadata);
    if (getHost() instanceof PublisherHost) {
      ExtraSystemState state = getSystemState();
      ifNullThen(state.hardware.make, () -> state.hardware.make = "bos");
      ifNullThen(state.hardware.model, () -> state.hardware.model = "pubber");
      ifNullThen(state.software, () -> {
        state.software = new HashMap<>();
        state.software.put(DEFAULT_SOFTWARE_KEY, "v1");
      });
    }
  }
}
