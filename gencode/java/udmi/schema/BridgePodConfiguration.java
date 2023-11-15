
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
    "enabled",
    "from",
    "to"
})
@Generated("jsonschema2pojo")
public class BridgePodConfiguration {

    @JsonProperty("enabled")
    public String enabled;
    /**
     * Endpoint Configuration
     * <p>
     * Parameters to define a message endpoint
     * 
     */
    @JsonProperty("from")
    @JsonPropertyDescription("Parameters to define a message endpoint")
    public EndpointConfiguration from;
    /**
     * Endpoint Configuration
     * <p>
     * Parameters to define a message endpoint
     * 
     */
    @JsonProperty("to")
    @JsonPropertyDescription("Parameters to define a message endpoint")
    public EndpointConfiguration to;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.from == null)? 0 :this.from.hashCode()));
        result = ((result* 31)+((this.to == null)? 0 :this.to.hashCode()));
        result = ((result* 31)+((this.enabled == null)? 0 :this.enabled.hashCode()));
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
        return ((((this.from == rhs.from)||((this.from!= null)&&this.from.equals(rhs.from)))&&((this.to == rhs.to)||((this.to!= null)&&this.to.equals(rhs.to))))&&((this.enabled == rhs.enabled)||((this.enabled!= null)&&this.enabled.equals(rhs.enabled))));
    }

}
