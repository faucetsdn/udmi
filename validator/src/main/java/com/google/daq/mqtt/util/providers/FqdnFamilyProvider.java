package com.google.daq.mqtt.util.providers;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static udmi.lib.ProtocolFamily.FQDN;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * General family of Fully Qualified Domain Names (FQDN).
 */
public class FqdnFamilyProvider implements FamilyProvider {

  private static final String FQDN_REGEX_STRING =
      "(?!-)(?:[a-zA-Z0-9-]{1,63}(?<!-)\\.)+[a-zA-Z]{2,63}\\.?";
  private static final Pattern FQDN_ADDR = Pattern.compile(FQDN_REGEX_STRING);
  private static final Pattern FQDN_NETWORK = FQDN_ADDR;
  private static final Pattern FQDN_REF = Pattern.compile(
      "^fqdn://(" + FQDN_REGEX_STRING + "):([0-9]{1,5})$");
  private static final int MAX_PORT_VALUE = 65535;

  @Override
  public String familyKey() {
    return FQDN;
  }

  @Override
  public void validateRef(String refValue) {
    requireNonNull(refValue, "missing required fqdn point ref");
    Matcher matcher = FQDN_REF.matcher(refValue);
    checkState(matcher.matches(),
        format("protocol ref %s does not match expression %s", refValue, FQDN_REF.pattern()));
    String port = matcher.group(2);
    checkState(Integer.parseInt(port) <= MAX_PORT_VALUE,
        format("fqdn ref port %s exceeds maximum %d", port, MAX_PORT_VALUE));
  }

  @Override
  public void validateAddr(String scanAddr) {
    requireNonNull(scanAddr, "missing required fqdn scan_addr");
    checkState(FQDN_ADDR.matcher(scanAddr).matches(),
        format("fqdn scan_addr %s does not match expression %s", scanAddr, FQDN_ADDR.pattern()));
  }

  @Override
  public void validateNetwork(String networkAddr) {
    requireNonNull(networkAddr, "missing required fqdn network (parent domain)");
    checkState(FQDN_NETWORK.matcher(networkAddr).matches(),
        format("fqdn network (parent domain) %s does not match expression %s", networkAddr,
            FQDN_NETWORK.pattern()));
  }
}
