
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Point Pointset Events
 * <p>
 * Object representation for for a single point
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "present_value"
})
public class PointPointsetEvents {

    /**
     * The specific point data reading
     * (Required)
     * 
     */
    @JsonProperty("present_value")
    @JsonPropertyDescription("The specific point data reading")
    public Object present_value;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.present_value == null)? 0 :this.present_value.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PointPointsetEvents) == false) {
            return false;
        }
        PointPointsetEvents rhs = ((PointPointsetEvents) other);
        return ((this.present_value == rhs.present_value)||((this.present_value!= null)&&this.present_value.equals(rhs.present_value)));
    }

}
