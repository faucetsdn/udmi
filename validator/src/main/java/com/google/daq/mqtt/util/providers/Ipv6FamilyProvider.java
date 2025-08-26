package com.google.daq.mqtt.util.providers;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static udmi.lib.ProtocolFamily.IPV_6;

import com.google.common.net.InetAddresses;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * General family of IPv6 addresses.
 */
public class Ipv6FamilyProvider implements FamilyProvider {

  private static final int MAX_PORT_VALUE = 65535;
  private static final int MAX_PREFIX_LENGTH = 128;

  @Override
  public String familyKey() {
    return IPV_6;
  }

  @Override
  public void validateRef(String refValue) {
    requireNonNull(refValue, "missing required ipv6 point ref");
    checkState(refValue.startsWith("ipv6://"), "ipv6 ref must start with 'ipv6://'");
    String core = refValue.substring("ipv6://".length());

    int closingBracketIndex = core.lastIndexOf(']');
    checkState(core.startsWith("[") && closingBracketIndex != -1,
        "IPv6 address in ref must be bracketed, e.g., [::1]");

    String address = core.substring(1, closingBracketIndex);
    validateAddr(address);

    checkState(core.charAt(closingBracketIndex + 1) == ':',
        "Missing port after brackets");
    String portStr = core.substring(closingBracketIndex + 2);
    int port = Integer.parseInt(portStr);
    checkState(port >= 0 && port <= MAX_PORT_VALUE,
        format("ipv6 ref port %s exceeds maximum %d", port, MAX_PORT_VALUE));
  }

  @Override
  public void validateAddr(String scanAddr) {
    requireNonNull(scanAddr, "missing required ipv6 scan_addr");

    try {
      InetAddress addr = InetAddresses.forString(scanAddr);
      checkState(addr instanceof Inet6Address, "Address is not IPv6");

      String canonicalAddr = InetAddresses.toAddrString(addr);
      checkState(scanAddr.equals(canonicalAddr),
          "Address is not in canonical form. Expected: " + canonicalAddr);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          format("ipv6 scan_addr %s is not a valid IPv6 address", scanAddr));
    }
  }

  @Override
  public void validateNetwork(String networkAddr) {
    requireNonNull(networkAddr, "missing required ipv6 network");
    String[] parts = networkAddr.split("/", 2);
    checkState(parts.length == 2,
        "ipv6 network must be in CIDR format (address/prefix)");

    validateAddr(parts[0]);

    int prefix = Integer.parseInt(parts[1]);
    checkState(prefix >= 0 && prefix <= MAX_PREFIX_LENGTH,
        format("ipv6 network prefix %s exceeds maximum %d", prefix, MAX_PREFIX_LENGTH));
  }
}
