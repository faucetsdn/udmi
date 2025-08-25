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
  public void validateRef(String refValue) {
    requireNonNull(refValue, "missing required fqdn point ref");
    checkState(refValue.startsWith("fqdn://"), "fqdn ref must start with 'fqdn://'");
    String core = refValue.substring("fqdn://".length());

    int lastColonIndex = core.lastIndexOf(':');
    checkState(lastColonIndex > 0 && lastColonIndex < core.length() - 1,
        "fqdn ref must be in format fqdn://<hostname>:<port>");

    String host = core.substring(0, lastColonIndex);
    String portStr = core.substring(lastColonIndex + 1);

    validateAddr(host);

    try {
      int port = Integer.parseInt(portStr);
      checkState(port >= 0 && port <= MAX_PORT_VALUE,
          format("fqdn ref port %s exceeds maximum %d", port, MAX_PORT_VALUE));
    } catch (NumberFormatException e) {
      throw new IllegalStateException(format("fqdn ref port %s is not a valid number", portStr));
    }
  }

  @Override
  public void validateAddr(String scanAddr) {
    requireNonNull(scanAddr, "missing required fqdn scan_addr");

    boolean isLowercase = scanAddr.equals(scanAddr.toLowerCase());

    checkState(DOMAIN_VALIDATOR.isValid(scanAddr) && isLowercase,
        format("fqdn scan_addr %s is not a valid lowercase FQDN", scanAddr));
  }

  @Override
  public void validateNetwork(String networkAddr) {
    validateAddr(networkAddr);
  }
}
