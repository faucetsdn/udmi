package udmi.lib;

import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetEvents;
import udmi.schema.PointPointsetState;
import udmi.schema.RefDiscovery;

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

  RefDiscovery enumerate();
}
