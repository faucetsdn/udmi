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
      "(?:[0-9a-f]{2}[:]){5}(?:[0-9a-f]{2})";
  private static final Pattern ETHER_ADDR = Pattern.compile("^" + ETHER_ADDR_REGEX_STRING + "$");
  private static final Pattern ETHER_REF = Pattern.compile(
      "^ether://(" + ETHER_ADDR_REGEX_STRING + ")$");
  private static final Pattern ETHER_NETWORK = Pattern.compile("^[0-9]{1,4}$");
  private static final int MIN_VLAN_ID = 1;
  private static final int MAX_VLAN_ID = 4094;


  @Override
  public String familyKey() {
    return ETHER;
  }

  public String validatePort(String urlAddr) {
    // Ethernet addresses don't support ports, so always assume it's just a raw address.
    return urlAddr;
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
    requireNonNull(networkAddr, "missing required ether network addr");
    checkState(ETHER_NETWORK.matcher(networkAddr).matches(),
        format("ether network addr %s is not a valid number", networkAddr));
    int vlanId = Integer.parseInt(networkAddr);
    checkState(vlanId >= MIN_VLAN_ID && vlanId <= MAX_VLAN_ID,
        format("ether network addr %s must be a number between %d and %d", networkAddr,
            MIN_VLAN_ID, MAX_VLAN_ID));
  }
}
