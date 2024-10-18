package udmi.lib.client;

import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static java.util.stream.Collectors.toMap;

import com.google.udmi.util.SiteModel;
import java.util.Map;
import udmi.lib.FamilyProvider;
import udmi.lib.IpProvider;
import udmi.lib.ManagerHost;
import udmi.lib.ProtocolFamily;
import udmi.lib.VendorProvider;
import udmi.schema.FamilyDiscovery;
import udmi.schema.FamilyLocalnetState;
import udmi.schema.LocalnetConfig;
import udmi.schema.LocalnetState;
import udmi.schema.PubberConfiguration;

/**
 * Localnet client.
 */
public interface LocalnetManagerClient extends ManagerHost, ManagerClient {

  Map<String, Class<? extends FamilyProvider>> LOCALNET_PROVIDERS =
      Map.of(
          ProtocolFamily.VENDOR, VendorProvider.class,
          ProtocolFamily.IPV_4, IpProvider.class,
          ProtocolFamily.IPV_6, IpProvider.class,
          ProtocolFamily.ETHER, IpProvider.class);

  LocalnetConfig getLocalnetConfig();

  void setLocalnetConfig(LocalnetConfig localnetConfig);

  /**
   * Enumerate families.
   *
   * @return Map of family key String -> FamilyDiscovery.
   */
  default Map<String, FamilyDiscovery> enumerateFamilies() {
    return getLocalnetState().families.keySet().stream()
        .collect(toMap(key -> key, this::makeFamilyDiscovery));
  }

  /**
   * Make family discovery from key.
   *
   * @param key Key parameter.
   * @return FamilyDiscovery instance.
   */
  default FamilyDiscovery makeFamilyDiscovery(String key) {
    FamilyDiscovery familyDiscovery = new FamilyDiscovery();
    familyDiscovery.addr = getLocalnetState().families.get(key).addr;
    return familyDiscovery;
  }

  default void update(Object update) {
    throw new RuntimeException("Not yet implemented");
  }

  /**
   * Update family state.
   */
  default void update(String family, FamilyLocalnetState stateEntry) {
    getLocalnetState().families.put(family, stateEntry);
    updateState();
  }

  /**
   * Update state.
   */
  default void updateState() {
    updateState(ifNotNullGet(getLocalnetConfig(), c -> getLocalnetState(), LocalnetState.class));
  }

  default void updateConfig(LocalnetConfig localnet) {
    setLocalnetConfig(localnet);
    updateState();
  }

  default FamilyProvider getLocalnetProvider(String family) {
    return getLocalnetProviders().get(family);
  }

  /**
   * Instantiate a family provider.
   */
  default FamilyProvider instantiateProvider(String family) {
    try {
      return LOCALNET_PROVIDERS.get(family).getDeclaredConstructor(
              ManagerHost.class, String.class, PubberConfiguration.class)
          .newInstance(this, family, getConfig());
    } catch (Exception e) {
      throw new RuntimeException("While creating instance of " + LOCALNET_PROVIDERS.get(family), e);
    }
  }

  Map<String, FamilyProvider> getLocalnetProviders();

  LocalnetState getLocalnetState();

  default void setSiteModel(SiteModel siteModel) {
    ((VendorProvider) getLocalnetProviders().get(ProtocolFamily.VENDOR)).setSiteModel(siteModel);
  }

  default void publish(Object message) {
    getHost().publish(message);
  }

}
