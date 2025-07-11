
package udmi.schema;

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

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.endpoint == null)? 0 :this.endpoint.hashCode()));
        result = ((result* 31)+((this.restart_count == null)? 0 :this.restart_count.hashCode()));
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
        return (((this.endpoint == rhs.endpoint)||((this.endpoint!= null)&&this.endpoint.equals(rhs.endpoint)))&&((this.restart_count == rhs.restart_count)||((this.restart_count!= null)&&this.restart_count.equals(rhs.restart_count))));
    }

}
