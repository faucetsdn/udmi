package com.google.daq.mqtt.util;

import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.catchToMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;
import com.google.udmi.util.GeneralUtils;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * Basic tests for the bacnet family provider.
 */
public class BacnetFamilyProviderTest {

  public static final Set<String> GOOD_REFERENCES = ImmutableSet.of(
      "bacnet://291842/AI-2#present_value",
      "bacnet://291842/BO-21");
  public static final Set<String> BAD_REFERENCES = ImmutableSet.of(
      "modbus://291842/AI-2#present_value");

  BacnetFamilyProvider provider = new BacnetFamilyProvider();

  private String validateToMessage(String ref) {
    return catchToMessage(() -> provider.refValidator(ref));
  }

  @Test
  public void bacnet_ref_validation() {
    List<String> goodErrors = GOOD_REFERENCES.stream().map(this::validateToMessage)
        .filter(GeneralUtils::isNotEmpty).toList();
    assertTrue("Unexpected ref errors: " + CSV_JOINER.join(goodErrors), goodErrors.isEmpty());
    List<String> badErrors = BAD_REFERENCES.stream().map(this::validateToMessage)
        .filter(GeneralUtils::isNotEmpty).toList();
    assertEquals("Not enough validation errors", BAD_REFERENCES.size(), badErrors.size());
  }
}