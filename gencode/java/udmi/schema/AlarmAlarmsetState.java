
package udmi.schema;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Alarm Alarmset State
 * <p>
 * Object representation for for a single alarm
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "units",
    "value_state",
    "status"
})
public class AlarmAlarmsetState {

    /**
     * If specified, indicates a programmed alarm unit. If empty, means unspecified or matches configured alarm.
     *
     */
    @JsonProperty("units")
    @JsonPropertyDescription("If specified, indicates a programmed alarm unit. If empty, means unspecified or matches configured alarm.")
    public String units;
    /**
     * State of the individual alarm
     *
     */
    @JsonProperty("value_state")
    @JsonPropertyDescription("State of the individual alarm")
    public AlarmAlarmsetState.Value_state value_state;
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
        result = ((result* 31)+((this.value_state == null)? 0 :this.value_state.hashCode()));
        result = ((result* 31)+((this.units == null)? 0 :this.units.hashCode()));
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
        return ((((this.value_state == rhs.value_state)||((this.value_state!= null)&&this.value_state.equals(rhs.value_state)))&&((this.units == rhs.units)||((this.units!= null)&&this.units.equals(rhs.units))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }


    /**
     * State of the individual alarm
     *
     */
    public enum Value_state {

        INITIALIZING("initializing"),
        APPLIED("applied"),
        UPDATING("updating"),
        OVERRIDDEN("overridden"),
        INVALID("invalid"),
        FAILURE("failure");
        private final String value;
        private final static Map<String, AlarmAlarmsetState.Value_state> CONSTANTS = new HashMap<String, AlarmAlarmsetState.Value_state>();

        static {
            for (AlarmAlarmsetState.Value_state c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Value_state(String value) {
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
        public static AlarmAlarmsetState.Value_state fromValue(String value) {
            AlarmAlarmsetState.Value_state constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
