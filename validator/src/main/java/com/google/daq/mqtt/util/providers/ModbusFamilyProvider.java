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
  //   modbus://<addr>/<unitid>/<function>/<address>[/<quantity>][?params]
  // Note: the `split("/", 4)` in `FamilyProvider` splits the URL into:
  // parts[0]: modbus:
  // parts[1]: (empty string)
  // parts[2]: <addr> (must start with alpha or be a valid IPv4 address)
  // parts[3]: <unitid>/<function>/<address>[/<quantity>][?interpretation]

  // Address: <unitid>
  private static final Pattern MODBUS_ADDR = Pattern.compile("0|[1-9][0-9]*");
  private static final int MAX_ADDR_VALUE = 255;

  // Point (parts[3]): <unitid>/<function>/<address>[/<quantity>][?interpretation]
  private static final Pattern MODBUS_POINT =
      Pattern.compile("^(\\d+)/(\\d+)/(\\d+)(?:/(\\d+))?(?:\\?(.*))?$");

  private static final Set<String> ALLOWED_FUNCTIONS =
      ImmutableSet.of("1", "2", "3", "4", "5", "6", "15", "16");

  private static final Set<String> ALLOWED_PARAMS =
      ImmutableSet.of("border", "type", "worder", "scale", "offset");

  private static final Set<String> ALLOWED_BORDERS = ImmutableSet.of("MSB", "LSB");
  private static final Set<String> ALLOWED_WORDERS = ImmutableSet.of("HWF", "LWF");
  private static final Set<String> ALLOWED_TYPES = ImmutableSet.of(
      "INT16", "INT32", "INT64", "UINT16", "UINT32", "UINT64",
      "FLOAT32", "FLOAT64", "BOOLEAN", "ASCII");

  @Override
  public String familyKey() {
    return MODBUS;
  }

  private static final Pattern ALPHA_ADDR = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_.-]*$");

  private boolean startsWithAlpha(String addr) {
    if (addr == null || addr.isEmpty()) {
      return false;
    }
    return ALPHA_ADDR.matcher(addr).matches();
  }

  private boolean isValidIpv4(String addr) {
    try {
      return com.google.common.net.InetAddresses.isInetAddress(addr)
          && com.google.common.net.InetAddresses.forString(addr) instanceof java.net.Inet4Address;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public void validateAddr(String scanAddr) {
    requireNonNull(scanAddr, "missing required modbus scan_addr");
    checkState(startsWithAlpha(scanAddr) || isValidIpv4(scanAddr),
        format("modbus addr %s is invalid; "
            + "must start with an alphabetical character or be a valid IPv4 address", scanAddr));
  }

  @Override
  public void validatePoint(String pointRef) {
    requireNonNull(pointRef, "missing required modbus point ref");
    Matcher matcher = MODBUS_POINT.matcher(pointRef);
    if (!matcher.matches()) {
      throw new RuntimeException(
          format("protocol ref %s does not match expression %s", pointRef, MODBUS_POINT));
    }

    String unitId = matcher.group(1);
    checkState(MODBUS_ADDR.matcher(unitId).matches(),
        format("modbus unitid %s does not match expression %s", unitId, MODBUS_ADDR));
    checkState(Integer.parseInt(unitId) <= MAX_ADDR_VALUE,
        format("modbus unitid %s exceeded maximum %d", unitId, MAX_ADDR_VALUE));

    String function = matcher.group(2);
    if (!ALLOWED_FUNCTIONS.contains(function)) {
      throw new RuntimeException(format("invalid modbus function code %s", function));
    }

    String query = matcher.group(5);
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

  private static final Set<String> ALLOWED_ADJUNCT_KEYS =
      ImmutableSet.of("protocol", "device", "baud", "parity", "data_bits", "stop_bits");

  @Override
  public void validateModel(udmi.schema.FamilyLocalnetModel familyModel) {
    if (familyModel.adjunct != null) {
      for (String key : familyModel.adjunct.keySet()) {
        if (!ALLOWED_ADJUNCT_KEYS.contains(key)) {
          throw new RuntimeException(format(
              "invalid modbus adjunct key '%s'; allowed keys are %s",
              key, ALLOWED_ADJUNCT_KEYS));
        }
      }
    }
  }
}
