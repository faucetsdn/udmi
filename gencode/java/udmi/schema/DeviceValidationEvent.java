
package udmi.schema;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
    "last_seen",
    "oldest_mark",
    "status",
    "missing_points"
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
    /**
     * Oldest recorded mark for this device
     * 
     */
    @JsonProperty("oldest_mark")
    @JsonPropertyDescription("Oldest recorded mark for this device")
    public Date oldest_mark;
    /**
     * Entry
     * <p>
     * 
     * 
     */
    @JsonProperty("status")
    public Entry status;
    @JsonProperty("missing_points")
    public List<String> missing_points = new ArrayList<String>();

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.last_seen == null)? 0 :this.last_seen.hashCode()));
        result = ((result* 31)+((this.oldest_mark == null)? 0 :this.oldest_mark.hashCode()));
        result = ((result* 31)+((this.missing_points == null)? 0 :this.missing_points.hashCode()));
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
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
        return (((((this.last_seen == rhs.last_seen)||((this.last_seen!= null)&&this.last_seen.equals(rhs.last_seen)))&&((this.oldest_mark == rhs.oldest_mark)||((this.oldest_mark!= null)&&this.oldest_mark.equals(rhs.oldest_mark))))&&((this.missing_points == rhs.missing_points)||((this.missing_points!= null)&&this.missing_points.equals(rhs.missing_points))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }

}
