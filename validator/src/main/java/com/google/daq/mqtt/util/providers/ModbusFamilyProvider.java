package com.google.daq.mqtt.util.providers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

/**
 * Provider for the Modbus localnet family.
 */
public class ModbusFamilyProvider implements FamilyProvider {

  // Unified format:
  // modbus://[network]/<unitid>/<range>/<address>[/<quantity>][?interpretation]
  private static final Pattern UNIFIED_MODBUS_PATTERN = Pattern.compile(
      "^modbus://(?<network>[^/@:]+)/(?<unitid>\\d+)/(?<range>[^/]+)/(?<address>\\d+)(?:/(?<quantity>\\d+))?(?:\\?(?<interpretation>[^/]+))?$");

  @Override
  public String familyKey() {
    return "modbus";
  }

  @Override
  public void validateUrl(String url) {
    checkState(url.startsWith("modbus://"), format("modbus url %s does not match expected pattern", url));

    Matcher unifiedMatcher = UNIFIED_MODBUS_PATTERN.matcher(url);

    if (unifiedMatcher.matches()) {
      // Valid unified format
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
