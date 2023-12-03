package com.google.udmi.util;

import static java.lang.String.format;

public record DiffEntry(DiffAction action, String key, String value) {
  public enum DiffAction {
    ADD("Add"),
    SET("Set"),
    REMOVE("Remove");

    final String value;

    DiffAction(String value) {
      this.value = value;
    }
  }
  
  @Override
  public String toString() {
    return action == DiffAction.REMOVE ? format("%s `%s`", action().value, key)
        : format("%s `%s` = `%s`", action().value, key, value);
  }
}
