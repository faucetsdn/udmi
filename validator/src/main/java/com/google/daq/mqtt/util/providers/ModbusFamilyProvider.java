package com.google.daq.mqtt.util.providers;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static udmi.lib.ProtocolFamily.MODBUS;

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * General family of modbus addresses.
 */
public class ModbusFamilyProvider implements FamilyProvider {

  // URL format:
  //   modbus://<host>[:port]/<unitid>/<function>/<address>[/<quantity>][?params]
  // Note: the `split("/", 4)` in `FamilyProvider` splits the URL into:
  // parts[0]: modbus:
  // parts[1]: (empty string)
  // parts[2]: <host>[:port]
  // parts[3]: <unitid>/<function>/<address>[/<quantity>][?interpretation]

  // Address (parts[2]): <host>[:port]
  private static final Pattern MODBUS_ADDR = Pattern.compile("^([a-zA-Z0-9.-]+)(?::\\d+)?$");

  // Point (parts[3]): <unitid>/<function>/<address>[/<quantity>][?interpretation]
  private static final Pattern MODBUS_POINT =
      Pattern.compile("^(\\d+)/(\\d+)/(\\d+)(?:/(\\d+))?(?:\\?(.*))?$");

  private static final Set<String> ALLOWED_FUNCTIONS =
      ImmutableSet.of("1", "2", "3", "4", "5", "6", "15", "16");

  private static final Set<String> ALLOWED_PARAMS =
      ImmutableSet.of("border", "type", "worder", "scale", "bit");

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
    if (!matcher.matches()) {
      throw new RuntimeException(
          format("protocol ref %s does not match expression %s", pointRef, MODBUS_POINT));
    }

    String function = matcher.group(2);
    if (!ALLOWED_FUNCTIONS.contains(function)) {
      throw new RuntimeException(format("invalid modbus function code %s", function));
    }

    String query = matcher.group(5);
    if (query != null) {
      Arrays.stream(query.split("&")).forEach(param -> {
        String key = param.split("=")[0];
        if (!ALLOWED_PARAMS.contains(key)) {
          throw new RuntimeException(format("invalid modbus interpretation parameter %s", key));
        }
      });
    }
  }

  @Override
  public void validateNetwork(String network) {
    // Network is part of the address string, so we don't expect it here
    FamilyProvider.super.validateNetwork(network);
  }
}
