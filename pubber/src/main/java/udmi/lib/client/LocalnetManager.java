package udmi.lib.client;

import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static java.util.stream.Collectors.toMap;

import java.util.Map;
import udmi.lib.intf.FamilyProvider;
import udmi.lib.intf.ManagerHost;
import udmi.schema.FamilyDiscovery;
import udmi.schema.FamilyLocalnetState;
import udmi.schema.LocalnetConfig;
import udmi.schema.LocalnetState;

/**
 * Localnet client.
 */
public interface LocalnetManager extends ManagerHost, SubBlockManager {


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

  Map<String, FamilyProvider> getLocalnetProviders();

  LocalnetState getLocalnetState();

  default void publish(String targetId, Object message) {
    getHost().publish(targetId, message);
  }

}
