package com.google.daq.mqtt.util.providers;

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
 * Basic tests for the ether family provider.
 */
public class EtherFamilyProviderTest {

  public static final Set<String> GOOD_ADDRS = ImmutableSet.of(
      "00:1a:2b:3c:4d:5e",
      "11:22:33:44:55:66",
      "aa:bb:cc:dd:ee:ff"
  );
  public static final Set<String> BAD_ADDRS = ImmutableSet.of(
      "",
      "00:1A:2B:3C:4D:5E", // Uppercase
      "00-1a-2b-3c-4d-5e", // Hyphens
      "001a2b3c4d5e",      // No separators
      "00:1a:2b:3c:4d",    // Too short
      "00:1a:2b:3c:4d:5e:6f", // Too long
      "00:1g:2b:3c:4d:5e"   // Invalid hex character
  );

  public static final Set<String> GOOD_NETWORKS = ImmutableSet.of("1", "4094", "1000");
  public static final Set<String> BAD_NETWORKS = ImmutableSet.of("0", "4095", "vlan1", "-1");

  public static final Set<String> GOOD_REFERENCES = ImmutableSet.of(
      "ether://00:1a:2b:3c:4d:5e",
      "ether://aa:bb:cc:dd:ee:ff"
  );
  public static final Set<String> BAD_REFERENCES = ImmutableSet.of(
      "ether:/00:1a:2b:3c:4d:5e",
      "http://00:1a:2b:3c:4d:5e",
      "ether://00-1a-2b-3c-4d-5e" // Invalid address in ref
  );

  private final EtherFamilyProvider provider = new EtherFamilyProvider();

  private String validate(Runnable validator) {
    return catchToMessage(validator);
  }

  @Test
  public void ether_addr_validation() {
    List<String> goodErrors = GOOD_ADDRS.stream()
        .map(addr -> validate(() -> provider.validateAddr(addr)))
        .filter(GeneralUtils::isNotEmpty).toList();
    assertTrue("Unexpected addr errors: " + CSV_JOINER.join(goodErrors), goodErrors.isEmpty());

    List<String> badErrors = BAD_ADDRS.stream()
        .map(addr -> validate(() -> provider.validateAddr(addr)))
        .filter(GeneralUtils::isNotEmpty).toList();
    assertEquals("Not enough validation errors for addrs", BAD_ADDRS.size(), badErrors.size());
  }

  @Test
  public void ether_network_validation() {
    List<String> goodErrors = GOOD_NETWORKS.stream()
        .map(net -> validate(() -> provider.validateNetwork(net)))
        .filter(GeneralUtils::isNotEmpty).toList();
    assertTrue("Unexpected network errors: " + CSV_JOINER.join(goodErrors), goodErrors.isEmpty());

    List<String> badErrors = BAD_NETWORKS.stream()
        .map(net -> validate(() -> provider.validateNetwork(net)))
        .filter(GeneralUtils::isNotEmpty).toList();
    assertEquals("Not enough validation errors for networks", BAD_NETWORKS.size(),
        badErrors.size());
  }

  @Test
  public void ether_ref_validation() {
    List<String> goodErrors = GOOD_REFERENCES.stream()
        .map(ref -> validate(() -> provider.validateUrl(ref)))
        .filter(GeneralUtils::isNotEmpty).toList();
    assertTrue("Unexpected ref errors: " + CSV_JOINER.join(goodErrors), goodErrors.isEmpty());

    List<String> badErrors = BAD_REFERENCES.stream()
        .map(ref -> validate(() -> provider.validateUrl(ref)))
        .filter(GeneralUtils::isNotEmpty).toList();
    assertEquals("Not enough validation errors for refs", BAD_REFERENCES.size(), badErrors.size());
  }
}