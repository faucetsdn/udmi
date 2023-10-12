package com.google.udmi.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;

/**
 * Serializer for a Double that generates a raw NaN rather than quoted NaN.
 */
public class NanSerializer extends JsonSerializer<Double> {

  public static SimpleModule TO_NULL = new SimpleModule();
  public static SimpleModule TO_NAN = new SimpleModule();

  static {
    TO_NULL.addSerializer(Double.class, new NanSerializer(false));
    TO_NAN.addSerializer(Double.class, new NanSerializer(true));
  }

  private final boolean toNan;

  public NanSerializer(boolean toNan) {
    this.toNan = toNan;
  }

  @Override
  public void serialize(Double value, JsonGenerator jsonGenerator,
      SerializerProvider serializerProvider) throws IOException {
    if (value.isNaN()) {
      jsonGenerator.writeRawValue(toNan ? "NaN" : "null");
    } else {
      jsonGenerator.writeNumber(value);
    }
  }
}
