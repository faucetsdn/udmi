package com.google.daq.mqtt.util;

import static org.junit.Assert.assertEquals;

import com.google.api.services.cloudiot.v1.model.Device;
import com.google.udmi.util.JsonUtil;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import org.junit.Test;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Resource_type;
import udmi.schema.Credential;
import udmi.schema.Credential.Key_format;

/**
 * Simple tests for core provider helper functions.
 */
public class IotCoreProviderTest {

  @Test
  public void conversions() {
    CloudModel original = new CloudModel();
    original.num_id = "123456782182321390";
    original.metadata = new HashMap<>();
    original.metadata.put("A", "B");
    original.last_event_time = new Date();
    original.blocked = true;
    original.resource_type = Resource_type.GATEWAY;
    original.credentials = new LinkedList<>();
    Credential credential = new Credential();
    credential.key_format = Key_format.ES_256;
    credential.key_data = "92173921uhdjwqd982hdo8qwhd08qwdh";
    original.credentials.add(credential);
    String stringify = JsonUtil.stringify(original);
    Device converted = IotCoreProvider.convert(original);
    CloudModel result = IotCoreProvider.convert(converted);
    assertEquals("converted device", stringify, JsonUtil.stringify(result));
  }
}