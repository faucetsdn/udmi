package daq.pubber;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;

public class NanSerializer extends JsonSerializer<Double> {

  public static SimpleModule MODULE = new SimpleModule();

  static {
    MODULE.addSerializer(Double.class, new NanSerializer());
  }

  @Override
  public void serialize(Double aDouble, JsonGenerator jsonGenerator,
      SerializerProvider serializerProvider) throws IOException {
    if (aDouble.isNaN()) {
      jsonGenerator.writeRawValue("NaN");
    } else {
      jsonGenerator.writeNumber(aDouble);
    }
  }
}
