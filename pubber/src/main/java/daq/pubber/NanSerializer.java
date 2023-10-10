package daq.pubber;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;

/**
 * Serializer for a Double that generates a raw NaN rather than quoted NaN.
 */
public class NanSerializer extends JsonSerializer<Double> {

  public static SimpleModule MODULE = new SimpleModule();

  static {
    MODULE.addSerializer(Double.class, new NanSerializer());
  }

  @Override
  public void serialize(Double value, JsonGenerator jsonGenerator,
      SerializerProvider serializerProvider) throws IOException {
    if (value.isNaN()) {
      jsonGenerator.writeRawValue("NaN");
    } else {
      jsonGenerator.writeNumber(value);
    }
  }
}
