
package udmi.schema;

import java.util.HashMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Points Template Model
 * <p>
 * Points template representing a collection of points which can be reused by multiple devices
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "points"
})
public class PointsTemplateModel {

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
        if ((other instanceof PointsTemplateModel) == false) {
            return false;
        }
        PointsTemplateModel rhs = ((PointsTemplateModel) other);
        return ((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points)));
    }

}
