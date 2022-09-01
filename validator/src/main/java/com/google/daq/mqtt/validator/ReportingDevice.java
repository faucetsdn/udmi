package com.google.daq.mqtt.validator;

import com.google.common.base.Joiner;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import udmi.schema.Entry;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.PointsetEvent;
import udmi.schema.PointsetState;

/**
 * Encapsulation of device data for a basic reporting device.
 */
public class ReportingDevice {

  private static final long THRESHOLD_SEC = 3600;
  private final String deviceId;
  private final List<Entry> entries = new ArrayList<>();
  private final Map<String, Date> messageMarks = new HashMap<>();
  private final Date lastSeen = new Date(0); // Always defined, just start a long time ago!
  private ReportingPointset reportingPointset;
  private Metadata metadata;

  /**
   * Create device with the given id.
   *
   * @param deviceId device iot id
   */
  public ReportingDevice(String deviceId) {
    this.deviceId = deviceId;
  }

  /**
   * Make a status Entry corresponding to a single exception.
   *
   * @param error exception to summarize
   * @return Entry summarizing the exception
   */
  private static Entry makeEntry(Exception error) {
    Entry entry = new Entry();
    entry.message = getExceptionMessage(error);
    String detail = getExceptionCauses(error);
    entry.detail = entry.message.equals(detail) ? null : detail;
    entry.category = "validation.error.simple";
    entry.level = Level.ERROR.value();
    entry.timestamp = new Date();
    return entry;
  }

  private static String getExceptionCauses(Throwable exception) {
    List<String> messages = new ArrayList<>();
    String previousMessage = null;
    while (exception != null) {
      String newMessage = getExceptionMessage(exception);
      if (previousMessage == null || !previousMessage.endsWith(newMessage)) {
        messages.add(newMessage);
        previousMessage = newMessage;
      }
      exception = exception.getCause();
    }
    return Joiner.on("; ").join(messages);
  }

  private static String getExceptionMessage(Throwable exception) {
    String message = exception.getMessage();
    return message != null ? message : exception.toString();
  }

  /**
   * Create a single status Entry for this device (which may have multiple internal Entries).
   *
   * @param entries list of Entry to summarize
   * @return status entry
   */
  public static Entry getSummaryEntry(List<Entry> entries) {
    if (entries.isEmpty()) {
      return null;
    }

    if (entries.size() == 1) {
      return entries.get(0);
    }

    Entry entry = new Entry();
    entry.category = "validation.error.multiple";
    entry.message = "Multiple validation errors";
    entry.detail = Joiner.on("; ")
        .join(entries.stream()
            .map(ReportingDevice::makeEntrySummary)
            .collect(Collectors.toList()));
    entry.level = entries.stream().map(item -> item.level).max(Integer::compareTo)
        .orElse(Level.ERROR.value());
    entry.timestamp = new Date();
    return entry;
  }

  private static String makeEntrySummary(Entry entry) {
    return String.format("%s:%s (%s)", entry.category, entry.message, entry.level);
  }

  /**
   * Get the device's iot id.
   *
   * @return device's iot id
   */
  public String getDeviceId() {
    return deviceId;
  }

  /**
   * Check if this device has been seen (any kind of message).
   *
   * @return {@code true} if this has been seen
   */
  public boolean hasBeenSeen() {
    return lastSeen.after(getThreshold());
  }

  /**
   * Check if this device as errors.
   *
   * @return {@code true} if this device has errors
   */
  public boolean hasErrors() {
    return !entries.isEmpty();
  }

  /**
   * Validate a message against specific message-type expectations (outside of base schema).
   *
   * @param message Message to validate
   */
  public void validateMessageType(Object message) {
    if (reportingPointset == null) {
      return;
    }
    final MetadataDiff metadataDiff;
    if (message instanceof PointsetEvent) {
      metadataDiff = reportingPointset.validateMessage((PointsetEvent) message);
    } else if (message instanceof PointsetState) {
      metadataDiff = reportingPointset.validateMessage((PointsetState) message);
    } else {
      throw new RuntimeException("Unknown message type " + message.getClass().getName());
    }
    metadataDiff.errors.forEach(this::addEntry);
  }

  private void addEntry(Entry entry) {
    entries.add(entry);
  }

  /**
   * Add a validation error to this device.
   *
   * @param error Exception to add
   */
  public void addError(Exception error) {
    entries.add(makeEntry(error));
  }

  /**
   * Get the error Entries associated with this device.
   *
   * @param threshold date threshold beyond which to ignore (null for all)
   *
   * @return Entry list or errors.
   */
  public List<Entry> getErrors(Date threshold) {
    if (threshold == null) {
      return entries;
    }
    return entries.stream()
        .filter(entry -> entry.timestamp.after(threshold))
        .collect(Collectors.toList());
  }

  /**
   * Clear all errors for this device.
   */
  public void clearErrors() {
    entries.clear();
  }

  public Metadata getMetadata() {
    return metadata;
  }

  /**
   * Set the metadata record for this device.
   *
   * @param metadata metadata to set
   */
  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
    this.reportingPointset = new ReportingPointset(metadata);
  }

  public Set<String> getMissingPoints() {
    throw new RuntimeException("Not yet implemented");
  }

  public Set<String> getExtraPoints() {
    throw new RuntimeException("Not yet implemented");
  }

  public void expireEntries() {
    entries.removeIf(entry -> entry.timestamp.before(getThreshold()));
  }

  private Date getThreshold() {
    return Date.from(Instant.now().minusSeconds(THRESHOLD_SEC));
  }

  public boolean markMessageType(String schemaName) {
    Date previous = messageMarks.put(schemaName, new Date());
    return previous == null || previous.before(getThreshold());
  }

  /**
   * Encapsulation of metadata differences.
   */
  public static class MetadataDiff {

    public List<Entry> errors;
    public Set<String> extraPoints;
    public Set<String> missingPoints;
  }
}
