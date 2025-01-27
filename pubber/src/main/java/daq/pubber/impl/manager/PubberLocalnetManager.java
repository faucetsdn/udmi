package daq.pubber.impl.manager;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static java.lang.String.format;

import com.google.udmi.util.SiteModel;
import daq.pubber.impl.PubberManager;
import daq.pubber.impl.provider.PubberBacnetProvider;
import daq.pubber.impl.provider.PubberFamilyProvider;
import daq.pubber.impl.provider.PubberIpProvider;
import daq.pubber.impl.provider.PubberVendorProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import udmi.lib.ProtocolFamily;
import udmi.lib.client.host.PublisherHost;
import udmi.lib.client.manager.LocalnetManager;
import udmi.lib.intf.FamilyProvider;
import udmi.lib.intf.ManagerHost;
import udmi.schema.LocalnetConfig;
import udmi.schema.LocalnetState;
import udmi.schema.Metadata;
import udmi.schema.PubberConfiguration;

/**
 * Container class for dealing with the localnet subblock of UDMI.
 */
public class PubberLocalnetManager extends PubberManager implements LocalnetManager {

  private final Map<String, PubberFamilyProvider> localnetProviders;
  private final LocalnetState localnetState;
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
  public LocalnetState getLocalnetState() {
    return this.localnetState;
  }

  @Override
  public Map<String, FamilyProvider> getLocalnetProviders() {
    // Silly type downgrade from PubberFamilyProvider to FamilyProvider.
    return localnetProviders.entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  /**
   * Instantiate a family provider.
   */
  PubberFamilyProvider instantiateProvider(String family) {
    try {
      return LOCALNET_PROVIDERS.get(family)
          .getDeclaredConstructor(ManagerHost.class, String.class, String.class)
          .newInstance(this, family, config.deviceId);
    } catch (Exception e) {
      throw new RuntimeException(format("While creating instance of %s",
          LOCALNET_PROVIDERS.get(family)), e);
    }
  }

  /**
   * Set site model.
   */
  public void setSiteModel(SiteModel siteModel) {
    LOCALNET_PROVIDERS.forEach((family, providerClass) -> {
      if (providerClass == PubberVendorProvider.class) {
        localnetProviders.put(family, instantiateProvider(family));
        return;
      }
      if (providerClass == PubberBacnetProvider.class && host instanceof PublisherHost) {
        localnetProviders.put(family, instantiateProvider(family));
        return;
      }
      if (providerClass == PubberIpProvider.class && host instanceof PublisherHost) {
        Metadata metadata = siteModel.getMetadata(getDeviceId());
        String addr = catchToNull(() -> metadata.localnet.families.get(family).addr);
        if (addr != null) {
          localnetProviders.put(family, instantiateProvider(family));
        }
      }
    });
    localnetProviders.values().forEach(value -> value.setSiteModel(siteModel));
  }
}
