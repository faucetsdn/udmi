
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
 * Point Pointset Model
 * <p>
 * Information about a specific point name of the device.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "units",
    "writable",
    "baseline_value",
    "baseline_tolerance",
    "baseline_state",
    "cov_increment",
    "ref"
})
@Generated("jsonschema2pojo")
public class PointPointsetModel {

    /**
     * Expected unit configuration for the point
     * 
     */
    @JsonProperty("units")
    @JsonPropertyDescription("Expected unit configuration for the point")
    public String units;
    /**
     * Indicates if this point is writable (else read-only)
     * 
     */
    @JsonProperty("writable")
    @JsonPropertyDescription("Indicates if this point is writable (else read-only)")
    public Boolean writable;
    /**
     * Represents the expected baseline value of the point
     * 
     */
    @JsonProperty("baseline_value")
    @JsonPropertyDescription("Represents the expected baseline value of the point")
    public Object baseline_value;
    /**
     * Maximum deviation from `baseline_value`
     * 
     */
    @JsonProperty("baseline_tolerance")
    @JsonPropertyDescription("Maximum deviation from `baseline_value`")
    public Double baseline_tolerance;
    /**
     * Expected state when `baseline_value` is set as the `set_value` for this point the config message
     * 
     */
    @JsonProperty("baseline_state")
    @JsonPropertyDescription("Expected state when `baseline_value` is set as the `set_value` for this point the config message")
    public PointPointsetModel.Baseline_state baseline_state;
    /**
     * Triggering threshold for partial cov update publishing
     * 
     */
    @JsonProperty("cov_increment")
    @JsonPropertyDescription("Triggering threshold for partial cov update publishing")
    public Double cov_increment;
    /**
     * Mapping for the point to an internal resource (e.g. BACnet object reference)
     * 
     */
    @JsonProperty("ref")
    @JsonPropertyDescription("Mapping for the point to an internal resource (e.g. BACnet object reference)")
    public String ref;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.ref == null)? 0 :this.ref.hashCode()));
        result = ((result* 31)+((this.baseline_value == null)? 0 :this.baseline_value.hashCode()));
        result = ((result* 31)+((this.baseline_state == null)? 0 :this.baseline_state.hashCode()));
        result = ((result* 31)+((this.units == null)? 0 :this.units.hashCode()));
        result = ((result* 31)+((this.baseline_tolerance == null)? 0 :this.baseline_tolerance.hashCode()));
        result = ((result* 31)+((this.cov_increment == null)? 0 :this.cov_increment.hashCode()));
        result = ((result* 31)+((this.writable == null)? 0 :this.writable.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PointPointsetModel) == false) {
            return false;
        }
        PointPointsetModel rhs = ((PointPointsetModel) other);
        return ((((((((this.ref == rhs.ref)||((this.ref!= null)&&this.ref.equals(rhs.ref)))&&((this.baseline_value == rhs.baseline_value)||((this.baseline_value!= null)&&this.baseline_value.equals(rhs.baseline_value))))&&((this.baseline_state == rhs.baseline_state)||((this.baseline_state!= null)&&this.baseline_state.equals(rhs.baseline_state))))&&((this.units == rhs.units)||((this.units!= null)&&this.units.equals(rhs.units))))&&((this.baseline_tolerance == rhs.baseline_tolerance)||((this.baseline_tolerance!= null)&&this.baseline_tolerance.equals(rhs.baseline_tolerance))))&&((this.cov_increment == rhs.cov_increment)||((this.cov_increment!= null)&&this.cov_increment.equals(rhs.cov_increment))))&&((this.writable == rhs.writable)||((this.writable!= null)&&this.writable.equals(rhs.writable))));
    }


    /**
     * Expected state when `baseline_value` is set as the `set_value` for this point the config message
     * 
     */
    @Generated("jsonschema2pojo")
    public enum Baseline_state {

        APPLIED("applied"),
        UPDATING("updating"),
        OVERRIDDEN("overridden"),
        INVALID("invalid"),
        FAILURE("failure");
        private final String value;
        private final static Map<String, PointPointsetModel.Baseline_state> CONSTANTS = new HashMap<String, PointPointsetModel.Baseline_state>();

        static {
            for (PointPointsetModel.Baseline_state c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Baseline_state(String value) {
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
        public static PointPointsetModel.Baseline_state fromValue(String value) {
            PointPointsetModel.Baseline_state constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
