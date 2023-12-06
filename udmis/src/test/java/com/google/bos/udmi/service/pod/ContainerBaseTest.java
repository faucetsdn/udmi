package com.google.bos.udmi.service.pod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContainerBaseTest {

  private Map<String, String> mockEnvMap = ImmutableMap.of("B", "X", "C", "!Y,Z");

  private ContainerBase getMockContainer() {
    return spy(ContainerBase.class);
  }

  @Test
  public void multiVariable() {
    ContainerBase testContainer = getMockContainer();
    when(testContainer.getEnv(anyString())).thenAnswer(i -> mockEnvMap.get((String) i.getArgument(0)));
    Set<String> strings = testContainer.multiSubstitution("A${A} B${B} C${C}");
    ImmutableSet<String> expected = ImmutableSet.of("A BX CY", "A BX CZ");
    assertEquals(expected, strings, "expanded multi-variable");
  }
}