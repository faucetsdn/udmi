package com.google.daq.mqtt.util.providers;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static udmi.lib.ProtocolFamily.IPV_4;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * General family of IPv4 addresses.
 */
public class Ipv4FamilyProvider implements FamilyProvider {

  private static final String IPV4_OCTET_REGEX = "(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])";
  private static final String IPV4_ADDR_REGEX_STRING =
      "(?:" + IPV4_OCTET_REGEX + "\\.){3}" + IPV4_OCTET_REGEX;
  private static final Pattern IPV4_ADDR = Pattern.compile("^" + IPV4_ADDR_REGEX_STRING + "$");
  private static final Pattern IPV4_NETWORK = Pattern.compile(
      "^" + IPV4_ADDR_REGEX_STRING + "/(3[0-2]|[12]?[0-9])$");
  private static final Pattern IPV4_REF = Pattern.compile(
      "^ipv4://(" + IPV4_ADDR_REGEX_STRING + "):([0-9]{1,5})$");
  private static final int MAX_PORT_VALUE = 65535;

  @Override
  public String familyKey() {
    return IPV_4;
  }

  @Override
  public void validateRef(String refValue) {
    requireNonNull(refValue, "missing required ipv4 point ref");
    Matcher matcher = IPV4_REF.matcher(refValue);
    checkState(matcher.matches(),
        format("protocol ref %s does not match expression %s", refValue, IPV4_REF.pattern()));
    String port = matcher.group(2);
    checkState(Integer.parseInt(port) <= MAX_PORT_VALUE,
        format("ipv4 ref port %s exceeds maximum %d", port, MAX_PORT_VALUE));
  }

  @Override
  public void validateAddr(String scanAddr) {
    requireNonNull(scanAddr, "missing required ipv4 scan_addr");
    checkState(IPV4_ADDR.matcher(scanAddr).matches(),
        format("ipv4 scan_addr %s does not match expression %s", scanAddr, IPV4_ADDR.pattern()));
  }

  @Override
  public void validateNetwork(String networkAddr) {
    requireNonNull(networkAddr, "missing required ipv4 network");
    checkState(IPV4_NETWORK.matcher(networkAddr).matches(),
        format("ipv4 network %s does not match expression %s", networkAddr,
            IPV4_NETWORK.pattern()));
  }
}
