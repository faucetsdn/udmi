
package udmi.schema;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * StateSystemOperation
 * <p>
 * A collection of state fields that describes the system operation
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "operational",
    "last_start",
    "restart_count",
    "mode"
})
public class StateSystemOperation {

    /**
     * Operational status of the device.
     * (Required)
     * 
     */
    @JsonProperty("operational")
    @JsonPropertyDescription("Operational status of the device.")
    public Boolean operational;
    /**
     * Last time the system started up.
     * 
     */
    @JsonProperty("last_start")
    @JsonPropertyDescription("Last time the system started up.")
    public Date last_start;
    /**
     * Number of system restarts
     * 
     */
    @JsonProperty("restart_count")
    @JsonPropertyDescription("Number of system restarts")
    public Integer restart_count;
    /**
     * System Mode
     * <p>
     * Operating mode for the device. Default is 'active'.
     * 
     */
    @JsonProperty("mode")
    @JsonPropertyDescription("Operating mode for the device. Default is 'active'.")
    public udmi.schema.Operation.SystemMode mode;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.mode == null)? 0 :this.mode.hashCode()));
        result = ((result* 31)+((this.operational == null)? 0 :this.operational.hashCode()));
        result = ((result* 31)+((this.restart_count == null)? 0 :this.restart_count.hashCode()));
        result = ((result* 31)+((this.last_start == null)? 0 :this.last_start.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof StateSystemOperation) == false) {
            return false;
        }
        StateSystemOperation rhs = ((StateSystemOperation) other);
        return (((((this.mode == rhs.mode)||((this.mode!= null)&&this.mode.equals(rhs.mode)))&&((this.operational == rhs.operational)||((this.operational!= null)&&this.operational.equals(rhs.operational))))&&((this.restart_count == rhs.restart_count)||((this.restart_count!= null)&&this.restart_count.equals(rhs.restart_count))))&&((this.last_start == rhs.last_start)||((this.last_start!= null)&&this.last_start.equals(rhs.last_start))));
    }

}
