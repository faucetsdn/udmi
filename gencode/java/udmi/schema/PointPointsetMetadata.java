
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Point Pointset Metadata
 * <p>
 * Information about a specific point name of the device.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "units",
    "writeable",
    "baseline_value",
    "baseline_tolerance",
    "baseline_state",
    "cov_increment",
    "ref"
})
@Generated("jsonschema2pojo")
public class PointPointsetMetadata {

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
    @JsonProperty("writeable")
    @JsonPropertyDescription("Indicates if this point is writable (else read-only)")
    public Boolean writeable;
    @JsonProperty("baseline_value")
    public Object baseline_value;
    @JsonProperty("baseline_tolerance")
    public Double baseline_tolerance;
    @JsonProperty("baseline_state")
    public String baseline_state;
    /**
     * Triggering threshold for partial cov update publishing
     * 
     */
    @JsonProperty("cov_increment")
    @JsonPropertyDescription("Triggering threshold for partial cov update publishing")
    public Double cov_increment;
    /**
     * A local network reference for a point, e.g. BACnet address or Modbus register
     * 
     */
    @JsonProperty("ref")
    @JsonPropertyDescription("A local network reference for a point, e.g. BACnet address or Modbus register")
    public String ref;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.ref == null)? 0 :this.ref.hashCode()));
        result = ((result* 31)+((this.baseline_value == null)? 0 :this.baseline_value.hashCode()));
        result = ((result* 31)+((this.baseline_state == null)? 0 :this.baseline_state.hashCode()));
        result = ((result* 31)+((this.units == null)? 0 :this.units.hashCode()));
        result = ((result* 31)+((this.writeable == null)? 0 :this.writeable.hashCode()));
        result = ((result* 31)+((this.baseline_tolerance == null)? 0 :this.baseline_tolerance.hashCode()));
        result = ((result* 31)+((this.cov_increment == null)? 0 :this.cov_increment.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PointPointsetMetadata) == false) {
            return false;
        }
        PointPointsetMetadata rhs = ((PointPointsetMetadata) other);
        return ((((((((this.ref == rhs.ref)||((this.ref!= null)&&this.ref.equals(rhs.ref)))&&((this.baseline_value == rhs.baseline_value)||((this.baseline_value!= null)&&this.baseline_value.equals(rhs.baseline_value))))&&((this.baseline_state == rhs.baseline_state)||((this.baseline_state!= null)&&this.baseline_state.equals(rhs.baseline_state))))&&((this.units == rhs.units)||((this.units!= null)&&this.units.equals(rhs.units))))&&((this.writeable == rhs.writeable)||((this.writeable!= null)&&this.writeable.equals(rhs.writeable))))&&((this.baseline_tolerance == rhs.baseline_tolerance)||((this.baseline_tolerance!= null)&&this.baseline_tolerance.equals(rhs.baseline_tolerance))))&&((this.cov_increment == rhs.cov_increment)||((this.cov_increment!= null)&&this.cov_increment.equals(rhs.cov_increment))));
    }

}
