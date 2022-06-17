package com.google.bos.iot.core.proxy;

import com.google.common.collect.Maps;
import java.util.Map;

/**
 * Wrapper class for containing a message data string and attributes.
 */
public class MessageBundle {

  public final String data;
  public final Map<String, String> attributes = Maps.newHashMap();

  public MessageBundle(String data) {
    this.data = data;
  }
}
