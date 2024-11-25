package com.google.daq.mqtt.validator;

import static java.lang.Boolean.TRUE;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.validator.ReportingDevice.MetadataDiff;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetEvents;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointPointsetState;
import udmi.schema.PointsetEvents;
import udmi.schema.PointsetState;
import udmi.schema.State;

/**
 * Manage pointset validation.
 */
public class ReportingPointset {

  private final Metadata metadata;

  public ReportingPointset(Metadata metadata) {
    this.metadata = metadata;
  }

  MetadataDiff validateMessage(State message) {
    if (message.pointset != null) {
      return validateMessage(message.pointset);
    }

    if (metadata.pointset == null) {
      return null;
    }

    // Return with internal fields null, to indicate that entire subsection is missing.
    return new MetadataDiff();
  }

  MetadataDiff validateMessage(PointsetEvents message) {
    MetadataDiff metadataDiff = validateMessage(getPoints(message).keySet());
    if (TRUE.equals(message.partial_update)) {
      metadataDiff.missingPoints = ImmutableSet.of();
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

  Map<String, PointPointsetEvents> getPoints(PointsetEvents message) {
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