
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Alarm Alarmset State
 * <p>
 * Object representation for for a single alarm
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "status"
})
public class AlarmAlarmsetState {

    /**
     * Entry
     * <p>
     * 
     * 
     */
    @JsonProperty("status")
    public Entry status;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof AlarmAlarmsetState) == false) {
            return false;
        }
        AlarmAlarmsetState rhs = ((AlarmAlarmsetState) other);
        return ((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status)));
    }

}
