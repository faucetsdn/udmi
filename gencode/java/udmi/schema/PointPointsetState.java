
package udmi.schema;

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
 * Point Pointset State
 * <p>
 * Object representation for for a single point
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "units",
    "value_state",
    "status"
})
@Generated("jsonschema2pojo")
public class PointPointsetState {

    /**
     * If specified, indicates a programmed point unit. If empty, means unspecified or matches configured point.
     * 
     */
    @JsonProperty("units")
    @JsonPropertyDescription("If specified, indicates a programmed point unit. If empty, means unspecified or matches configured point.")
    public String units;
    /**
     * Optional enumeration indicating the state of the points value. Valid `value_state` settings include:
     * * _<missing>_: No `set_value` _config_ has been specified, the source is device-native.
     * * _applied_: The `set_value` _config_ has been successfully applied.
     * * _overridden_: The _config_ setting is being overridden by another source.
     * * _invalid_: A problem has been identified with the _config_ setting.
     * * _failure_: The _config_ is fine, but a problem has been encountered applying the setting.
     * When the value state indicates an error state, an assosciated status entry should be included
     * 
     */
    @JsonProperty("value_state")
    @JsonPropertyDescription("Optional enumeration indicating the state of the points value. Valid `value_state` settings include:\n* _<missing>_: No `set_value` _config_ has been specified, the source is device-native.\n* _applied_: The `set_value` _config_ has been successfully applied.\n* _overridden_: The _config_ setting is being overridden by another source.\n* _invalid_: A problem has been identified with the _config_ setting.\n* _failure_: The _config_ is fine, but a problem has been encountered applying the setting.\nWhen the value state indicates an error state, an assosciated status entry should be included")
    public PointPointsetState.Value_state value_state;
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
        if ((other instanceof PointPointsetState) == false) {
            return false;
        }
        PointPointsetState rhs = ((PointPointsetState) other);
        return ((((this.value_state == rhs.value_state)||((this.value_state!= null)&&this.value_state.equals(rhs.value_state)))&&((this.units == rhs.units)||((this.units!= null)&&this.units.equals(rhs.units))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }


    /**
     * Optional enumeration indicating the state of the points value. Valid `value_state` settings include:
     * * _<missing>_: No `set_value` _config_ has been specified, the source is device-native.
     * * _applied_: The `set_value` _config_ has been successfully applied.
     * * _overridden_: The _config_ setting is being overridden by another source.
     * * _invalid_: A problem has been identified with the _config_ setting.
     * * _failure_: The _config_ is fine, but a problem has been encountered applying the setting.
     * When the value state indicates an error state, an assosciated status entry should be included
     * 
     */
    @Generated("jsonschema2pojo")
    public enum Value_state {

        APPLIED("applied"),
        UPDATING("updating"),
        OVERRIDDEN("overridden"),
        INVALID("invalid"),
        FAILURE("failure");
        private final String value;
        private final static Map<String, PointPointsetState.Value_state> CONSTANTS = new HashMap<String, PointPointsetState.Value_state>();

        static {
            for (PointPointsetState.Value_state c: values()) {
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
        public static PointPointsetState.Value_state fromValue(String value) {
            PointPointsetState.Value_state constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
