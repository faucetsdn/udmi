
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Pointset Metadata
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "points"
})
@Generated("jsonschema2pojo")
public class PointsetMetadata__1 {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("points")
    public Object points;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.points == null)? 0 :this.points.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PointsetMetadata__1) == false) {
            return false;
        }
        PointsetMetadata__1 rhs = ((PointsetMetadata__1) other);
        return ((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points)));
    }

}
