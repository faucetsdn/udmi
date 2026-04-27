
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Alarm Alarmset Config
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "ref"
})
public class AlarmAlarmsetConfig {

    /**
     * Mapping for the alarm to its internal counterpart
     * 
     */
    @JsonProperty("ref")
    @JsonPropertyDescription("Mapping for the alarm to its internal counterpart")
    public String ref;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.ref == null)? 0 :this.ref.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof AlarmAlarmsetConfig) == false) {
            return false;
        }
        AlarmAlarmsetConfig rhs = ((AlarmAlarmsetConfig) other);
        return ((this.ref == rhs.ref)||((this.ref!= null)&&this.ref.equals(rhs.ref)));
    }

}
