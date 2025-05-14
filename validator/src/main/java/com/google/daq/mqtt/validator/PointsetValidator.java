package com.google.daq.mqtt.validator;

import static java.lang.Boolean.TRUE;
import static java.lang.String.format;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.validator.ReportingDevice.MetadataDiff;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.ValidationException;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import udmi.schema.Category;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetEvents;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointPointsetState;
import udmi.schema.PointsetEvents;
import udmi.schema.PointsetState;
import udmi.schema.State;

/** Manage pointset validation. */
public class PointsetValidator {

  private final Metadata metadata;
  private final ErrorCollector errorCollector;
  private Set<String> missingPoints;
  private Set<String> extraPoints;
  private Set<String> outOfRangePoints;

  public PointsetValidator(ErrorCollector errorCollector, Metadata metadata) {
    this.errorCollector = errorCollector;
    this.metadata = metadata;
  }

  /** Validate a typed message. */
  public void validateMessage(Object message, Map<String, String> attributes) {
    final MetadataDiff pointsetDiff;
    final Date timestamp;
    final Map<String, PointPointsetEvents> points;

    if (message instanceof State stateMessage) {
      pointsetDiff = validateMessage(stateMessage);
      timestamp = stateMessage.timestamp;
    } else if (message instanceof PointsetState pointsetState) {
      pointsetDiff = validateMessage(pointsetState);
      timestamp = pointsetState.timestamp;
    } else if (message instanceof PointsetEvents pointsetEvents) {
      pointsetDiff = validateMessage(pointsetEvents);
      timestamp = pointsetEvents.timestamp;
    } else {
      return;
    }

    if (pointsetDiff == null) {
      return;
    }

    missingPoints = pointsetDiff.missingPoints;

    String messageDetail = makeMessageDetail(timestamp, attributes);

    if (missingPoints == null) {
      addError(new ValidationException("missing pointset subblock"), messageDetail);
    } else if (!missingPoints.isEmpty()) {
      addError(pointValidationError("missing points", missingPoints), messageDetail);
    }

    extraPoints = pointsetDiff.extraPoints;
    if (extraPoints != null && !extraPoints.isEmpty()) {
      addError(pointValidationError("extra points", extraPoints), messageDetail);
    }

    outOfRangePoints = pointsetDiff.outOfRangePoints;
    if (outOfRangePoints != null && !outOfRangePoints.isEmpty()) {
      addError(pointValidationError("points out of range", outOfRangePoints), messageDetail);
    }
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

    metadataDiff.outOfRangePoints = validatePointValues(getPoints(metadata), getPoints(message));
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

  private Set<String> validatePointValues(
      Map<String, PointPointsetModel> modelPoints, Map<String, PointPointsetEvents> points) {
    Set<String> outOfRangeErrors = new HashSet<>();
    for (Entry<String, PointPointsetEvents> entry : points.entrySet()) {
      String pointName = entry.getKey();
      PointPointsetModel pointModel = modelPoints.get(pointName);
      if (pointModel == null) {
        continue;
      }

      if (pointModel.range_min == null && pointModel.range_max == null) {
        continue;
      }

      PointPointsetEvents point = points.get(pointName);

      if (pointModel.range_min != null || pointModel.range_max != null) {
        if (!Number.class.isInstance(point.present_value)) {
          outOfRangeErrors.add(String.format("%s: not numeric value, but range is set", pointName));
        }

        Double presentValue = ((Number) point.present_value).doubleValue();
        if (pointModel.range_max != null && presentValue > pointModel.range_max) {
          outOfRangeErrors.add(
              String.format(
                  "%s: present_value %s greater than range_max %s",
                  pointName, presentValue, pointModel.range_max));
        } else if (pointModel.range_min != null && presentValue < pointModel.range_min) {
          outOfRangeErrors.add(
              String.format(
                  "%s: present_value %s lower than range_min %s",
                  pointName, presentValue, pointModel.range_min));
        }
      }
    }
    return outOfRangeErrors;
  }

  private String makeMessageDetail(Date timestamp, Map<String, String> attributes) {
    return "While validating pointset message at " + JsonUtil.isoConvert(timestamp);
  }

  private void addError(Exception error, String detail) {
    errorCollector.addError(error, Category.VALIDATION_DEVICE_CONTENT, detail);
  }

  private Exception pointValidationError(String description, Set<String> points) {
    return new ValidationException(
        format("Device has %s: %s", description, Joiner.on(", ").join(points)));
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

  public Set<String> getMissingPoints() {
    return missingPoints;
  }

  public Set<String> getExtraPoints() {
    return extraPoints;
  }
}
