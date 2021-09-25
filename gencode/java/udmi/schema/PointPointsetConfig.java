
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
    "set_value"
})
@Generated("jsonschema2pojo")
public class PointPointsetConfig {

    @JsonProperty("ref")
    public String ref;
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
        result = ((result* 31)+((this.set_value == null)? 0 :this.set_value.hashCode()));
        result = ((result* 31)+((this.ref == null)? 0 :this.ref.hashCode()));
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
        return (((this.set_value == rhs.set_value)||((this.set_value!= null)&&this.set_value.equals(rhs.set_value)))&&((this.ref == rhs.ref)||((this.ref!= null)&&this.ref.equals(rhs.ref))));
    }

}
