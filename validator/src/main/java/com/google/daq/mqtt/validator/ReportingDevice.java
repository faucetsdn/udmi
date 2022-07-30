package com.google.daq.mqtt.validator;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import udmi.schema.Entry;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetEvent;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointPointsetState;
import udmi.schema.PointsetEvent;
import udmi.schema.PointsetState;

/**
 * Encapsulation of device data for a basic reporting device.
 */
public class ReportingDevice {

  private final String deviceId;
  private final MetadataDiff metadataDiff = new MetadataDiff();
  private final List<Exception> errors = new ArrayList<>();
  private final Set<String> validatedTypes = new HashSet<>();
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
   * Set the metadata record for this device.
   *
   * @param metadata metadata to set
   */
  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
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
   * Check if this device has been run through a validation pass or not.
   *
   * @return {@code true} if this has been validated
   */
  public boolean hasBeenValidated() {
    return metadataDiff.extraPoints != null;
  }

  /**
   * Check if this device as errors.
   *
   * @return {@code true} if this device has errors
   */
  public boolean hasErrors() {
    return metadataDiff.errors != null && !metadataDiff.errors.isEmpty();
  }

  /**
   * Check if there has been any difference detected by this pass.
   *
   * @return {@code true} if this device has detected errors
   */
  public boolean hasMetadataDiff() {
    return (metadataDiff.extraPoints != null && !metadataDiff.extraPoints.isEmpty())
        || (metadataDiff.missingPoints != null && !metadataDiff.missingPoints.isEmpty());
  }

  /**
   * Get a (string) message for the metadata of this device.
   *
   * @return Device metadata encoded as a string.
   */
  public String metadataMessage() {
    if (metadataDiff.extraPoints != null && !metadataDiff.extraPoints.isEmpty()) {
      return "Extra points: " + Joiner.on(",").join(metadataDiff.extraPoints);
    }
    if (metadataDiff.missingPoints != null && !metadataDiff.missingPoints.isEmpty()) {
      return "Missing points: " + Joiner.on(",").join(metadataDiff.missingPoints);
    }
    return null;
  }

  /**
   * Get the metadata difference for this device.
   *
   * @return Metadata difference
   */
  public MetadataDiff getMetadataDiff() {
    return metadataDiff;
  }

  /**
   * Validate a message against expectations (outside of base schema).
   *
   * @param message Message to validate
   */
  public void validateMessage(Object message) {
    if (message instanceof PointsetEvent) {
      validateMessage((PointsetEvent) message);
    } else if (message instanceof PointsetState) {
      validateMessage((PointsetState) message);
    } else {
      throw new RuntimeException("Unknown message type " + message.getClass().getName());
    }
  }

  private void validateMessage(PointsetEvent message) {
    Set<String> expectedPoints = new TreeSet<>(getPoints(metadata).keySet());
    Set<String> deliveredPoints = new TreeSet<>(getPoints(message).keySet());
    metadataDiff.extraPoints = new TreeSet<>(deliveredPoints);
    metadataDiff.extraPoints.removeAll(expectedPoints);
    if (message.partial_update != null && message.partial_update) {
      metadataDiff.missingPoints = null;
    } else {
      metadataDiff.missingPoints = new TreeSet<>(expectedPoints);
      metadataDiff.missingPoints.removeAll(deliveredPoints);
    }
    if (hasMetadataDiff()) {
      throw new RuntimeException("Metadata validation failed: " + metadataMessage());
    }
  }

  private void validateMessage(PointsetState message) {
    Set<String> expectedPoints = new TreeSet<>(getPoints(metadata).keySet());
    Set<String> deliveredPoints = new TreeSet<>(getPoints(message).keySet());
    metadataDiff.extraPoints = new TreeSet<>(deliveredPoints);
    metadataDiff.extraPoints.removeAll(expectedPoints);
    metadataDiff.missingPoints = new TreeSet<>(expectedPoints);
    metadataDiff.missingPoints.removeAll(deliveredPoints);
    if (hasMetadataDiff()) {
      throw new RuntimeException("Metadata validation failed: " + metadataMessage());
    }
  }

  private Map<String, PointPointsetEvent> getPoints(PointsetEvent message) {
    return message.points == null ? ImmutableMap.of() : message.points;
  }

  private Map<String, PointPointsetState> getPoints(PointsetState message) {
    return message.points == null ? ImmutableMap.of() : message.points;
  }

  private Map<String, PointPointsetModel> getPoints(Metadata metadata) {
    if (metadata == null || metadata.pointset == null || metadata.pointset.points == null) {
      return ImmutableMap.of();
    }
    return metadata.pointset.points;
  }

  /**
   * Add a validation error to this device.
   *
   * @param error Exception to add
   */
  public void addError(Exception error) {
    errors.add(error);
    if (metadataDiff.errors == null) {
      metadataDiff.errors = new ArrayList<>();
    }
    metadataDiff.errors.add(makeEntry(error));
  }

  private Entry makeEntry(Exception error) {
    Entry entry = new Entry();
    entry.message = getExceptionMessage(error);
    String detail = getExceptionCauses(error);
    entry.detail = entry.message.equals(detail) ? null : detail;
    entry.category = "validation.error.simple";
    entry.level = Level.ERROR.value();
    return entry;
  }

  /**
   * Mark the type of message that has been received.
   *
   * @param subFolder Message type
   * @return {@code true} if it has been previously recorded
   */
  public boolean markMessageType(String subFolder) {
    return validatedTypes.add(subFolder);
  }

  /**
   * Create a status entry for this device.
   *
   * @return status entry
   */
  public Entry getErrorStatus() {
    if (errors.isEmpty()) {
      return null;
    }
    return errors.size() == 1 ? makeEntry(errors.get(0)) : makeCompoundEntry(errors);
  }

  private Entry makeCompoundEntry(List<Exception> erx) {
    Entry entry = new Entry();
    entry.category = "validation.error.multiple";
    entry.message = "Multiple validation errors";
    entry.detail = Joiner.on("; ")
        .join(erx.stream().map(this::getExceptionMessage).collect(Collectors.toList()));
    return entry;
  }

  private String getExceptionCauses(Throwable exception) {
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

  private String getExceptionMessage(Throwable exception) {
    String message = exception.getMessage();
    return message != null ? message : exception.toString();
  }

  public List<Entry> getErrors() {
    return metadataDiff.errors == null ? null : metadataDiff.errors;
  }

  /**
   * Clear all errors for this device.
   */
  public void clearErrors() {
    errors.clear();
    if (metadataDiff.errors != null) {
      metadataDiff.errors.clear();
    }
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
