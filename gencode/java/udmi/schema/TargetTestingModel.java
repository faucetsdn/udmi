
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Target Testing Model
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "target_point",
    "target_value"
})
public class TargetTestingModel {

    /**
     * Point name used for testing
     * 
     */
    @JsonProperty("target_point")
    @JsonPropertyDescription("Point name used for testing")
    public String target_point;
    /**
     * Value used for testing
     * 
     */
    @JsonProperty("target_value")
    @JsonPropertyDescription("Value used for testing")
    public Object target_value;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.target_point == null)? 0 :this.target_point.hashCode()));
        result = ((result* 31)+((this.target_value == null)? 0 :this.target_value.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof TargetTestingModel) == false) {
            return false;
        }
        TargetTestingModel rhs = ((TargetTestingModel) other);
        return (((this.target_point == rhs.target_point)||((this.target_point!= null)&&this.target_point.equals(rhs.target_point)))&&((this.target_value == rhs.target_value)||((this.target_value!= null)&&this.target_value.equals(rhs.target_value))));
    }

}
