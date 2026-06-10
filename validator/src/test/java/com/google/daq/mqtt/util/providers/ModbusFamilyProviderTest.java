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
 * Simple tests for the modbus family provider.
 */
public class ModbusFamilyProviderTest {

  public static final Set<String> GOOD_REFERENCES = ImmutableSet.of(
      "modbus://modbus_rtu_1/1/3/40001",
      "modbus://modbus_rtu_1/1/3/40001/10",
      "modbus://modbus_rtu_1/1/3/40001/10?type=INT16&border=MSB",
      "modbus://192.168.1.1/2/4/30001?type=UINT32&worder=LWF",
      "modbus://localhost/2/4/3001",
      "modbus://my-host/1/3/40001/2?scale=0.1",
      "modbus://modbus_rtu_1/1/3/40001/1?offset=1.5",
      "modbus://modbus_rtu_1/1/3/40001/1?border=LSB",
      "modbus://modbus_rtu_1/1/3/40001/1?type=BOOLEAN",
      "modbus://modbus_rtu_1/1/3/40001/1?type=ASCII",
      "modbus://modbus_rtu_1/1/3/40001/1?type=FLOAT32",
      "modbus://modbus_rtu_1/1/3/40001/1?worder=HWF",
      "modbus://modbus_rtu_1/1/3/40001/1?type=UINT32&worder=HWF&border=LSB&scale=10&offset=5",
      "modbus://192.168.1.1:502/1/3/40001"
  );

  public static final Set<String> BAD_REFERENCES = ImmutableSet.of(
      "modbus://network@192.168.1.1:502/1/3/40001",
      "modbus://1/3/40001",
      "modbus://1/3/40001/10",
      "modbus://1/3/40001/10?type=INT16&border=MSB",
      "modbus://2/4/30001?type=UINT32&worder=LWF",
      "modbus://2/4/3001",
      "modbus://1/3/40001/2?scale=0.1",
      "modbus://1/3/40001/1?offset=1.5",
      "modbus://1/3/40001/1?border=LSB",
      "modbus://1/3/40001/1?type=BOOLEAN",
      "modbus://1/3/40001/1?type=ASCII",
      "modbus://1/3/40001/1?type=FLOAT32",
      "modbus://1/3/40001/1?worder=HWF",
      "modbus://1/3/40001/1?type=UINT32&worder=HWF&border=LSB&scale=10&offset=5",
      "modbus://localhost/1/3",
      "modbus://localhost/1/3/40001/10/extra",
      "modbus://localhost/a/3/40001",
      "modbus://localhost/1/b/40001",
      "modbus://localhost/1/3/c",
      "modbus://localhost/1/7/40001", // Invalid function code 7
      "modbus://localhost/1/3/40001?foo=bar", // Invalid parameter foo
      "modbus://localhost/1/3/40001?byte_order=MSB", // Old parameter name
      "modbus://localhost/1/3/40001/1?type=BADTYPE",
      "modbus://localhost/1/3/40001/1?border=BADBORDER",
      "modbus://localhost/1/3/40001/1?worder=BADWORDER",
      "modbus://localhost/1/3/40001/1?scale=abc",
      "modbus://localhost/1/3/40001/1?offset=xyz",
      "modbus://localhost/1/3/40001/1?type", // Missing value
      "modbus://localhost/256/3/40001", // unitid exceeded maximum 255
      "modbus://localhost/01/3/40001", // unitid has leading zero
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
