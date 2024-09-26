
package udmi.schema;

import java.util.Date;
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
@JsonPropertyOrder({
    "ref",
    "units",
    "set_value",
    "stop_time",
    "cov_increment"
})
public class PointPointsetConfig {

    /**
     * Mapping for the point to an internal resource (e.g. BACnet object reference)
     * 
     */
    @JsonProperty("ref")
    @JsonPropertyDescription("Mapping for the point to an internal resource (e.g. BACnet object reference)")
    public String ref;
    /**
     * If specified, indicates the units the device should report the data in.
     * 
     */
    @JsonProperty("units")
    @JsonPropertyDescription("If specified, indicates the units the device should report the data in.")
    public String units;
    /**
     * Used for cloud writeback functionality, this field specifies the value for a given point in the device's current units.
     * 
     */
    @JsonProperty("set_value")
    @JsonPropertyDescription("Used for cloud writeback functionality, this field specifies the value for a given point in the device's current units.")
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
        result = ((result* 31)+((this.units == null)? 0 :this.units.hashCode()));
        result = ((result* 31)+((this.stop_time == null)? 0 :this.stop_time.hashCode()));
        result = ((result* 31)+((this.set_value == null)? 0 :this.set_value.hashCode()));
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
        return ((((((this.ref == rhs.ref)||((this.ref!= null)&&this.ref.equals(rhs.ref)))&&((this.units == rhs.units)||((this.units!= null)&&this.units.equals(rhs.units))))&&((this.stop_time == rhs.stop_time)||((this.stop_time!= null)&&this.stop_time.equals(rhs.stop_time))))&&((this.set_value == rhs.set_value)||((this.set_value!= null)&&this.set_value.equals(rhs.set_value))))&&((this.cov_increment == rhs.cov_increment)||((this.cov_increment!= null)&&this.cov_increment.equals(rhs.cov_increment))));
    }

}
