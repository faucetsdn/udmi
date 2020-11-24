package com.google.bos.iot.core.proxy;

import java.util.Date;

public class UDMI {

  static class State {
    public Number version = 1;
    public Date timestamp = new Date();
    public SystemState system = new SystemState();
  }

  static class SystemState {
    public String make_model = "iot_core_proxy";
  }
}
