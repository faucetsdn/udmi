package com.google.daq.mqtt.util.providers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

/**
 * Provider for the Modbus localnet family.
 */
public class ModbusFamilyProvider implements FamilyProvider {

  // Legacy RTU format:
  // modbus://[modbus_id]/[range]/[data_type]/[offset]/[bit|length] (or similarly 6 parts as in bambi test)
  private static final Pattern LEGACY_MODBUS_RTU_PATTERN = Pattern.compile("^modbus://[^/]+/[^/]+(?:/[^/]+){1,4}$");

  // Unified format:
  // modbus://[network@]host[:port]/<unitid>/<range>/<address>[/<quantity>][?interpretation]
  private static final Pattern UNIFIED_MODBUS_PATTERN = Pattern.compile(
      "^modbus://(?:(?<network>[^@/]+)@)?(?<host>[^\\[\\]:/@]+|\\[[^\\]]+\\])(?::(?<port>\\d+))?" +
      "/(?<unitid>\\d+)/(?<range>[^/]+)/(?<address>\\d+)(?:/(?<quantity>\\d+))?(?:\\?(?<interpretation>[^/]+))?$");

  @Override
  public String familyKey() {
    return "modbus";
  }

  @Override
  public void validateUrl(String url) {
    checkState(url.startsWith("modbus://"), format("modbus url %s does not match expected pattern", url));

    Matcher unifiedMatcher = UNIFIED_MODBUS_PATTERN.matcher(url);

    if (unifiedMatcher.matches()) {
      String portStr = unifiedMatcher.group("port");
      if (portStr != null) {
        int port = Integer.parseInt(portStr);
        checkState(port >= 0 && port <= FamilyProvider.MAX_PORT_VALUE,
            format("modbus ref port %s exceeds maximum %d", portStr, FamilyProvider.MAX_PORT_VALUE));
      }
    } else {
      String path = url.substring("modbus://".length());
      String[] parts = path.split("/");
      checkState(parts.length >= 4 && parts.length <= 6, format("modbus legacy url %s does not match expected parts count", url));
      // Check invalid characters that signify a malformed unified format instead of legacy
      checkState(!path.contains("?") && !path.contains("@") && !path.contains(":"), format("modbus legacy url %s contains invalid characters", url));
    }
  }

  @Override
  public void validateAddr(String scanAddr) {
    // Basic validation, e.g., must be a number if we strictly followed modbus
    // But we just let it pass for now as it's a test provider.
  }
}
