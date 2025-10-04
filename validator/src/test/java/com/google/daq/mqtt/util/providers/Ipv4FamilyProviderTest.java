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
 * Basic tests for the ipv4 family provider.
 */
public class Ipv4FamilyProviderTest {

  public static final Set<String> GOOD_ADDRS = ImmutableSet.of(
      "192.168.1.1",
      "10.0.0.1",
      "8.8.8.8",
      "0.0.0.0",
      "255.255.255.255"
  );
  public static final Set<String> BAD_ADDRS = ImmutableSet.of(
      "",
      "192.168.1.256",   // Octet out of range
      "192.168.1",       // Too short
      "192.168.1.1.1",   // Too long
      "192.168.1.a",     // Invalid character
      "2001:db8::1"      // Is an IPv6 address
  );

  public static final Set<String> GOOD_NETWORKS = ImmutableSet.of(
      "10.0.0.0/8",
      "192.168.1.0/24",
      "8.8.8.8/32"
  );
  public static final Set<String> BAD_NETWORKS = ImmutableSet.of(
      "10.0.0.0",        // No prefix
      "10.0.0.0/33",     // Prefix out of range
      "256.0.0.0/8"      // Invalid address part
  );

  public static final Set<String> GOOD_REFERENCES = ImmutableSet.of(
      "ipv4://192.168.1.1",
      "ipv4://192.168.1.1:8080",
      "ipv4://8.8.8.8:53"
  );
  public static final Set<String> BAD_REFERENCES = ImmutableSet.of(
      "ipv4://192.168.1.256:80",    // Invalid address
      "ipv4://192.168.1.1:99999",   // Invalid port
      "http://192.168.1.1:80"       // Wrong scheme
  );

  private final Ipv4FamilyProvider provider = new Ipv4FamilyProvider();

  private String validate(Runnable validator) {
    return catchToMessage(validator);
  }

  @Test
  public void ipv4_addr_validation() {
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
  public void ipv4_network_validation() {
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
  public void ipv4_ref_validation() {
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