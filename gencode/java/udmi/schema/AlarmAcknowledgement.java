
package udmi.schema;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Alarm Acknowledgement
 * <p>
 * Details about an alarm acknowledgement
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "user",
    "comment"
})
public class AlarmAcknowledgement {

    /**
     * Timestamp of when the alarm was acknowledged.
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("Timestamp of when the alarm was acknowledged.")
    public Date timestamp;
    /**
     * Name of the user who acknowledged this alarm.
     * 
     */
    @JsonProperty("user")
    @JsonPropertyDescription("Name of the user who acknowledged this alarm.")
    public String user;
    /**
     * Comment left by the user when acknowledging this alarm.
     * 
     */
    @JsonProperty("comment")
    @JsonPropertyDescription("Comment left by the user when acknowledging this alarm.")
    public String comment;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.user == null)? 0 :this.user.hashCode()));
        result = ((result* 31)+((this.comment == null)? 0 :this.comment.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof AlarmAcknowledgement) == false) {
            return false;
        }
        AlarmAcknowledgement rhs = ((AlarmAcknowledgement) other);
        return ((((this.user == rhs.user)||((this.user!= null)&&this.user.equals(rhs.user)))&&((this.comment == rhs.comment)||((this.comment!= null)&&this.comment.equals(rhs.comment))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
