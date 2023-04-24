
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
    "yin",
    "yang"
})
@Generated("jsonschema2pojo")
public class BridgePodConfiguration {

    /**
     * Endpoint Configuration
     * <p>
     * Parameters to define an MQTT endpoint
     * 
     */
    @JsonProperty("yin")
    @JsonPropertyDescription("Parameters to define an MQTT endpoint")
    public EndpointConfiguration yin;
    /**
     * Endpoint Configuration
     * <p>
     * Parameters to define an MQTT endpoint
     * 
     */
    @JsonProperty("yang")
    @JsonPropertyDescription("Parameters to define an MQTT endpoint")
    public EndpointConfiguration yang;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.yang == null)? 0 :this.yang.hashCode()));
        result = ((result* 31)+((this.yin == null)? 0 :this.yin.hashCode()));
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
        return (((this.yang == rhs.yang)||((this.yang!= null)&&this.yang.equals(rhs.yang)))&&((this.yin == rhs.yin)||((this.yin!= null)&&this.yin.equals(rhs.yin))));
    }

}
