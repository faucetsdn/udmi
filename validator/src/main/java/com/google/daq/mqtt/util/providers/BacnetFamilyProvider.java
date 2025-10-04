package com.google.daq.mqtt.util.providers;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static udmi.lib.ProtocolFamily.BACNET;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * General family of bacnet addresses.
 */
public class BacnetFamilyProvider implements FamilyProvider {

  private static final Pattern BACNET_ADDR = Pattern.compile("[1-9][0-9]*");
  private static final int MAX_ADDR_VALUE = 4194303;
  private static final Pattern BACNET_NETWORK = Pattern.compile("[1-9][0-9]{0,4}");
  private static final int MAX_NETWORK_VALUE = 65534;
  private static final Pattern BACNET_POINT = Pattern.compile(
      "([A-Z]{2,4}):(0|[1-9][0-9]*)(#[_a-z]+)?");

  @Override
  public String familyKey() {
    return BACNET;
  }

  @Override
  public void validatePoint(String pointRef) {
    requireNonNull(pointRef, "missing required bacnet point ref");
    Matcher matcher = BACNET_POINT.matcher(pointRef);
    boolean matches = matcher.matches();
    if (!matches) {
      throw new RuntimeException(
          format("protocol ref %s does not match expression %s", pointRef, BACNET_POINT));
    }
  }

  @Override
  public void validateAddr(String scanAddr) {
    checkState(BACNET_ADDR.matcher(scanAddr).matches(),
        format("bacnet scan_addr %s does not match expression %s", scanAddr, BACNET_ADDR));
    checkState(Integer.parseInt(scanAddr) <= MAX_ADDR_VALUE,
        format("bacnet network %s exceeded maximum %d", scanAddr, MAX_ADDR_VALUE));
  }

  @Override
  public void validateNetwork(String networkAddr) {
    checkState(BACNET_NETWORK.matcher(networkAddr).matches(),
        format("bacnet network %s does not match expression %s", networkAddr, BACNET_NETWORK));
    checkState(Integer.parseInt(networkAddr) <= MAX_NETWORK_VALUE,
        format("bacnet network %s exceeded maximum %d", networkAddr, MAX_NETWORK_VALUE));
  }
}
