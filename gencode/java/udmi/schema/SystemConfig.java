
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * System Config
 * <p>
 * [System Config Documentation](../docs/messages/system.md#config)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "min_loglevel",
    "metrics_rate_sec",
    "mode",
    "latest_start"
})
@Generated("jsonschema2pojo")
public class SystemConfig {

    /**
     * The minimum loglevel for reporting log messages below which log entries should not be sent. Default to 300.
     * 
     */
    @JsonProperty("min_loglevel")
    @JsonPropertyDescription("The minimum loglevel for reporting log messages below which log entries should not be sent. Default to 300.")
    public Integer min_loglevel = 300;
    /**
     * The rate at which the system should send system event metric updates. 0 indicates no updates.
     * 
     */
    @JsonProperty("metrics_rate_sec")
    @JsonPropertyDescription("The rate at which the system should send system event metric updates. 0 indicates no updates.")
    public Integer metrics_rate_sec = 600;
    /**
     * System Mode
     * <p>
     * Operating mode for the device. Defaults is 'active'.
     * 
     */
    @JsonProperty("mode")
    @JsonPropertyDescription("Operating mode for the device. Defaults is 'active'.")
    public SystemConfig.SystemMode mode;
    /**
     * Last time a device with this id said it restarted: being later than status-supplied last_start indicates resource conflict.
     * 
     */
    @JsonProperty("latest_start")
    @JsonPropertyDescription("Last time a device with this id said it restarted: being later than status-supplied last_start indicates resource conflict.")
    public Date latest_start;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.metrics_rate_sec == null)? 0 :this.metrics_rate_sec.hashCode()));
        result = ((result* 31)+((this.mode == null)? 0 :this.mode.hashCode()));
        result = ((result* 31)+((this.latest_start == null)? 0 :this.latest_start.hashCode()));
        result = ((result* 31)+((this.min_loglevel == null)? 0 :this.min_loglevel.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SystemConfig) == false) {
            return false;
        }
        SystemConfig rhs = ((SystemConfig) other);
        return (((((this.metrics_rate_sec == rhs.metrics_rate_sec)||((this.metrics_rate_sec!= null)&&this.metrics_rate_sec.equals(rhs.metrics_rate_sec)))&&((this.mode == rhs.mode)||((this.mode!= null)&&this.mode.equals(rhs.mode))))&&((this.latest_start == rhs.latest_start)||((this.latest_start!= null)&&this.latest_start.equals(rhs.latest_start))))&&((this.min_loglevel == rhs.min_loglevel)||((this.min_loglevel!= null)&&this.min_loglevel.equals(rhs.min_loglevel))));
    }


    /**
     * System Mode
     * <p>
     * Operating mode for the device. Defaults is 'active'.
     * 
     */
    @Generated("jsonschema2pojo")
    public enum SystemMode {

        INITIAL("initial"),
        ACTIVE("active"),
        RESTART("restart");
        private final String value;
        private final static Map<String, SystemConfig.SystemMode> CONSTANTS = new HashMap<String, SystemConfig.SystemMode>();

        static {
            for (SystemConfig.SystemMode c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        SystemMode(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static SystemConfig.SystemMode fromValue(String value) {
            SystemConfig.SystemMode constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
