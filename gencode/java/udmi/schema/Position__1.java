
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "x",
    "y"
})
@Generated("jsonschema2pojo")
public class Position__1 {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("x")
    public Double x;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("y")
    public Double y;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.x == null)? 0 :this.x.hashCode()));
        result = ((result* 31)+((this.y == null)? 0 :this.y.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Position__1) == false) {
            return false;
        }
        Position__1 rhs = ((Position__1) other);
        return (((this.x == rhs.x)||((this.x!= null)&&this.x.equals(rhs.x)))&&((this.y == rhs.y)||((this.y!= null)&&this.y.equals(rhs.y))));
    }

}
