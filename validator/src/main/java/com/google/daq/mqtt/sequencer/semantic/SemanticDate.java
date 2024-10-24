package com.google.daq.mqtt.sequencer.semantic;

import com.google.udmi.util.CleanDateFormat;
import java.time.Instant;
import java.util.Date;

/**
 * Helper class to better manage understanding diffs between objects.
 */
public class SemanticDate extends Date implements SemanticValue {

  private final String description;

  /**
   * New semantic date initialized from an already existing date. Also quantize date to nearest
   * second to make comparisons meaningful.
   *
   * @param description description of what the date is
   * @param date        date to wrap with meaning
   */
  private SemanticDate(String description, Date date) {
    super(date == null ? 0 : CleanDateFormat.cleanDate(date).getTime());
    this.description = description;
  }

  /**
   * Create a new SemanticDate.
   *
   * @param description description for the date
   * @param date        date value itself
   * @return constructed SemanticDate
   */
  public static Date describe(String description, Date date) {
    return date == null ? null : new SemanticDate(description, date);
  }

  public static Date describe(String description, Instant date) {
    return SemanticDate.describe(description, Date.from(date));
  }

  public String getDescription() {
    return description;
  }

  /**
   * Semantic equality provider.
   *
   * @param other other object to compare against
   * @return true if the other object is the same or _means_ the same
   */
  public boolean equals(Object other) {
    boolean semanticMatch = SemanticValue.isSemanticValue(other)
        && getDescription().equals(((SemanticValue) other).getDescription());
    return semanticMatch || super.equals(other);
  }
}
