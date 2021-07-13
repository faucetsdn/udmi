package com.google.daq.mqtt.validator;

import com.google.common.base.Joiner;

import com.google.common.collect.ImmutableMap;
import java.util.*;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetEvent;
import udmi.schema.PointPointsetMetadata;
import udmi.schema.PointsetEvent;

public class ReportingDevice {

  private final String deviceId;
  private final MetadataDiff metadataDiff = new MetadataDiff();
  private Metadata metadata;
  private List<Exception> errors = new ArrayList<>();
  private Set<String> validatedTypes = new HashSet<>();

  public ReportingDevice(String deviceId) {
    this.deviceId = deviceId;
  }

  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
  }

  public String getDeviceId() {
    return deviceId;
  }

  public boolean hasBeenValidated() {
    return metadataDiff.extraPoints != null;
  }

  public boolean hasError() {
    return metadataDiff.errors != null && !metadataDiff.errors.isEmpty();
  }

  public boolean hasMetadataDiff() {
    return (metadataDiff.extraPoints != null && !metadataDiff.extraPoints.isEmpty())
        || (metadataDiff.missingPoints != null && !metadataDiff.missingPoints.isEmpty());
  }

  public String metadataMessage() {
    if (metadataDiff.extraPoints != null && !metadataDiff.extraPoints.isEmpty()) {
      return "Extra points: " + Joiner.on(",").join(metadataDiff.extraPoints);
    }
    if (metadataDiff.missingPoints != null && !metadataDiff.missingPoints.isEmpty()) {
      return "Missing points: " + Joiner.on(",").join(metadataDiff.missingPoints);
    }
    return null;
  }

  public MetadataDiff getMetadataDiff() {
    return metadataDiff;
  }

  public void validateMetadata(PointsetEvent message) {
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

  private Map<String, PointPointsetMetadata> getPoints(Metadata metadata) {
    if (metadata == null || metadata.pointset == null || metadata.pointset.points == null) {
      return ImmutableMap.of();
    }
    return metadata.pointset.points;
  }

  public void addError(Exception error) {
    errors.add(error);
    if (metadataDiff.errors == null) {
      metadataDiff.errors = new ArrayList<>();
    }
    metadataDiff.errors.add(error.toString());
  }

  public boolean markMessageType(String subFolder) {
    return validatedTypes.add(subFolder);
  }

  public static class MetadataDiff {
    public List<String> errors;
    public Set<String> extraPoints;
    public Set<String> missingPoints;
  }
}
