package com.google.daq.mqtt.util.providers;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static udmi.lib.ProtocolFamily.ETHER;

import java.util.regex.Pattern;

/**
 * General family of Ethernet addresses.
 */
public class EtherFamilyProvider implements FamilyProvider {

  private static final String ETHER_ADDR_REGEX_STRING =
      "(?:[0-9a-fA-F]{2}[:-]){5}(?:[0-9a-fA-F]{2})";
  private static final Pattern ETHER_ADDR = Pattern.compile("^" + ETHER_ADDR_REGEX_STRING + "$",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern ETHER_REF = Pattern.compile(
      "^ether://(" + ETHER_ADDR_REGEX_STRING + ")$", Pattern.CASE_INSENSITIVE);
  private static final Pattern ETHER_NETWORK = Pattern.compile(
      "^(409[0-4]|40[0-8][0-9]|[1-3][0-9]{3}|[1-9][0-9]{0,2})$");
  private static final int MAX_VLAN_ID = 4094;


  @Override
  public String familyKey() {
    return ETHER;
  }

  @Override
  public void validateRef(String refValue) {
    requireNonNull(refValue, "missing required ether point ref");
    checkState(ETHER_REF.matcher(refValue).matches(),
        format("protocol ref %s does not match expression %s", refValue, ETHER_REF.pattern()));
  }

  @Override
  public void validateAddr(String scanAddr) {
    requireNonNull(scanAddr, "missing required ether scan_addr");
    checkState(ETHER_ADDR.matcher(scanAddr).matches(),
        format("ether scan_addr %s does not match expression %s", scanAddr,
            ETHER_ADDR.pattern()));
  }

  @Override
  public void validateNetwork(String networkAddr) {
    requireNonNull(networkAddr, "missing required ether network (VLAN ID)");
    checkState(ETHER_NETWORK.matcher(networkAddr).matches(),
        format("ether network (VLAN ID) %s must be a number between 1 and %d", networkAddr,
            MAX_VLAN_ID));
  }
}
