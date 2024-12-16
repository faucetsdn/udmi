package com.google.daq.mqtt.util;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;

public class ContextWrapper {

  private static final ThreadLocal<List<String>> contexts = ThreadLocal.withInitial(ArrayList::new);

  public static <T> T withContext(String context, Supplier<T> supplier) {
    contexts.get().add(context);
    try {
      return supplier.get();
    } catch (Exception e) {
      throw wrapExceptionWithContext(e);
    } finally {
      contexts.get().remove(contexts.get().size() - 1);
    }
  }

  public static void withContext(String context, Runnable action) {
    withContext(context, () -> {
      action.run();
      return null;
    });
  }

  public static String getCurrentContext() {
    List<String> contextList = contexts.get();
    return String.join(" -> ", contextList);
  }

  public static RuntimeException wrapExceptionWithContext(Exception e) {
    RuntimeException wrappedException = new RuntimeException(e);
    for (int i = contexts.get().size() - 1; i >= 0; i--) {
      String context = contexts.get().get(i);
      wrappedException = new RuntimeException("While " + context, wrappedException);
    }
    return wrappedException;
  }
}
