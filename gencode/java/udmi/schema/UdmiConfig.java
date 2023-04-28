
package udmi.schema;

import java.util.Date;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Udmi Config
 * <p>
 * Config for a UDMI reflector client
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "last_state",
    "setup"
})
@Generated("jsonschema2pojo")
public class UdmiConfig {

    @JsonProperty("last_state")
    public Date last_state;
    /**
     * Setup Udmi Config
     * <p>
     * 
     * 
     */
    @JsonProperty("setup")
    public SetupUdmiConfig setup;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.setup == null)? 0 :this.setup.hashCode()));
        result = ((result* 31)+((this.last_state == null)? 0 :this.last_state.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof UdmiConfig) == false) {
            return false;
        }
        UdmiConfig rhs = ((UdmiConfig) other);
        return (((this.setup == rhs.setup)||((this.setup!= null)&&this.setup.equals(rhs.setup)))&&((this.last_state == rhs.last_state)||((this.last_state!= null)&&this.last_state.equals(rhs.last_state))));
    }

}
