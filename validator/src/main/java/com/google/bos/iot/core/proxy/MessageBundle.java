package com.google.bos.iot.core.proxy;

import com.google.common.collect.Maps;
import java.util.Map;

public class MessageBundle {

  public final String data;
  public final Map<String, String> attributes = Maps.newHashMap();

  public MessageBundle(String data) {
    this.data = data;
  }
}
