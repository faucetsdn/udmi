
package udmi.schema;

import javax.annotation.processing.Generated;
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
    "set_value"
})
@Generated("jsonschema2pojo")
public class PointPointsetConfig {

    @JsonProperty("ref")
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

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.ref == null)? 0 :this.ref.hashCode()));
        result = ((result* 31)+((this.units == null)? 0 :this.units.hashCode()));
        result = ((result* 31)+((this.set_value == null)? 0 :this.set_value.hashCode()));
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
        return ((((this.ref == rhs.ref)||((this.ref!= null)&&this.ref.equals(rhs.ref)))&&((this.units == rhs.units)||((this.units!= null)&&this.units.equals(rhs.units))))&&((this.set_value == rhs.set_value)||((this.set_value!= null)&&this.set_value.equals(rhs.set_value))));
    }

}
