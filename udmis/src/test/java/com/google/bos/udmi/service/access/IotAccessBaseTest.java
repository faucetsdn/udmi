package com.google.bos.udmi.service.access;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import udmi.schema.IotAccess;

class IotAccessBaseTest {

  @Test
  public void emptyOptions() {
    Map<String, Object> stringObjectMap = IotAccessBase.parseOptions(new IotAccess());
    assertEquals(ImmutableMap.of(), stringObjectMap, "empty options");
  }

  @Test
  public void optionsParser() {
    IotAccess access = new IotAccess();
    access.options = "enable, foo=bar, x=";
    Map<String, Object> stringObjectMap = IotAccessBase.parseOptions(access);
    ImmutableMap<String, Object> of =
        ImmutableMap.of("enable", true, "foo", "bar", "x", "");
    assertEquals(of, stringObjectMap, "parsed options object");
  }
}