
package udmi.schema;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Alarm Alarmset Events
 * <p>
 * Object representation for for a single alarm
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "activate_time",
    "activate_ack",
    "active",
    "return_to_normal_time",
    "return_to_normal_ack"
})
public class AlarmAlarmsetEvents {

    /**
     * Timestamp of when the alarm became active.
     * (Required)
     * 
     */
    @JsonProperty("activate_time")
    @JsonPropertyDescription("Timestamp of when the alarm became active.")
    public Date activate_time;
    /**
     * Alarm Acknowledgement
     * <p>
     * Details about an alarm acknowledgement
     * 
     */
    @JsonProperty("activate_ack")
    @JsonPropertyDescription("Details about an alarm acknowledgement")
    public AlarmAcknowledgement activate_ack;
    /**
     * Indicates whether or not the alarm conditions are currently active.
     * (Required)
     * 
     */
    @JsonProperty("active")
    @JsonPropertyDescription("Indicates whether or not the alarm conditions are currently active.")
    public Boolean active;
    /**
     * Timestamp of when the alarm conditions returned to normal.
     * 
     */
    @JsonProperty("return_to_normal_time")
    @JsonPropertyDescription("Timestamp of when the alarm conditions returned to normal.")
    public Date return_to_normal_time;
    /**
     * Alarm Acknowledgement
     * <p>
     * Details about an alarm acknowledgement
     * 
     */
    @JsonProperty("return_to_normal_ack")
    @JsonPropertyDescription("Details about an alarm acknowledgement")
    public AlarmAcknowledgement return_to_normal_ack;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.return_to_normal_time == null)? 0 :this.return_to_normal_time.hashCode()));
        result = ((result* 31)+((this.active == null)? 0 :this.active.hashCode()));
        result = ((result* 31)+((this.activate_time == null)? 0 :this.activate_time.hashCode()));
        result = ((result* 31)+((this.activate_ack == null)? 0 :this.activate_ack.hashCode()));
        result = ((result* 31)+((this.return_to_normal_ack == null)? 0 :this.return_to_normal_ack.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof AlarmAlarmsetEvents) == false) {
            return false;
        }
        AlarmAlarmsetEvents rhs = ((AlarmAlarmsetEvents) other);
        return ((((((this.return_to_normal_time == rhs.return_to_normal_time)||((this.return_to_normal_time!= null)&&this.return_to_normal_time.equals(rhs.return_to_normal_time)))&&((this.active == rhs.active)||((this.active!= null)&&this.active.equals(rhs.active))))&&((this.activate_time == rhs.activate_time)||((this.activate_time!= null)&&this.activate_time.equals(rhs.activate_time))))&&((this.activate_ack == rhs.activate_ack)||((this.activate_ack!= null)&&this.activate_ack.equals(rhs.activate_ack))))&&((this.return_to_normal_ack == rhs.return_to_normal_ack)||((this.return_to_normal_ack!= null)&&this.return_to_normal_ack.equals(rhs.return_to_normal_ack))));
    }

}
