package com.google.daq.mqtt.validator;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetEvent;
import udmi.schema.PointPointsetMetadata;
import udmi.schema.PointsetEvent;

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
  public boolean hasError() {
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
   * Validate a pointset message against the device's metadata.
   *
   * @param message Message to validate
   */
  public void validateMetadata(PointsetEvent message) {
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

  private Map<String, PointPointsetEvent> getPoints(PointsetEvent message) {
    return message.points == null ? ImmutableMap.of() : message.points;
  }

  private Map<String, PointPointsetMetadata> getPoints(Metadata metadata) {
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
    metadataDiff.errors.add(error.toString());
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
   * Encapsulation of metadata differences.
   */
  public static class MetadataDiff {

    public List<String> errors;
    public Set<String> extraPoints;
    public Set<String> missingPoints;
  }
}
