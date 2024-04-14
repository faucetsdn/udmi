package daq.pubber;

import udmi.schema.PointDiscovery;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetEvents;
import udmi.schema.PointPointsetState;

/**
 * Interface representing a point reading.
 */
public interface AbstractPoint {

  String getName();

  PointPointsetEvents getData();

  void updateData();

  boolean isDirty();

  PointPointsetState getState();

  void setConfig(PointPointsetConfig config);

  PointDiscovery enumerate();
}
