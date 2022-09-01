package com.google.daq.mqtt.validator;

import com.google.common.collect.ImmutableMap;
import com.google.daq.mqtt.validator.ReportingDevice.MetadataDiff;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetEvent;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointPointsetState;
import udmi.schema.PointsetEvent;
import udmi.schema.PointsetState;

/**
 * Manage pointset validation.
 */
public class ReportingPointset {

  private final Metadata metadata;

  public ReportingPointset(Metadata metadata) {
    this.metadata = metadata;
  }

  MetadataDiff validateMessage(PointsetEvent message) {
    MetadataDiff metadataDiff = validateMessage(getPoints(message).keySet());
    if (message.partial_update != null && message.partial_update) {
      metadataDiff.missingPoints = null;
    }
    return metadataDiff;
  }

  MetadataDiff validateMessage(PointsetState message) {
    return validateMessage(getPoints(message).keySet());
  }

  private MetadataDiff validateMessage(Set<String> strings) {
    Set<String> deliveredPoints = new TreeSet<>(strings);
    Set<String> expectedPoints = new TreeSet<>(getPoints(metadata).keySet());
    MetadataDiff metadataDiff = new MetadataDiff();
    metadataDiff.extraPoints = new TreeSet<>(deliveredPoints);
    metadataDiff.extraPoints.removeAll(expectedPoints);
    metadataDiff.missingPoints = new TreeSet<>(expectedPoints);
    metadataDiff.missingPoints.removeAll(deliveredPoints);
    return metadataDiff;
  }

  Map<String, PointPointsetEvent> getPoints(PointsetEvent message) {
    return message.points == null ? ImmutableMap.of() : message.points;
  }

  Map<String, PointPointsetState> getPoints(PointsetState message) {
    return message.points == null ? ImmutableMap.of() : message.points;
  }

  Map<String, PointPointsetModel> getPoints(Metadata metadata) {
    if (metadata == null || metadata.pointset == null || metadata.pointset.points == null) {
      return ImmutableMap.of();
    }
    return metadata.pointset.points;
  }
}