
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
    "state_flow",
    "target_flow"
})
@Generated("jsonschema2pojo")
public class PodConfiguration {

    /**
     * Message Configuration
     * <p>
     * Parameters for configuring a message in/out pipeline
     * 
     */
    @JsonProperty("state_flow")
    @JsonPropertyDescription("Parameters for configuring a message in/out pipeline")
    public MessageConfiguration state_flow;
    /**
     * Message Configuration
     * <p>
     * Parameters for configuring a message in/out pipeline
     * 
     */
    @JsonProperty("target_flow")
    @JsonPropertyDescription("Parameters for configuring a message in/out pipeline")
    public MessageConfiguration target_flow;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.state_flow == null)? 0 :this.state_flow.hashCode()));
        result = ((result* 31)+((this.target_flow == null)? 0 :this.target_flow.hashCode()));
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
        return (((this.state_flow == rhs.state_flow)||((this.state_flow!= null)&&this.state_flow.equals(rhs.state_flow)))&&((this.target_flow == rhs.target_flow)||((this.target_flow!= null)&&this.target_flow.equals(rhs.target_flow))));
    }

}
