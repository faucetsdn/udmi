
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
    "exclude_units_from_config",
    "exclude_alarms_from_config",
    "sample_limit_sec",
    "sample_rate_sec"
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
    @JsonProperty("exclude_units_from_config")
    public Boolean exclude_units_from_config;
    @JsonProperty("exclude_alarms_from_config")
    public Boolean exclude_alarms_from_config;
    /**
     * Minimum time between sample updates for the device (including complete and COV updates). Updates more frequent than this should be coalesced into one update.
     *
     */
    @JsonProperty("sample_limit_sec")
    @JsonPropertyDescription("Minimum time between sample updates for the device (including complete and COV updates). Updates more frequent than this should be coalesced into one update.")
    public Integer sample_limit_sec;
    /**
     * Maximum time between samples for the device to send out a complete update. It can send out updates more frequently than this. Default to 600.
     *
     */
    @JsonProperty("sample_rate_sec")
    @JsonPropertyDescription("Maximum time between samples for the device to send out a complete update. It can send out updates more frequently than this. Default to 600.")
    public Integer sample_rate_sec;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.sample_rate_sec == null)? 0 :this.sample_rate_sec.hashCode()));
        result = ((result* 31)+((this.exclude_units_from_config == null)? 0 :this.exclude_units_from_config.hashCode()));
        result = ((result* 31)+((this.exclude_alarms_from_config == null)? 0 :this.exclude_alarms_from_config.hashCode()));
        result = ((result* 31)+((this.sample_limit_sec == null)? 0 :this.sample_limit_sec.hashCode()));
        result = ((result* 31)+((this.alarms == null)? 0 :this.alarms.hashCode()));
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
        return ((((((this.sample_rate_sec == rhs.sample_rate_sec)||((this.sample_rate_sec!= null)&&this.sample_rate_sec.equals(rhs.sample_rate_sec)))&&((this.exclude_units_from_config == rhs.exclude_units_from_config)||((this.exclude_units_from_config!= null)&&this.exclude_units_from_config.equals(rhs.exclude_units_from_config))))&&((this.exclude_alarms_from_config == rhs.exclude_alarms_from_config)||((this.exclude_alarms_from_config!= null)&&this.exclude_alarms_from_config.equals(rhs.exclude_alarms_from_config))))&&((this.sample_limit_sec == rhs.sample_limit_sec)||((this.sample_limit_sec!= null)&&this.sample_limit_sec.equals(rhs.sample_limit_sec))))&&((this.alarms == rhs.alarms)||((this.alarms!= null)&&this.alarms.equals(rhs.alarms))));
    }

}
