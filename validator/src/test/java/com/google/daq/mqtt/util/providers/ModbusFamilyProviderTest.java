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
      "modbus://host/1/holding_register/40001",
      "modbus://network@host/1/holding_register/40001",
      "modbus://network@host:502/1/holding_register/40001/10",
      "modbus://network@host/1/holding_register/40001/10?int16",
      "modbus://host:502/1/holding_register/40001?int16",
      "modbus://[2001:db8::1]:502/1/holding_register/40001",
      "modbus://1/i64/1/2/1/0", // misc/bambi/metadata.json (legacy RTU)
      "modbus://1/holding_register/2_byte_signed/0" // docs/specs/modbus.md (legacy RTU)
  );

  public static final Set<String> BAD_REFERENCES = ImmutableSet.of(
      "modbus://network@host/1/holding_register", // missing address
      "modbus://network@host/1/holding_register/40001/10/extra", // too many parts
      "modbus://host:65536/1/holding_register/40001", // bad port
      "modbus://@host/1/holding_register/40001", // empty network
      "bacnet://network@host/1/holding_register/40001" // wrong family
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
