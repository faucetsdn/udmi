package com.google.daq.mqtt.util;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import java.util.regex.Pattern;
import udmi.lib.ProtocolFamily;

/**
 * General family of bacnet addresses.
 */
public class BacnetFamily implements NetworkFamily {

  // TODO: Make something that validates a bacnet addr against a decimal integer.

  // TODO: Fix this so it can include all the different forms.
  private static final Pattern BACNET_REF = Pattern.compile("([A-Z]{2,4})([0-9]+)\\.([A-Za-z_]+)");

  @Override
  public String familyKey() {
    return ProtocolFamily.BACNET;
  }

  @Override
  public void refValidator(String metadataRef) {
    requireNonNull(metadataRef, "missing required bacnet point ref");
    boolean matches = BACNET_REF.matcher(metadataRef).matches();
    if (!matches) {
      throw new RuntimeException(
          format("point ref %s does not match expression %s", metadataRef, BACNET_REF));
    }
  }
}
