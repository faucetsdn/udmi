package daq.pubber;

import static java.util.Optional.ofNullable;

import udmi.lib.base.ManagerBase;
import udmi.lib.intf.ManagerHost;
import udmi.schema.PubberConfiguration;
import udmi.schema.PubberOptions;

/**
 * Common manager class for all Pubber managers. Mainly just holds on to configuration and options.
 */
public class PubberManager extends ManagerBase {

  protected final PubberConfiguration config;
  protected final PubberOptions options;

  /**
   * New instance aye.
   */
  public PubberManager(ManagerHost host, PubberConfiguration configuration) {
    super(host, configuration.deviceId);
    config = configuration;
    options = configuration.options;
  }

  @Override
  protected int getIntervalSec(Integer sampleRateSec) {
    return (int) ofNullable(options.fixedSampleRate).orElse(super.getIntervalSec(sampleRateSec));
  }
}
