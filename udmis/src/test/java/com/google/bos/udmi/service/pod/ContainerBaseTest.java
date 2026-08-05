package com.google.bos.udmi.service.pod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import udmi.schema.BasePodConfiguration;
import udmi.schema.Level;
import udmi.schema.PodConfiguration;

class ContainerBaseTest {

  private final Map<String, String> mockEnvMap = ImmutableMap.of(
      "B", "X",
      "C", "!Y,Z");

  private ContainerBase getMockContainer() {
    return spy(ContainerBase.class);
  }

  @Test
  public void multiVariable() {
    ContainerBase testContainer = getMockContainer();
    when(testContainer.getEnv(anyString())).thenAnswer(
        i -> mockEnvMap.get((String) i.getArgument(0)));
    Set<String> strings = testContainer.multiSubstitution("A${A} B${B} C${C}");
    ImmutableSet<String> expected = ImmutableSet.of("A BX CY", "A BX CZ");
    assertEquals(expected, strings, "expanded multi-variable");
  }

  @Test
  public void logLevelControl() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    try {
      System.setOut(new PrintStream(out));
      PodConfiguration podConfiguration = new PodConfiguration();
      podConfiguration.base = new BasePodConfiguration();
      podConfiguration.base.log_level = "INFO";
      ContainerBase container = new ContainerBase(podConfiguration) {};
      container.debug("Should be ignored at INFO level");
      container.info("Should be visible at INFO level");
      String logOutput = out.toString();
      assertFalse(logOutput.contains("Should be ignored at INFO level"),
          "DEBUG message should be suppressed at INFO level");
      assertTrue(logOutput.contains("Should be visible at INFO level"),
          "INFO message should be logged at INFO level");
      out.reset();
      ContainerBase.setLogLevel(Level.TRACE);
      container.trace("Should be visible at TRACE level");
      assertTrue(out.toString().contains("Should be visible at TRACE level"),
          "TRACE message should be logged at TRACE level");
    } finally {
      System.setOut(originalOut);
    }
  }
}