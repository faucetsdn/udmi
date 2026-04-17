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

public class ModbusFamilyProviderTest {

  public static final Set<String> GOOD_REFERENCES = ImmutableSet.of(
      "modbus://network@192.168.1.1:502/1/3/40001",
      "modbus://192.168.1.1:502/1/3/40001/10",
      "modbus://network@192.168.1.1/1/3/40001/10?type=INT16&byte_order=Big-Endian",
      "modbus://my-host.com/2/4/30001?foo=bar",
      "modbus://10.0.0.1/1/3/40001/2?multiplier=0.1"
  );

  public static final Set<String> BAD_REFERENCES = ImmutableSet.of(
      "modbus://network@192.168.1.1:502/1/3",
      "modbus://network@192.168.1.1:502/1/3/40001/10/extra",
      "modbus://network@192.168.1.1:502/a/3/40001",
      "modbus://network@192.168.1.1:502/1/b/40001",
      "modbus://network@192.168.1.1:502/1/3/c",
      "bacnet://192.168.1.1/1/3/40001"
  );

  ModbusFamilyProvider provider = new ModbusFamilyProvider();

  private String validateUrl(String ref) {
    return catchToMessage(() -> provider.validateUrl(ref));
  }

  @Test
  public void modbus_ref_validation() {
    List<String> goodErrors = GOOD_REFERENCES.stream().map(this::validateUrl)
        .filter(GeneralUtils::isNotEmpty).toList();
    assertTrue("Unexpected ref errors: " + CSV_JOINER.join(goodErrors), goodErrors.isEmpty());
    List<String> badErrors = BAD_REFERENCES.stream().map(this::validateUrl)
        .filter(GeneralUtils::isNotEmpty).toList();
    assertEquals("Not enough validation errors", BAD_REFERENCES.size(), badErrors.size());
  }
}
