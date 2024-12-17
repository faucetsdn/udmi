package daq.pubber;

import com.google.udmi.util.SiteModel;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import udmi.lib.ProtocolFamily;
import udmi.lib.client.LocalnetManager;
import udmi.lib.intf.FamilyProvider;
import udmi.lib.intf.ManagerHost;
import udmi.schema.LocalnetConfig;
import udmi.schema.LocalnetState;
import udmi.schema.PubberConfiguration;

/**
 * Container class for dealing with the localnet subblock of UDMI.
 */
public class PubberLocalnetManager extends PubberManager implements LocalnetManager {

  private final LocalnetState localnetState;
  private final Map<String, PubberFamilyProvider> localnetProviders;
  private LocalnetConfig localnetConfig;

  static Map<String, Class<? extends PubberFamilyProvider>> LOCALNET_PROVIDERS = Map.of(
      ProtocolFamily.VENDOR, PubberVendorProvider.class,
      ProtocolFamily.IPV_4, PubberIpProvider.class,
      ProtocolFamily.IPV_6, PubberIpProvider.class,
      ProtocolFamily.ETHER, PubberIpProvider.class,
      ProtocolFamily.BACNET, PubberBacnetProvider.class);

  /**
   * Create a new container with the given host.
   */
  public PubberLocalnetManager(ManagerHost host, PubberConfiguration configuration) {
    super(host, configuration);
    localnetState = new LocalnetState();
    localnetState.families = new HashMap<>();

    localnetProviders = new HashMap<>();
    LOCALNET_PROVIDERS.forEach((family, providerClass) -> {
      if (host instanceof Pubber || providerClass == PubberVendorProvider.class) {
        localnetProviders.put(family, instantiateProvider(family));
      }
    });
  }

  /**
   * Instantiate a family provider.
   */
  PubberFamilyProvider instantiateProvider(String family) {
    try {
      return LOCALNET_PROVIDERS.get(family).getDeclaredConstructor(
              ManagerHost.class, String.class, String.class)
          .newInstance(this, family, config.deviceId);
    } catch (Exception e) {
      throw new RuntimeException("While creating instance of " + LOCALNET_PROVIDERS.get(family), e);
    }
  }

  public void setSiteModel(SiteModel siteModel) {
    localnetProviders.forEach((key, value) -> value.setSiteModel(siteModel));
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
    // Silly type downgrade from PubberFamilyProvider to FamilyProvider.
    return localnetProviders.entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

}
