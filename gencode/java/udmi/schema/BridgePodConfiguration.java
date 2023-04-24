
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Bridge Pod Configuration
 * <p>
 * Parameters to define a bridge between message domains
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "from",
    "to"
})
@Generated("jsonschema2pojo")
public class BridgePodConfiguration {

    /**
     * Endpoint Configuration
     * <p>
     * Parameters to define an MQTT endpoint
     * 
     */
    @JsonProperty("from")
    @JsonPropertyDescription("Parameters to define an MQTT endpoint")
    public EndpointConfiguration from;
    /**
     * Endpoint Configuration
     * <p>
     * Parameters to define an MQTT endpoint
     * 
     */
    @JsonProperty("to")
    @JsonPropertyDescription("Parameters to define an MQTT endpoint")
    public EndpointConfiguration to;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.from == null)? 0 :this.from.hashCode()));
        result = ((result* 31)+((this.to == null)? 0 :this.to.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof BridgePodConfiguration) == false) {
            return false;
        }
        BridgePodConfiguration rhs = ((BridgePodConfiguration) other);
        return (((this.from == rhs.from)||((this.from!= null)&&this.from.equals(rhs.from)))&&((this.to == rhs.to)||((this.to!= null)&&this.to.equals(rhs.to))));
    }

}
