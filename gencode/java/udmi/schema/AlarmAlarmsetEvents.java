
package udmi.schema;

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
    "present_value"
})
public class AlarmAlarmsetEvents {

    /**
     * The specific alarm data reading.  If the value is numeric, then the type must be integer or number.  If the value is an integer, it should be represented as type integer
     * (Required)
     *
     */
    @JsonProperty("present_value")
    @JsonPropertyDescription("The specific alarm data reading.  If the value is numeric, then the type must be integer or number.  If the value is an integer, it should be represented as type integer")
    public Object present_value;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.present_value == null)? 0 :this.present_value.hashCode()));
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
        return ((this.present_value == rhs.present_value)||((this.present_value!= null)&&this.present_value.equals(rhs.present_value)));
    }

}
