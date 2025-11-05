
package udmi.schema;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;


/**
 * Point Pointset Model
 * <p>
 * Information about a specific point name of the device.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "units",
    "description",
    "writable",
    "baseline_value",
    "baseline_tolerance",
    "baseline_state",
    "range_min",
    "range_max",
    "unchanged_limit_sec",
    "cov_increment",
    "ref",
    "adjunct",
    "tags",
    "structure"
})
public class PointPointsetModel {

    /**
     * Expected unit configuration for the point
     * 
     */
    @JsonProperty("units")
    @JsonPropertyDescription("Expected unit configuration for the point")
    public java.lang.String units;
    /**
     * Detailed description of this point
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("Detailed description of this point")
    public java.lang.String description;
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
     * Represents the lower bound of the error threshold for a point
     * 
     */
    @JsonProperty("range_min")
    @JsonPropertyDescription("Represents the lower bound of the error threshold for a point")
    public Double range_min;
    /**
     * Represents the upper bound of the error threshold for a point
     * 
     */
    @JsonProperty("range_max")
    @JsonPropertyDescription("Represents the upper bound of the error threshold for a point")
    public Double range_max;
    /**
     * Represents the limit in seconds that a point can be unchanged for
     * 
     */
    @JsonProperty("unchanged_limit_sec")
    @JsonPropertyDescription("Represents the limit in seconds that a point can be unchanged for")
    public Integer unchanged_limit_sec;
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
    public java.lang.String ref;
    @JsonProperty("adjunct")
    public Map<String, String> adjunct;
    /**
     * Tags assosciated with the point
     * 
     */
    @JsonProperty("tags")
    @JsonDeserialize(as = java.util.LinkedHashSet.class)
    @JsonPropertyDescription("Tags assosciated with the point")
    public Set<Object> tags;
    /**
     * Collection of family point information
     * 
     */
    @JsonProperty("structure")
    @JsonPropertyDescription("Collection of family point information")
    public Map<String, RefDiscovery> structure;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.baseline_state == null)? 0 :this.baseline_state.hashCode()));
        result = ((result* 31)+((this.range_min == null)? 0 :this.range_min.hashCode()));
        result = ((result* 31)+((this.description == null)? 0 :this.description.hashCode()));
        result = ((result* 31)+((this.units == null)? 0 :this.units.hashCode()));
        result = ((result* 31)+((this.baseline_tolerance == null)? 0 :this.baseline_tolerance.hashCode()));
        result = ((result* 31)+((this.structure == null)? 0 :this.structure.hashCode()));
        result = ((result* 31)+((this.writable == null)? 0 :this.writable.hashCode()));
        result = ((result* 31)+((this.tags == null)? 0 :this.tags.hashCode()));
        result = ((result* 31)+((this.ref == null)? 0 :this.ref.hashCode()));
        result = ((result* 31)+((this.baseline_value == null)? 0 :this.baseline_value.hashCode()));
        result = ((result* 31)+((this.range_max == null)? 0 :this.range_max.hashCode()));
        result = ((result* 31)+((this.unchanged_limit_sec == null)? 0 :this.unchanged_limit_sec.hashCode()));
        result = ((result* 31)+((this.adjunct == null)? 0 :this.adjunct.hashCode()));
        result = ((result* 31)+((this.cov_increment == null)? 0 :this.cov_increment.hashCode()));
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
        return (((((((((((((((this.baseline_state == rhs.baseline_state)||((this.baseline_state!= null)&&this.baseline_state.equals(rhs.baseline_state)))&&((this.range_min == rhs.range_min)||((this.range_min!= null)&&this.range_min.equals(rhs.range_min))))&&((this.description == rhs.description)||((this.description!= null)&&this.description.equals(rhs.description))))&&((this.units == rhs.units)||((this.units!= null)&&this.units.equals(rhs.units))))&&((this.baseline_tolerance == rhs.baseline_tolerance)||((this.baseline_tolerance!= null)&&this.baseline_tolerance.equals(rhs.baseline_tolerance))))&&((this.structure == rhs.structure)||((this.structure!= null)&&this.structure.equals(rhs.structure))))&&((this.writable == rhs.writable)||((this.writable!= null)&&this.writable.equals(rhs.writable))))&&((this.tags == rhs.tags)||((this.tags!= null)&&this.tags.equals(rhs.tags))))&&((this.ref == rhs.ref)||((this.ref!= null)&&this.ref.equals(rhs.ref))))&&((this.baseline_value == rhs.baseline_value)||((this.baseline_value!= null)&&this.baseline_value.equals(rhs.baseline_value))))&&((this.range_max == rhs.range_max)||((this.range_max!= null)&&this.range_max.equals(rhs.range_max))))&&((this.unchanged_limit_sec == rhs.unchanged_limit_sec)||((this.unchanged_limit_sec!= null)&&this.unchanged_limit_sec.equals(rhs.unchanged_limit_sec))))&&((this.adjunct == rhs.adjunct)||((this.adjunct!= null)&&this.adjunct.equals(rhs.adjunct))))&&((this.cov_increment == rhs.cov_increment)||((this.cov_increment!= null)&&this.cov_increment.equals(rhs.cov_increment))));
    }


    /**
     * Expected state when `baseline_value` is set as the `set_value` for this point the config message
     * 
     */
    public enum Baseline_state {

        APPLIED("applied"),
        UPDATING("updating"),
        OVERRIDDEN("overridden"),
        INVALID("invalid"),
        FAILURE("failure");
        private final java.lang.String value;
        private final static Map<java.lang.String, PointPointsetModel.Baseline_state> CONSTANTS = new HashMap<java.lang.String, PointPointsetModel.Baseline_state>();

        static {
            for (PointPointsetModel.Baseline_state c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Baseline_state(java.lang.String value) {
            this.value = value;
        }

        @Override
        public java.lang.String toString() {
            return this.value;
        }

        @JsonValue
        public java.lang.String value() {
            return this.value;
        }

        @JsonCreator
        public static PointPointsetModel.Baseline_state fromValue(java.lang.String value) {
            PointPointsetModel.Baseline_state constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
