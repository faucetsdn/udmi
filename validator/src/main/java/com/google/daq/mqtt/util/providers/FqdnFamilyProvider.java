package com.google.daq.mqtt.util.providers;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static udmi.lib.ProtocolFamily.FQDN;

import org.apache.commons.validator.routines.DomainValidator;

/**
 * General family of Fully Qualified Domain Names (FQDN).
 */
public class FqdnFamilyProvider implements FamilyProvider {

  private static final int MAX_PORT_VALUE = 65535;
  private static final DomainValidator DOMAIN_VALIDATOR = DomainValidator.getInstance();

  @Override
  public String familyKey() {
    return FQDN;
  }


  @Override
  public void validateAddr(String scanAddr) {
    requireNonNull(scanAddr, "missing required fqdn scan_addr");

    boolean isLowercase = scanAddr.equals(scanAddr.toLowerCase());

    checkState(DOMAIN_VALIDATOR.isValid(scanAddr) && isLowercase,
        format("fqdn scan_addr %s is not a valid lowercase FQDN", scanAddr));

    String[] parts = scanAddr.split(":", 2);
    String host = parts[0];
    String portStr = parts.length > 1 ? parts[1] : null;

    try {
      int port = Integer.parseInt(portStr);
      checkState(port >= 0 && port <= MAX_PORT_VALUE,
          format("fqdn ref port %s exceeds maximum %d", port, MAX_PORT_VALUE));
    } catch (NumberFormatException e) {
      throw new IllegalStateException(format("fqdn ref port %s is not a valid number", portStr));
    }
  }

  @Override
  public void validateNetwork(String networkAddr) {
    validateAddr(networkAddr);
  }
}
