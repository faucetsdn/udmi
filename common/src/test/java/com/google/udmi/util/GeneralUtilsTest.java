package com.google.udmi.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * Tests for general utilities.
 */
public class GeneralUtilsTest {

  @Test
  @SuppressWarnings("unchecked")
  public void deepCopy() {
    Map<String, Object> original = new HashMap<>();
    original.put("A", "B");
    original.put("C", ImmutableMap.of("D", "E"));
    Map<String, Object> copy = GeneralUtils.deepCopy(original);
    assertEquals("copy is equal", original, copy);
    ((Map<String, String>) copy.get("C")).put("F", "G");
    assertNotEquals("copy is not equal", original, copy);
  }

  @Test
  public void deepMergeDefaults() {
    throw new RuntimeException("Nope");
  }

  @Test
  public void testDeepMergeDefaults() {
    throw new RuntimeException("Nope");
  }
}