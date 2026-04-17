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

  // URL format: modbus://[network@]<host>[:port]/<unitid>/<function>/<address>[/<quantity>][?interpretation_parameters]
  // Note: the `split("/", 4)` in `FamilyProvider` splits the URL into:
  // parts[0]: modbus:
  // parts[1]: (empty string)
  // parts[2]: [network@]<host>[:port]
  // parts[3]: <unitid>/<function>/<address>[/<quantity>][?interpretation_parameters]

  // Address (parts[2]): [network@]<host>
  private static final Pattern MODBUS_ADDR = Pattern.compile("^(?:([a-zA-Z0-9_-]+)@)?([a-zA-Z0-9.-]+)(?::\\d+)?$");

  // Point (parts[3]): <unitid>/<function>/<address>[/<quantity>][?interpretation_parameters]
  private static final Pattern MODBUS_POINT = Pattern.compile("^(\\d+)/(\\d+)/(\\d+)(?:/(\\d+))?(?:\\?(.*))?$");

  @Override
  public String familyKey() {
    return MODBUS;
  }

  @Override
  public void validateAddr(String scanAddr) {
    requireNonNull(scanAddr, "missing required modbus scan_addr");
    checkState(MODBUS_ADDR.matcher(scanAddr).matches(),
        format("modbus scan_addr %s does not match expression %s", scanAddr, MODBUS_ADDR));
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
  public void validateNetwork(String network) {
    // Network is part of the address string, so we don't expect it here
    FamilyProvider.super.validateNetwork(network);
  }
}
