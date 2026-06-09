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
  //   modbus://<unitid>/<function>/<address>[/<quantity>][?params]
  // Note: the `split("/", 4)` in `FamilyProvider` splits the URL into:
  // parts[0]: modbus:
  // parts[1]: (empty string)
  // parts[2]: <unitid>
  // parts[3]: <function>/<address>[/<quantity>][?interpretation]

  // Address (parts[2]): <unitid>
  private static final Pattern MODBUS_ADDR = Pattern.compile("0|[1-9][0-9]*");
  private static final int MAX_ADDR_VALUE = 255;

  // Point (parts[3]): <function>/<address>[/<quantity>][?interpretation]
  private static final Pattern MODBUS_POINT =
      Pattern.compile("^(\\d+)/(\\d+)(?:/(\\d+))?(?:\\?(.*))?$");

  private static final Set<String> ALLOWED_FUNCTIONS =
      ImmutableSet.of("1", "2", "3", "4", "5", "6", "15", "16");

  private static final Set<String> ALLOWED_PARAMS =
      ImmutableSet.of("border", "type", "worder", "scale", "offset", "network");

  private static final Set<String> ALLOWED_BORDERS = ImmutableSet.of("MSB", "LSB");
  private static final Set<String> ALLOWED_WORDERS = ImmutableSet.of("HWF", "LWF");
  private static final Set<String> ALLOWED_TYPES = ImmutableSet.of(
      "INT16", "INT32", "INT64", "UINT16", "UINT32", "UINT64",
      "FLOAT32", "FLOAT64", "BOOLEAN", "ASCII");

  @Override
  public String familyKey() {
    return MODBUS;
  }

  @Override
  public void validateAddr(String scanAddr) {
    requireNonNull(scanAddr, "missing required modbus scan_addr");
    checkState(MODBUS_ADDR.matcher(scanAddr).matches(),
        format("modbus unitid %s does not match expression %s", scanAddr, MODBUS_ADDR));
    checkState(Integer.parseInt(scanAddr) <= MAX_ADDR_VALUE,
        format("modbus unitid %s exceeded maximum %d", scanAddr, MAX_ADDR_VALUE));
  }

  @Override
  public void validatePoint(String pointRef) {
    requireNonNull(pointRef, "missing required modbus point ref");
    Matcher matcher = MODBUS_POINT.matcher(pointRef);
    if (!matcher.matches()) {
      throw new RuntimeException(
          format("protocol ref %s does not match expression %s", pointRef, MODBUS_POINT));
    }

    String function = matcher.group(1);
    if (!ALLOWED_FUNCTIONS.contains(function)) {
      throw new RuntimeException(format("invalid modbus function code %s", function));
    }

    String query = matcher.group(4);
    if (query != null) {
      Arrays.stream(query.split("&")).forEach(param -> {
        String[] parts = param.split("=");
        String key = parts[0];
        if (!ALLOWED_PARAMS.contains(key)) {
          throw new RuntimeException(format("invalid modbus interpretation parameter %s", key));
        }
        if (parts.length < 2) {
          throw new RuntimeException(
              format("missing value for modbus interpretation parameter %s", key));
        }
        String value = parts[1];
        switch (key) {
          case "border":
            if (!ALLOWED_BORDERS.contains(value)) {
              throw new RuntimeException(format("invalid modbus border value %s", value));
            }
            break;
          case "worder":
            if (!ALLOWED_WORDERS.contains(value)) {
              throw new RuntimeException(format("invalid modbus worder value %s", value));
            }
            break;
          case "type":
            if (!ALLOWED_TYPES.contains(value)) {
              throw new RuntimeException(format("invalid modbus type value %s", value));
            }
            break;
          case "scale":
          case "offset":
            try {
              Double.parseDouble(value);
            } catch (NumberFormatException e) {
              throw new RuntimeException(format("invalid modbus %s value %s", key, value));
            }
            break;
          case "network":
            if (value.isEmpty()) {
              throw new RuntimeException("empty modbus network parameter");
            }
            break;
          default:
            throw new RuntimeException(format("unhandled modbus interpretation parameter %s", key));
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
