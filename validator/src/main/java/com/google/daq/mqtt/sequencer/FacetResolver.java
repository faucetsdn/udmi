package com.google.daq.mqtt.sequencer;

import com.google.udmi.util.SiteModel;
import java.util.List;

public interface FacetResolver {
  List<String> resolve(SiteModel siteModel);
}
