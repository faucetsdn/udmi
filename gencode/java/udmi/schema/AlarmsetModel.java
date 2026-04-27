
package udmi.schema;

import java.util.HashMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Alarmset Model
 * <p>
 * Alarmset representing the abstract system expectation for what the device should be doing, and how it should be configured and operated. This block specifies the expected alarms that a device holds
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "alarms",
    "exclude_alarms_from_config"
})
public class AlarmsetModel {

    /**
     * Information about a specific alarm name of the device.
     * (Required)
     * 
     */
    @JsonProperty("alarms")
    @JsonPropertyDescription("Information about a specific alarm name of the device.")
    public HashMap<String, AlarmAlarmsetModel> alarms;
    @JsonProperty("exclude_alarms_from_config")
    public Boolean exclude_alarms_from_config;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.alarms == null)? 0 :this.alarms.hashCode()));
        result = ((result* 31)+((this.exclude_alarms_from_config == null)? 0 :this.exclude_alarms_from_config.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof AlarmsetModel) == false) {
            return false;
        }
        AlarmsetModel rhs = ((AlarmsetModel) other);
        return (((this.alarms == rhs.alarms)||((this.alarms!= null)&&this.alarms.equals(rhs.alarms)))&&((this.exclude_alarms_from_config == rhs.exclude_alarms_from_config)||((this.exclude_alarms_from_config!= null)&&this.exclude_alarms_from_config.equals(rhs.exclude_alarms_from_config))));
    }

}
