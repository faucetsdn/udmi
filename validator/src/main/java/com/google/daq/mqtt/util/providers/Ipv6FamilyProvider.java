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

  private static final int MAX_PREFIX_LENGTH = 128;
  private static final String STARTING_BRACKET = "[";
  private static final String ENDING_BRACKET = "]";

  @Override
  public String familyKey() {
    return IPV_6;
  }

  @Override
  public void validateAddr(String address) {
    try {
      InetAddress addr = InetAddresses.forString(address);
      checkState(addr instanceof Inet6Address, "Address is not IPv6");

      String canonicalAddr = InetAddresses.toAddrString(addr);
      checkState(address.equals(canonicalAddr),
          "Address is not in canonical form. Expected: " + canonicalAddr);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          format("ipv6 scan_addr %s is not a valid IPv6 address", address));
    }
  }

  @Override
  public void validateAddrUrl(String urlAddr) {
    requireNonNull(urlAddr, "missing required ipv6 scan_addr");

    int lastBracket = urlAddr.lastIndexOf(ENDING_BRACKET);
    int lastColon = urlAddr.lastIndexOf(PORT_SEPARATOR);

    // Don't rely on the default port detection logic, because it gets confused with IPv6 colons.
    String fullAddr = lastColon == lastBracket + 1 ? validatePort(urlAddr) : urlAddr;

    checkState(fullAddr.startsWith(STARTING_BRACKET), "URL missing starting " + STARTING_BRACKET);
    checkState(fullAddr.endsWith(ENDING_BRACKET), "URL missing ending " + ENDING_BRACKET);

    String address = fullAddr.substring(1, fullAddr.length() - 1);
    validateAddr(address);
  }

  @Override
  public void validateNetwork(String networkAddr) {
    requireNonNull(networkAddr, "missing required ipv6 network");
    String[] parts = networkAddr.split("/", 2);
    checkState(parts.length == 2,
        "ipv6 network must be in CIDR format (address/prefix)");

    validateAddr(parts[0]);

    int prefixLen = Integer.parseInt(parts[1]);
    checkState(prefixLen >= 0 && prefixLen <= MAX_PREFIX_LENGTH,
        format("ipv6 network prefix %s exceeds maximum %d", prefixLen, MAX_PREFIX_LENGTH));
  }
}
