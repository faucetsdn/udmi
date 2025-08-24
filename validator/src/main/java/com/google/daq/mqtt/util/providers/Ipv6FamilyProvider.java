package com.google.daq.mqtt.util.providers;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static udmi.lib.ProtocolFamily.IPV_6;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * General family of IPv6 addresses.
 */
public class Ipv6FamilyProvider implements FamilyProvider {

  private static final String IPV6_ADDR_REGEX_STRING =
      "(?:(?:[0-9a-f]{1,4}:){7}[0-9a-f]{1,4}|"
          + "(?:[0-9a-f]{1,4}:){1,7}:|"
          + ":(?:(?::[0-9a-f]{1,4}){1,7})?|"
          + "(?:[0-9a-f]{1,4}:){1,6}:[0-9a-f]{1,4}|"
          + "(?:[0-9a-f]{1,4}:){1,5}(?::[0-9a-f]{1,4}){1,2}|"
          + "(?:[0-9a-f]{1,4}:){1,4}(?::[0-9a-f]{1,4}){1,3}|"
          + "(?:[0-9a-f]{1,4}:){1,3}(?::[0-9a-f]{1,4}){1,4}|"
          + "(?:[0-9a-f]{1,4}:){1,2}(?::[0-9a-f]{1,4}){1,5}|"
          + "[0-9a-f]{1,4}:(?:(?::[0-9a-f]{1,4}){1,6})|"
          + "(?:(?:[0-9a-f]{1,4}:){1,4}:)?(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}"
          + "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?))";

  private static final Pattern IPV6_ADDR = Pattern.compile("^" + IPV6_ADDR_REGEX_STRING + "$");
  private static final Pattern IPV6_NETWORK = Pattern.compile(
      "^(" + IPV6_ADDR_REGEX_STRING + ")/([0-9]{1,3})$");
  private static final Pattern IPV6_REF = Pattern.compile(
      "^ipv6://\\[(" + IPV6_ADDR_REGEX_STRING + ")\\]:([0-9]{1,5})$");
  private static final int MAX_PORT_VALUE = 65535;
  private static final int MAX_PREFIX_LENGTH = 128;

  @Override
  public String familyKey() {
    return IPV_6;
  }

  @Override
  public void validateRef(String refValue) {
    requireNonNull(refValue, "missing required ipv6 point ref");
    Matcher matcher = IPV6_REF.matcher(refValue);
    checkState(matcher.matches(),
        format("protocol ref %s does not match expression %s", refValue, IPV6_REF.pattern()));
    String port = matcher.group(2);
    checkState(Integer.parseInt(port) <= MAX_PORT_VALUE,
        format("ipv6 ref port %s exceeds maximum %d", port, MAX_PORT_VALUE));
  }

  @Override
  public void validateAddr(String scanAddr) {
    requireNonNull(scanAddr, "missing required ipv6 scan_addr");
    checkState(IPV6_ADDR.matcher(scanAddr).matches(),
        format("ipv6 scan_addr %s does not match expression %s", scanAddr, IPV6_ADDR.pattern()));
  }

  @Override
  public void validateNetwork(String networkAddr) {
    requireNonNull(networkAddr, "missing required ipv6 network");
    Matcher matcher = IPV6_NETWORK.matcher(networkAddr);
    checkState(matcher.matches(),
        format("ipv6 network %s does not match expression %s", networkAddr,
            IPV6_NETWORK.pattern()));
    String prefix = matcher.group(2);
    checkState(Integer.parseInt(prefix) <= MAX_PREFIX_LENGTH,
        format("ipv6 network prefix %s exceeds maximum %d", prefix, MAX_PREFIX_LENGTH));
  }
}
