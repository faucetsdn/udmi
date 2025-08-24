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

  private static final int MAX_PORT_VALUE = 65535;
  private static final int MAX_PREFIX_LENGTH = 32;

  @Override
  public String familyKey() {
    return IPV_4;
  }

  @Override
  public void validateRef(String refValue) {
    requireNonNull(refValue, "missing required ipv4 point ref");
    checkState(refValue.startsWith("ipv4://"), "ipv4 ref must start with 'ipv4://'");

    String[] parts = refValue.substring("ipv4://".length()).split(":", 2);
    checkState(parts.length == 2,
        "ipv4 ref must be in format ipv4://<address>:<port>");
    validateAddr(parts[0]);

    int port = Integer.parseInt(parts[1]);
    checkState(port >= 0 && port <= MAX_PORT_VALUE,
        format("ipv4 ref port %s exceeds maximum %d", port, MAX_PORT_VALUE));
  }

  @Override
  public void validateAddr(String scanAddr) {
    requireNonNull(scanAddr, "missing required ipv4 scan_addr");
    checkState(InetAddresses.isInetAddress(scanAddr)
            && InetAddresses.forString(scanAddr) instanceof Inet4Address,
        format("ipv4 scan_addr %s is not a valid IPv4 address", scanAddr));
  }

  @Override
  public void validateNetwork(String networkAddr) {
    requireNonNull(networkAddr, "missing required ipv4 network");
    String[] parts = networkAddr.split("/", 2);
    checkState(parts.length == 2,
        "ipv4 network must be in CIDR format (address/prefix)");

    validateAddr(parts[0]);

    int prefix = Integer.parseInt(parts[1]);
    checkState(prefix >= 0 && prefix <= MAX_PREFIX_LENGTH,
        format("ipv4 network prefix %s exceeds maximum %d", prefix, MAX_PREFIX_LENGTH));
  }
}
