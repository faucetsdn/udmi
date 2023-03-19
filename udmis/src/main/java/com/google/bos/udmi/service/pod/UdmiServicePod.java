package com.google.bos.udmi.service.pod;

import com.google.udmi.util.JsonUtil;

public class UdmiServicePod {

  public static final long TEN_SECONDS_MS = 10 * 1000;

  public static void main(String[] args) {
    new UdmiServicePod();
  }

  public UdmiServicePod() {
    while (true) {
      System.out.println("Hello world " + JsonUtil.getTimestamp());
      JsonUtil.safeSleep(TEN_SECONDS_MS);
    }
  }
}
