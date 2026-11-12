
package udmi.schema;

import java.util.Date;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Point Pointset Config
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PointPointsetConfig {

    /**
     * Mapping for the point to an internal resource (e.g. BACnet object reference)
     * 
     */
    @JsonProperty("ref")
    @JsonPropertyDescription("Mapping for the point to an internal resource (e.g. BACnet object reference)")
    public java.lang.String ref;
    /**
     * If specified, indicates the units the device should report the data in.
     * 
     */
    @JsonProperty("units")
    @JsonPropertyDescription("If specified, indicates the units the device should report the data in.")
    public java.lang.String units;
    /**
     * Expected data type for the point
     * 
     */
    @JsonProperty("type")
    @JsonPropertyDescription("Expected data type for the point")
    public java.lang.String type;
    /**
     * State value mapping for this point (e.g. 0.0: OFF)
     * 
     */
    @JsonProperty("values")
    @JsonPropertyDescription("State value mapping for this point (e.g. 0.0: OFF)")
    public Map<String, String> values;
    /**
     * Used for cloud writeback functionality, this field specifies the value for a given point in the device's current units. If the value is numeric, then the type must be integer or number.  If the value is an integer, it should be represented as type integer
     * 
     */
    @JsonProperty("set_value")
    @JsonPropertyDescription("Used for cloud writeback functionality, this field specifies the value for a given point in the device's current units. If the value is numeric, then the type must be integer or number.  If the value is an integer, it should be represented as type integer")
    public Object set_value;
    /**
     * RFC 3339 timestamp for the specified point write easing to stop
     * 
     */
    @JsonProperty("stop_time")
    @JsonPropertyDescription("RFC 3339 timestamp for the specified point write easing to stop")
    public Date stop_time;
    /**
     * The CoV (change of value) increment, which when set, enables CoV on the point and defines the minimum change in present value of the respective point before an update is published
     * 
     */
    @JsonProperty("cov_increment")
    @JsonPropertyDescription("The CoV (change of value) increment, which when set, enables CoV on the point and defines the minimum change in present value of the respective point before an update is published")
    public Double cov_increment;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.ref == null)? 0 :this.ref.hashCode()));
        result = ((result* 31)+((this.stop_time == null)? 0 :this.stop_time.hashCode()));
        result = ((result* 31)+((this.set_value == null)? 0 :this.set_value.hashCode()));
        result = ((result* 31)+((this.values == null)? 0 :this.values.hashCode()));
        result = ((result* 31)+((this.units == null)? 0 :this.units.hashCode()));
        result = ((result* 31)+((this.type == null)? 0 :this.type.hashCode()));
        result = ((result* 31)+((this.cov_increment == null)? 0 :this.cov_increment.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PointPointsetConfig) == false) {
            return false;
        }
        PointPointsetConfig rhs = ((PointPointsetConfig) other);
        return ((((((((this.ref == rhs.ref)||((this.ref!= null)&&this.ref.equals(rhs.ref)))&&((this.stop_time == rhs.stop_time)||((this.stop_time!= null)&&this.stop_time.equals(rhs.stop_time))))&&((this.set_value == rhs.set_value)||((this.set_value!= null)&&this.set_value.equals(rhs.set_value))))&&((this.values == rhs.values)||((this.values!= null)&&this.values.equals(rhs.values))))&&((this.units == rhs.units)||((this.units!= null)&&this.units.equals(rhs.units))))&&((this.type == rhs.type)||((this.type!= null)&&this.type.equals(rhs.type))))&&((this.cov_increment == rhs.cov_increment)||((this.cov_increment!= null)&&this.cov_increment.equals(rhs.cov_increment))));
    }

}
