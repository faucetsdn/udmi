package com.google.bos.udmi.service.core;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.udmi.util.JsonUtil;
import java.util.Map;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.DiscoveryEvent;
import udmi.schema.EndpointConfiguration;

/**
 * Adapter class for consuming raw bitbox (non-UDMI format) messages and rejiggering them to conform
 * to the normalized schema.
 */
@ComponentName("bitbox")
public class BitboxAdapter extends ProcessorBase {

  public static final String BACNET_PROTOCOL = "bacnet";

  public BitboxAdapter(EndpointConfiguration config) {
    super(config);
  }

  @Override
  protected void defaultHandler(Object defaultedMessage) {
    MessageContinuation continuation = getContinuation(defaultedMessage);
    Map<String, Object> stringObjectMap = JsonUtil.asMap(defaultedMessage);
    if (!BACNET_PROTOCOL.equals(stringObjectMap.get("protocol"))) {
      return;
    }
    continuation.publish(convertDiscovery(defaultedMessage));
  }

  private DiscoveryEvent convertDiscovery(Object defaultedMessage) {
    DiscoveryEvent discoveryEvent = new DiscoveryEvent();
    discoveryEvent.scan_family = ProtocolFamily.BACNET;
    return discoveryEvent;
  }
}
