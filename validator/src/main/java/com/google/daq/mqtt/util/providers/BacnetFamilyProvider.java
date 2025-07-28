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

  private static final Pattern BACNET_ADDR = Pattern.compile("0|[1-9][0-9]*");
  private static final Pattern BACNET_NETWORK = Pattern.compile("[1-9][0-9]{0,4}");
  private static final Pattern BACNET_REF = Pattern.compile(
      "bacnet://(0|[1-9][0-9]*)/([A-Z]{2,4}):(0|[1-9][0-9]*)(#[_a-z]+)?");

  @Override
  public String familyKey() {
    return BACNET;
  }

  @Override
  public void validateRef(String refValue) {
    requireNonNull(refValue, "missing required bacnet point ref");
    Matcher matcher = BACNET_REF.matcher(refValue);
    boolean matches = matcher.matches();
    if (!matches) {
      throw new RuntimeException(
          format("protocol ref %s does not match expression %s", refValue, BACNET_REF));
    }
  }

  @Override
  public void validateAddr(String scanAddr) {
    checkState(BACNET_ADDR.matcher(scanAddr).matches(),
        format("bacnet scan_addr %s does not match expression %s", scanAddr, BACNET_ADDR));
  }

  @Override
  public void validateNetwork(String networkAddr) {
    checkState(BACNET_NETWORK.matcher(networkAddr).matches(),
        format("bacnet network %s does not match expression %s", networkAddr, BACNET_NETWORK));
  }
}
