package com.google.udmi.util;

import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.mergeObject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

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
  public void testDeepCopy() {
    Map<String, Object> original = getBaseMap();
    Map<String, Object> copy = deepCopy(original);
    assertEquals("copy is equal", original, copy);
    ((Map<String, String>) copy.get("C")).put("F", "H");
    assertNotEquals("copy is not equal", original, copy);
  }

  private Map<String, Object> getBaseMap() {
    Map<String, Object> original = new HashMap<>();
    original.put("A", "B");
    original.put("C", ImmutableMap.of("D", "E", "F", "G"));
    return original;
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testMergeObject() {
    final Map<String, Object> target = deepCopy(getBaseMap());
    final Map<String, Object> originalTarget = deepCopy(target);
    final Map<String, Object> source = deepCopy(target);
    source.put("X", "Y");
    final Map<String, String> sourceC = (Map<String, String>) source.get("C");
    sourceC.put("D", "Q");
    sourceC.put("H", "I");
    final Map<String, Object> originalSource = deepCopy(source);
    mergeObject(target, source);
    assertEquals("unmolested source", originalSource, source);
    assertNotEquals("changed target", target, originalTarget);
    assertNull("original X", originalTarget.get("X"));
    assertEquals("target X", "Y", target.get("X"));
    final Map<String, String> originalC = (Map<String, String>) originalTarget.get("C");
    assertEquals("original C.D", "E", originalC.get("D"));
    assertEquals("original C.F", "G", originalC.get("F"));
    assertNull("original C.H", originalC.get("H"));
    final Map<String, String> targetC = (Map<String, String>) target.get("C");
    assertEquals("target C.D", "Q", targetC.get("D"));
    assertEquals("target C.F", "G", targetC.get("F"));
    assertEquals("target C.H", "I", targetC.get("H"));
  }

  @Test
  public void testMergeTyped() {
    Container source = new Container();
    Container target = new Container();
    source.anInt = 2;
    source.bool = true;
    target.string = "hello";
    target.bool = false;
    Container merged = mergeObject(target, source);
    assertEquals("source a", 2, source.anInt);
    assertEquals("target a", 0, target.anInt);
    assertEquals("merged a", 2, merged.anInt);
    assertNull("source b", source.string);
    assertEquals("target b", "hello", target.string);
    assertEquals("merged b", "hello", merged.string);
    assertEquals("source c", true, source.bool);
    assertEquals("target c", false, target.bool);
    assertEquals("merged c", true, merged.bool);
  }

  /**
   * Simple test container class.
   */
  public static class Container {

    public int anInt;
    public String string;
    public Boolean bool;
  }
}