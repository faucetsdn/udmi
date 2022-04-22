
package udmi.schema;

import java.util.HashMap;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Pointset Model
 * <p>
 * Pointset representing the abstract system expectation for what the device should be doing, and how it should be configured and operated. This block specifies the expected points that a device holds
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "points"
})
@Generated("jsonschema2pojo")
public class PointsetModel {

    /**
     * Information about a specific point name of the device.
     * (Required)
     * 
     */
    @JsonProperty("points")
    @JsonPropertyDescription("Information about a specific point name of the device.")
    public HashMap<String, PointPointsetModel> points;

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
        if ((other instanceof PointsetModel) == false) {
            return false;
        }
        PointsetModel rhs = ((PointsetModel) other);
        return ((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points)));
    }

}
