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
 * Basic tests for the fqdn family provider.
 */
public class FqdnFamilyProviderTest {

  public static final Set<String> GOOD_ADDRS = ImmutableSet.of(
      "example.com",
      "sub.domain.co.uk",
      "a.b.c.d.e.f.g.org",
      "test-ing.com",
      "zz-tri-fecta-bms-7.lon.bosiot.in.goog.",
      "xn--fsqu00a.com" // Punycode for internationalized domain
  );
  public static final Set<String> BAD_ADDRS = ImmutableSet.of(
      "",
      "Example.com",         // Uppercase
      "-example.com",        // Starts with hyphen
      "example-.com",        // Ends with hyphen
      "example.-sub.com",    // Label starts with hyphen
      "example.com-",        // TLD ends with hyphen
      "example..com",        // Double dot
      "example.c",           // TLD too short
      "1.2.3.4"              // Looks like an IP
  );

  public static final Set<String> GOOD_NETWORKS = GOOD_ADDRS;
  public static final Set<String> BAD_NETWORKS = BAD_ADDRS;

  public static final Set<String> GOOD_REFERENCES = ImmutableSet.of(
      "fqdn://example.com:80",
      "fqdn://sub.domain.co.uk:443"
  );
  public static final Set<String> BAD_REFERENCES = ImmutableSet.of(
      "fqdn://example.com",         // No port
      "fqdn://-example.com:80",     // Invalid FQDN
      "fqdn://example.com:99999",   // Invalid port
      "http://example.com:80"       // Wrong scheme
  );

  private final FqdnFamilyProvider provider = new FqdnFamilyProvider();

  private String validate(Runnable validator) {
    return catchToMessage(validator);
  }

  @Test
  public void fqdn_addr_validation() {
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
  public void fqdn_network_validation() {
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
  public void fqdn_ref_validation() {
    List<String> goodErrors = GOOD_REFERENCES.stream()
        .map(ref -> validate(() -> provider.validateRef(ref)))
        .filter(GeneralUtils::isNotEmpty).toList();
    assertTrue("Unexpected ref errors: " + CSV_JOINER.join(goodErrors), goodErrors.isEmpty());

    List<String> badErrors = BAD_REFERENCES.stream()
        .map(ref -> validate(() -> provider.validateRef(ref)))
        .filter(GeneralUtils::isNotEmpty).toList();
    assertEquals("Not enough validation errors for refs", BAD_REFERENCES.size(), badErrors.size());
  }
}