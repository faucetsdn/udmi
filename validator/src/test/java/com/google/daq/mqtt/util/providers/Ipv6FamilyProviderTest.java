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
 * Basic tests for the ipv6 family provider.
 */
public class Ipv6FamilyProviderTest {

  public static final Set<String> GOOD_ADDRS = ImmutableSet.of(
      "2001:db8::8a2e:370:7334",
      "::1",
      "fe80::1ff:fe23:4567:890a"
  );

  // These are all structurally valid but NOT canonical or lowercase.
  public static final Set<String> BAD_ADDRS = ImmutableSet.of(
      "",
      "2001:DB8::1",              // Uppercase
      "2001:0db8::1",             // Leading zero
      "2001:db8:0:0::1",          // Not maximally compressed
      "2001:db8::1::1",           // Two double-colons
      "192.168.1.1"               // Is an IPv4 address
  );

  public static final Set<String> GOOD_NETWORKS = ImmutableSet.of(
      "2001:db8::/32",
      "::1/128"
  );
  public static final Set<String> BAD_NETWORKS = ImmutableSet.of(
      "2001:db8::",              // No prefix
      "2001:db8::/129",          // Prefix out of range
      "2001:DB8::/32"            // Non-canonical address part
  );

  public static final Set<String> GOOD_REFERENCES = ImmutableSet.of(
      "ipv6://[2001:db8::1]:8080",
      "ipv6://[2001:db8::1]",
      "ipv6://[::1]:443"
  );
  public static final Set<String> BAD_REFERENCES = ImmutableSet.of(
      "ipv6://2001:db8::1:8080",      // No brackets
      "ipv6://[2001:DB8::1]:8080",    // Non-canonical address
      "ipv6://[::1]:99999",           // Invalid port
      "http://[::1]:80"               // Wrong scheme
  );

  private final Ipv6FamilyProvider provider = new Ipv6FamilyProvider();

  private String validate(Runnable validator) {
    return catchToMessage(validator);
  }

  @Test
  public void ipv6_addr_validation() {
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
  public void ipv6_network_validation() {
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
  public void ipv6_ref_validation() {
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