
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Point Pointset Event
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "present_value"
})
@Generated("jsonschema2pojo")
public class PointPointsetEvent__1 {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("present_value")
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
        if ((other instanceof PointPointsetEvent__1) == false) {
            return false;
        }
        PointPointsetEvent__1 rhs = ((PointPointsetEvent__1) other);
        return ((this.present_value == rhs.present_value)||((this.present_value!= null)&&this.present_value.equals(rhs.present_value)));
    }

}
