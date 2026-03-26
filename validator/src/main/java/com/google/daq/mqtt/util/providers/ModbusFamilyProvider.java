package com.google.daq.mqtt.util.providers;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static udmi.lib.ProtocolFamily.MODBUS;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * General family of modbus addresses.
 */
public class ModbusFamilyProvider implements FamilyProvider {

  private static final Pattern MODBUS_ADDR = Pattern.compile("[1-9][0-9]*");
  private static final int MAX_ADDR_VALUE = 4194303;
  private static final Pattern MODBUS_NETWORK = Pattern.compile("[1-9][0-9]{0,4}");
  private static final int MAX_NETWORK_VALUE = 65534;
  private static final Pattern MODBUS_POINT = Pattern.compile(
      "([A-Z]{2,4}):(0|[1-9][0-9]*)(#[_a-z]+)?");

  @Override
  public String familyKey() {
    return MODBUS;
  }

  @Override
  public void validatePoint(String pointRef) {
    requireNonNull(pointRef, "missing required modbus point ref");
    Matcher matcher = MODBUS_POINT.matcher(pointRef);
    boolean matches = matcher.matches();
    if (!matches) {
      throw new RuntimeException(
          format("protocol ref %s does not match expression %s", pointRef, MODBUS_POINT));
    }
  }

  @Override
  public void validateAddr(String scanAddr) {
    checkState(MODBUS_ADDR.matcher(scanAddr).matches(),
        format("modbus scan_addr %s does not match expression %s", scanAddr, MODBUS_ADDR));
    checkState(Integer.parseInt(scanAddr) <= MAX_ADDR_VALUE,
        format("modbus network %s exceeded maximum %d", scanAddr, MAX_ADDR_VALUE));
  }

  @Override
  public void validateNetwork(String networkAddr) {
    checkState(MODBUS_NETWORK.matcher(networkAddr).matches(),
        format("modbus network %s does not match expression %s", networkAddr, MODBUS_NETWORK));
    checkState(Integer.parseInt(networkAddr) <= MAX_NETWORK_VALUE,
        format("modbus network %s exceeded maximum %d", networkAddr, MAX_NETWORK_VALUE));
  }
}
