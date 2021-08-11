package daq.pubber;

import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetEvent;
import udmi.schema.PointPointsetState;

public interface AbstractPoint {

  String getName();

  PointPointsetEvent getData();

  void updateData();

  boolean isDirty();

  PointPointsetState getState();

  void setConfig(PointPointsetConfig config);
}
