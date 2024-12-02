package com.google.daq.mqtt.util;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static udmi.lib.ProtocolFamily.BACNET;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * General family of bacnet addresses.
 */
public class BacnetFamilyProvider implements FamilyProvider {

  private static final Pattern BACNET_REF = Pattern.compile("bacnet://[1-9][0-9]*/([A-Z]{2,4})-([1-9][0-9]*)(#[_a-z]+)?");

  @Override
  public String familyKey() {
    return BACNET;
  }

  @Override
  public void refValidator(String metadataRef) {
    requireNonNull(metadataRef, "missing required bacnet point ref");
    Matcher matcher = BACNET_REF.matcher(metadataRef);
    boolean matches = matcher.matches();
    if (!matches) {
      throw new RuntimeException(
          format("protocol ref %s does not match expression %s", metadataRef, BACNET_REF));
    }
  }
}
