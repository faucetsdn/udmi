package com.google.bos.udmi.service.core;

import udmi.schema.EndpointConfiguration;

/**
 * Adapter class for consuming raw bitbox (non-UDMI format) messages and rejiggering them to conform
 * to the normalized schema.
 */
@ComponentName("bitbox")
public class BitboxAdapter extends ProcessorBase {

  public BitboxAdapter(EndpointConfiguration config) {
    super(config);
  }

}
