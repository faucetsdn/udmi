package com.google.bos.udmi.service.access;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import udmi.schema.IotAccess;

class IotAccessBaseTest {

  @Test
  public void emptyOptions() {
    IotAccess access = new IotAccess();
    LocalIotAccessProvider localIotAccessProvider = new LocalIotAccessProvider(access);
    assertEquals(ImmutableMap.of(), localIotAccessProvider.options, "empty options");
  }

  @Test
  public void optionsParser() {
    IotAccess access = new IotAccess();
    access.options = "enable, foo=bar, x=";
    LocalIotAccessProvider localIotAccessProvider = new LocalIotAccessProvider(access);
    ImmutableMap<String, String> expected =
        ImmutableMap.of("enable", "true", "foo", "bar", "x", "");
    assertEquals(expected, localIotAccessProvider.options, "parsed options object");
  }
}
