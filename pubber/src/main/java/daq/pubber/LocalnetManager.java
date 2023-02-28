package daq.pubber;

import java.util.HashMap;
import udmi.schema.Config;
import udmi.schema.FamilyLocalnetState;
import udmi.schema.LocalnetState;
import udmi.schema.State;

public class LocalnetManager {

  public LocalnetManager(State deviceState, Config deviceConfig) {
    deviceState.localnet = new LocalnetState();
    deviceState.localnet.families = enumerateInterfaceAddresses();
  }

  private HashMap<String, FamilyLocalnetState> enumerateInterfaceAddresses() {

  }
}
