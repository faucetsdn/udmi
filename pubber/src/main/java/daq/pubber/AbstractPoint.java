package daq.pubber;

import daq.udmi.Message.PointConfig;
import daq.udmi.Message.PointData;
import daq.udmi.Message.PointState;

public interface AbstractPoint {

  String getName();

  PointData getData();

  void updateData();

  boolean isDirty();

  PointState getState();

  void setConfig(PointConfig config);
}
