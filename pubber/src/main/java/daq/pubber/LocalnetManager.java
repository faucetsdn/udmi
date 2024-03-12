package daq.pubber;

import static java.util.stream.Collectors.toMap;

import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.SiteModel;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.FamilyDiscovery;
import udmi.schema.FamilyLocalnetState;
import udmi.schema.LocalnetState;
import udmi.schema.PubberConfiguration;

/**
 * Container class for dealing with the localnet subblock of UDMI.
 */
public class LocalnetManager extends ManagerBase implements ManagerHost {

  private static final Map<ProtocolFamily, Class<? extends FamilyProvider>> LOCALNET_PROVIDERS =
      ImmutableMap.of(
          ProtocolFamily.VENDOR, VendorProvider.class,
          ProtocolFamily.IPV_4, IpProvider.class,
          ProtocolFamily.IPV_6, IpProvider.class,
          ProtocolFamily.ETHER, IpProvider.class);
  private final LocalnetState localnetState;
  private final Map<ProtocolFamily, FamilyProvider> localnetProviders;

  /**
   * Create a new container with the given host.
   */
  public LocalnetManager(ManagerHost host, PubberConfiguration configuration) {
    super(host, configuration);
    localnetState = new LocalnetState();
    localnetState.families = new HashMap<>();
    localnetProviders = LOCALNET_PROVIDERS
        .keySet().stream().collect(Collectors.toMap(family -> family, this::instantiateProvider));
  }

  private FamilyProvider instantiateProvider(ProtocolFamily family) {
    try {
      return LOCALNET_PROVIDERS.get(family).getDeclaredConstructor(
              ManagerHost.class, ProtocolFamily.class, PubberConfiguration.class)
          .newInstance(this, family, config);
    } catch (Exception e) {
      throw new RuntimeException("While creating instance of " + LOCALNET_PROVIDERS.get(family), e);
    }
  }

  Map<ProtocolFamily, FamilyDiscovery> enumerateFamilies() {
    return localnetState.families.keySet().stream()
        .collect(toMap(key -> key, this::makeFamilyDiscovery));
  }

  private FamilyDiscovery makeFamilyDiscovery(ProtocolFamily key) {
    FamilyDiscovery familyDiscovery = new FamilyDiscovery();
    familyDiscovery.addr = localnetState.families.get(key).addr;
    return familyDiscovery;
  }

  public FamilyProvider getLocalnetProvider(ProtocolFamily family) {
    return localnetProviders.get(family);
  }

  @Override
  public void update(Object update) {
    throw new RuntimeException("Not yet implemented");
  }

  protected void update(ProtocolFamily family, FamilyLocalnetState stateEntry) {
    localnetState.families.put(family, stateEntry);
    updateState(localnetState);
  }

  @Override
  public void publish(Object message) {
    host.publish(message);
  }

  public void setSiteModel(SiteModel siteModel) {
    ((VendorProvider) localnetProviders.get(ProtocolFamily.VENDOR)).setSiteModel(siteModel);
  }
}
