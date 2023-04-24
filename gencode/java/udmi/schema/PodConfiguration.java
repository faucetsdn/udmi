
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Pod Configuration
 * <p>
 * Parameters for configuring the execution run of a UDMIS pod
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "flow_defaults",
    "target_flow",
    "state_flow"
})
@Generated("jsonschema2pojo")
public class PodConfiguration {

    /**
     * Endpoint Configuration
     * <p>
     * Parameters to define an MQTT endpoint
     * 
     */
    @JsonProperty("flow_defaults")
    @JsonPropertyDescription("Parameters to define an MQTT endpoint")
    public EndpointConfiguration flow_defaults;
    /**
     * Endpoint Configuration
     * <p>
     * Parameters to define an MQTT endpoint
     * 
     */
    @JsonProperty("target_flow")
    @JsonPropertyDescription("Parameters to define an MQTT endpoint")
    public EndpointConfiguration target_flow;
    /**
     * Endpoint Configuration
     * <p>
     * Parameters to define an MQTT endpoint
     * 
     */
    @JsonProperty("state_flow")
    @JsonPropertyDescription("Parameters to define an MQTT endpoint")
    public EndpointConfiguration state_flow;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.flow_defaults == null)? 0 :this.flow_defaults.hashCode()));
        result = ((result* 31)+((this.target_flow == null)? 0 :this.target_flow.hashCode()));
        result = ((result* 31)+((this.state_flow == null)? 0 :this.state_flow.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PodConfiguration) == false) {
            return false;
        }
        PodConfiguration rhs = ((PodConfiguration) other);
        return ((((this.flow_defaults == rhs.flow_defaults)||((this.flow_defaults!= null)&&this.flow_defaults.equals(rhs.flow_defaults)))&&((this.target_flow == rhs.target_flow)||((this.target_flow!= null)&&this.target_flow.equals(rhs.target_flow))))&&((this.state_flow == rhs.state_flow)||((this.state_flow!= null)&&this.state_flow.equals(rhs.state_flow))));
    }

}
