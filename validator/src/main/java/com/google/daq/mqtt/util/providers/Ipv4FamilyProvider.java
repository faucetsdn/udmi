package com.google.daq.mqtt.util.providers;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static udmi.lib.ProtocolFamily.IPV_4;

import com.google.common.net.InetAddresses;
import java.net.Inet4Address;

/**
 * General family of IPv4 addresses.
 */
public class Ipv4FamilyProvider implements FamilyProvider {

  private static final int MAX_PREFIX_LENGTH = 32;
  public static final String CIDR_SEPARATOR = "/";

  @Override
  public String familyKey() {
    return IPV_4;
  }

  @Override
  public void validateAddr(String fullAddr) {
    requireNonNull(fullAddr, "missing required ipv4 scan_addr");

    checkState(InetAddresses.isInetAddress(fullAddr)
            && InetAddresses.forString(fullAddr) instanceof Inet4Address,
        format("ipv4 scan_addr %s is not a valid IPv4 address", fullAddr));
  }

  @Override
  public void validateNetwork(String networkAddr) {
    requireNonNull(networkAddr, "missing required ipv4 network");
    String[] parts = networkAddr.split(CIDR_SEPARATOR, 2);
    checkState(parts.length == 2,
        "ipv4 network must be in CIDR format (address/size)");

    validateAddr(parts[0]);

    int prefix = Integer.parseInt(parts[1]);
    checkState(prefix >= 0 && prefix <= MAX_PREFIX_LENGTH,
        format("ipv4 network prefix %s exceeds maximum %d", prefix, MAX_PREFIX_LENGTH));
  }
}
