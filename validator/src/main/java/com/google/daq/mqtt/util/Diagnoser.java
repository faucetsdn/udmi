package com.google.daq.mqtt.util;

import com.google.udmi.util.SiteModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple utility to diagnose basic configuration problems.
 */
public class Diagnoser {

  public static void main(String[] args) {
    List<String> argList = new ArrayList<>(List.of(args));
    SiteModel siteModel = new SiteModel("exe", argList);
  }
}
