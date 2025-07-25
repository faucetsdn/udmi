package com.google.daq.mqtt.sequencer.semantic;

import java.util.ArrayList;
import java.util.List;

/**
 * A list object that provides a semantic description.
 */
public class SemanticList<T> extends ArrayList<T> implements SemanticValue {

  private final String description;

  public SemanticList(String description, List<T> objects) {
    super(objects);
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
