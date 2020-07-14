package com.google.daq.mqtt.validator;

import com.google.common.base.Joiner;

import com.google.daq.mqtt.registrar.UdmiSchema;
import com.google.daq.mqtt.registrar.UdmiSchema.Metadata;
import com.google.daq.mqtt.registrar.UdmiSchema.PointsetMessage;
import java.util.*;

public class ReportingDevice {

  private final String deviceId;
  private final MetadataDiff metadataDiff = new MetadataDiff();
  private Metadata metadata;
  private List<Exception> errors = new ArrayList<>();

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
    return metadataDiff.errors != null && metadataDiff.errors.isEmpty();
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

  public void validateMetadata(PointsetMessage message) {
    Set<String> expectedPoints = new TreeSet<>(metadata.pointset.points.keySet());
    Set<String> deliveredPoints = new TreeSet<>(message.points.keySet());
    metadataDiff.extraPoints = new TreeSet<>(deliveredPoints);
    metadataDiff.extraPoints.removeAll(expectedPoints);
    metadataDiff.missingPoints = new TreeSet<>(expectedPoints);
    metadataDiff.missingPoints.removeAll(deliveredPoints);
    if (hasMetadataDiff()) {
      throw new RuntimeException("Metadata validation failed: " + metadataMessage());
    }
  }

  public void addError(Exception error) {
    errors.add(error);
    if (metadataDiff.errors == null) {
      metadataDiff.errors = new ArrayList<>();
    }
    metadataDiff.errors.add(error.toString());
  }

  public static class MetadataDiff {
    public List<String> errors;
    public Set<String> extraPoints;
    public Set<String> missingPoints;
  }
}
