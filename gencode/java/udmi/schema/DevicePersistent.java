
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Device Persistent
 * <p>
 * Device persistent data
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "endpoint",
    "restarts"
})
@Generated("jsonschema2pojo")
public class DevicePersistent {

    /**
     * Endpoint Configuration
     * <p>
     * Parameters to define an MQTT endpoint
     * 
     */
    @JsonProperty("endpoint")
    @JsonPropertyDescription("Parameters to define an MQTT endpoint")
    public EndpointConfiguration endpoint;
    @JsonProperty("restarts")
    public Integer restarts;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.restarts == null)? 0 :this.restarts.hashCode()));
        result = ((result* 31)+((this.endpoint == null)? 0 :this.endpoint.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof DevicePersistent) == false) {
            return false;
        }
        DevicePersistent rhs = ((DevicePersistent) other);
        return (((this.restarts == rhs.restarts)||((this.restarts!= null)&&this.restarts.equals(rhs.restarts)))&&((this.endpoint == rhs.endpoint)||((this.endpoint!= null)&&this.endpoint.equals(rhs.endpoint))));
    }

}
