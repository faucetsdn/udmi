package com.google.daq.mqtt.util;

import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.catchToMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.util.providers.BacnetFamilyProvider;
import com.google.udmi.util.GeneralUtils;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * Basic tests for the bacnet family provider.
 */
public class BacnetFamilyProviderTest {

  public static final Set<String> GOOD_ADDRS = ImmutableSet.of("10", "23", "9273123");
  public static final Set<String> BAD_ADDRS = ImmutableSet.of(
      "", "x", "snoop", "01293", "0x9122", "B87AC9", "87a8c");

  public static final Set<String> GOOD_REFERENCES = ImmutableSet.of(
      "bacnet://291842/AI:2#present_value",
      "bacnet://29212/AI:2#something_else",
      "bacnet://1/AI:2",
      "bacnet://291842/BO:21");

  public static final Set<String> BAD_REFERENCES = ImmutableSet.of(
      "bacnet://-82/AI:2#present_value",
      "bacnet://091842/AI:2#present_value",
      "bacnet://other/AI:2#present_value",
      "bacnet://291842/AI:2#something-else",
      "bacnet://29A821/AI:2#present_value",
      "bacnet://23a87/AI:2#present_value",
      "bacnet://291842/nope:2#present_value",
      "bacnet://291842/AI#present_value",
      "bacnet://291842/AI",
      "bacnet://291842/VV:x",
      "bacnet://291842/AI2#present_value",
      "bacnet://291842/AI-2#present_value",
      "modbus://291842/AI:2#present_value");

  BacnetFamilyProvider provider = new BacnetFamilyProvider();

  private String validateRef(String ref) {
    return catchToMessage(() -> provider.validateRef(ref));
  }

  private String validateAddr(String addr) {
    return catchToMessage(() -> provider.validateAddr(addr));
  }

  @Test
  public void bacnet_ref_validation() {
    List<String> goodErrors = GOOD_REFERENCES.stream().map(this::validateRef)
        .filter(GeneralUtils::isNotEmpty).toList();
    assertTrue("Unexpected ref errors: " + CSV_JOINER.join(goodErrors), goodErrors.isEmpty());
    List<String> badErrors = BAD_REFERENCES.stream().map(this::validateRef)
        .filter(GeneralUtils::isNotEmpty).toList();
    assertEquals("Not enough validation errors", BAD_REFERENCES.size(), badErrors.size());
  }

  @Test
  public void bacnet_addr_validation() {
    List<String> goodErrors = GOOD_ADDRS.stream().map(this::validateAddr)
        .filter(GeneralUtils::isNotEmpty).toList();
    assertTrue("Unexpected ref errors: " + CSV_JOINER.join(goodErrors), goodErrors.isEmpty());
    List<String> badErrors = BAD_ADDRS.stream().map(this::validateAddr)
        .filter(GeneralUtils::isNotEmpty).toList();
    assertEquals("Not enough validation errors", BAD_ADDRS.size(), badErrors.size());
  }
}