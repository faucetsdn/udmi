package daq.udmi;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class Message {

  public static class State extends UdmiBase {
    public SystemState system = new SystemState();
    public PointsetState pointset;
  }

  public static class Config extends UdmiBase {
    public SystemConfig system;
    public PointsetConfig pointset;
    public GatewayConfig gateway;
  }

  public static class Pointset extends UdmiBase {
    public Map<String, PointData> points = new HashMap<>();
    public Object extraField;
  }

  public static class SystemEvent extends UdmiBase {
    public List<Entry> logentries = new ArrayList<>();
  }

  public static class PointsetState {
    public Map<String, PointState> points = new HashMap<>();
  }

  public static class PointsetConfig {
    public Map<String, PointConfig> points = new HashMap<>();
  }

  public static class PointConfig {
  }

  public static class GatewayConfig {
    public List<String> proxy_ids;
  }

  public static class SystemState {
    public String make_model;
    public Bundle firmware = new Bundle();
    public boolean operational;
    public Date last_config;
    public Map<String, Entry> statuses = new HashMap<>();
  }

  public static class SystemConfig {
    public Integer report_interval_ms;
  }

  public static class PointData {
    public Object present_value;
  }

  public static class PointState {
    public String units;
    public Boolean fault;
  }

  public static class Bundle {
    public String version;
  }

  public static class UdmiBase {
    public Integer version = 1;
    public Date timestamp = new Date();
  }
}
