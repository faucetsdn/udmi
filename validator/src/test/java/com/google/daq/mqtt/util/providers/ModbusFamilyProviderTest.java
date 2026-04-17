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
      "modbus://network/1/holding_register/40001",
      "modbus://network/1/holding_register/40001/10",
      "modbus://network/1/holding_register/40001/10?int16",
      "modbus://network/1/holding_register/40001?int16",
      "modbus://1/i64/1/2/1/0", // misc/bambi/metadata.json (legacy RTU)
      "modbus://1/holding_register/2_byte_signed/0" // docs/specs/modbus.md (legacy RTU)
  );

  public static final Set<String> BAD_REFERENCES = ImmutableSet.of(
      "modbus://network/1/holding_register", // missing address
      "modbus://network/1/holding_register/40001/10/extra/part", // too many parts
      "modbus://network@host:502/1/holding_register/40001", // no port/host/network@ allowed anymore
      "bacnet://network/1/holding_register/40001" // wrong family
  );

  ModbusFamilyProvider provider = new ModbusFamilyProvider();

  private String validateUrl(String ref) {
    String error = catchToMessage(() -> provider.validateUrl(ref));
    if (error == null) {
      System.out.println("VALIDATION PASSED FOR " + ref);
    } else {
      System.out.println("VALIDATION FAILED FOR " + ref + ": " + error);
    }
    return error;
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
