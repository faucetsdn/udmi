
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
    "udmis_flow"
})
@Generated("jsonschema2pojo")
public class PodConfiguration {

    /**
     * Message Configuration
     * <p>
     * Parameters for configuring a message in/out pipeline
     * 
     */
    @JsonProperty("udmis_flow")
    @JsonPropertyDescription("Parameters for configuring a message in/out pipeline")
    public MessageConfiguration udmis_flow;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.udmis_flow == null)? 0 :this.udmis_flow.hashCode()));
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
        return ((this.udmis_flow == rhs.udmis_flow)||((this.udmis_flow!= null)&&this.udmis_flow.equals(rhs.udmis_flow)));
    }

}
