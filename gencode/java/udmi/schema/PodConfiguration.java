
package udmi.schema;

import java.util.HashMap;
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
    "flows",
    "bridges",
    "iot_access",
    "distributors"
})
@Generated("jsonschema2pojo")
public class PodConfiguration {

    /**
     * Endpoint Configuration
     * <p>
     * Parameters to define a message endpoint
     * 
     */
    @JsonProperty("flow_defaults")
    @JsonPropertyDescription("Parameters to define a message endpoint")
    public EndpointConfiguration flow_defaults;
    @JsonProperty("flows")
    public HashMap<String, EndpointConfiguration> flows;
    @JsonProperty("bridges")
    public HashMap<String, BridgePodConfiguration> bridges;
    /**
     * Iot Access Providers
     * <p>
     * 
     * 
     */
    @JsonProperty("iot_access")
    public HashMap<String, IotAccess> iot_access;
    /**
     * Distributed state managers
     * <p>
     * 
     * 
     */
    @JsonProperty("distributors")
    public HashMap<String, EndpointConfiguration> distributors;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.bridges == null)? 0 :this.bridges.hashCode()));
        result = ((result* 31)+((this.flow_defaults == null)? 0 :this.flow_defaults.hashCode()));
        result = ((result* 31)+((this.distributors == null)? 0 :this.distributors.hashCode()));
        result = ((result* 31)+((this.flows == null)? 0 :this.flows.hashCode()));
        result = ((result* 31)+((this.iot_access == null)? 0 :this.iot_access.hashCode()));
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
        return ((((((this.bridges == rhs.bridges)||((this.bridges!= null)&&this.bridges.equals(rhs.bridges)))&&((this.flow_defaults == rhs.flow_defaults)||((this.flow_defaults!= null)&&this.flow_defaults.equals(rhs.flow_defaults))))&&((this.distributors == rhs.distributors)||((this.distributors!= null)&&this.distributors.equals(rhs.distributors))))&&((this.flows == rhs.flows)||((this.flows!= null)&&this.flows.equals(rhs.flows))))&&((this.iot_access == rhs.iot_access)||((this.iot_access!= null)&&this.iot_access.equals(rhs.iot_access))));
    }

}
