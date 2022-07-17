
package udmi.schema;

import java.util.Date;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Device Validation Event
 * <p>
 * Validation summary information for an individual device.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "last_seen"
})
@Generated("jsonschema2pojo")
public class DeviceValidationEvent {

    /**
     * Last time any message from this device was received
     * 
     */
    @JsonProperty("last_seen")
    @JsonPropertyDescription("Last time any message from this device was received")
    public Date last_seen;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.last_seen == null)? 0 :this.last_seen.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof DeviceValidationEvent) == false) {
            return false;
        }
        DeviceValidationEvent rhs = ((DeviceValidationEvent) other);
        return ((this.last_seen == rhs.last_seen)||((this.last_seen!= null)&&this.last_seen.equals(rhs.last_seen)));
    }

}
