package daq.pubber;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import udmi.lib.intf.FamilyProvider;
import udmi.lib.base.ManagerBase;
import udmi.lib.intf.ManagerHost;
import udmi.lib.client.LocalnetManager;
import udmi.schema.LocalnetConfig;
import udmi.schema.LocalnetState;
import udmi.schema.PubberConfiguration;

/**
 * Container class for dealing with the localnet subblock of UDMI.
 */
public class PubberLocalnetManager extends ManagerBase implements LocalnetManager {

  private final LocalnetState localnetState;
  private final Map<String, FamilyProvider> localnetProviders;
  private LocalnetConfig localnetConfig;

  /**
   * Create a new container with the given host.
   */
  public PubberLocalnetManager(ManagerHost host, PubberConfiguration configuration) {
    super(host, configuration);
    localnetState = new LocalnetState();
    localnetState.families = new HashMap<>();
    localnetProviders = LOCALNET_PROVIDERS
        .keySet().stream().collect(Collectors.toMap(family -> family, this::instantiateProvider));
  }


  @Override
  public LocalnetState getLocalnetState() {
    return this.localnetState;
  }


  @Override
  public LocalnetConfig getLocalnetConfig() {
    return localnetConfig;
  }

  @Override
  public void setLocalnetConfig(LocalnetConfig localnetConfig) {
    this.localnetConfig = localnetConfig;
  }

  @Override
  public Map<String, FamilyProvider> getLocalnetProviders() {
    return localnetProviders;
  }

}
