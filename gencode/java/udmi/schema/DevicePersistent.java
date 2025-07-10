
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


/**
 * Device Persistent
 * <p>
 * Device persistent data
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "endpoint",
    "restart_count"
})
public class DevicePersistent {

    /**
     * Endpoint Configuration
     * <p>
     * Parameters to define a message endpoint
     * 
     */
    @JsonProperty("endpoint")
    @JsonPropertyDescription("Parameters to define a message endpoint")
    public EndpointConfiguration endpoint;
    @JsonProperty("restart_count")
    public Integer restart_count;

    /**
     * Additional properties
     * <p>
     * additional properties stored in the device persistent data
     *
     */
    private final Map<String, Object> additionalProperties = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
      return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
      this.additionalProperties.put(name, value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(endpoint, restart_count, additionalProperties);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof DevicePersistent devicePersistent)) return false;
      return Objects.equals(endpoint, devicePersistent.endpoint) &&
          Objects.equals(restart_count, devicePersistent.restart_count) &&
          Objects.equals(additionalProperties, devicePersistent.additionalProperties);
    }

}
