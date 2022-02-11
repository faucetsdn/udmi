
package udmi.schema;

import java.util.HashMap;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Pointset State
 * <p>
 * A set of points reporting telemetry data.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "state_etag",
    "points"
})
@Generated("jsonschema2pojo")
public class PointsetState {

    /**
     * An identifier which uniquely represents the state, and used by a device avoid race conditions where the incoming config is based off an obsolete state. [Additional information on implementation](../docs/specs/sequences/writeback.md)
     * 
     */
    @JsonProperty("state_etag")
    @JsonPropertyDescription("An identifier which uniquely represents the state, and used by a device avoid race conditions where the incoming config is based off an obsolete state. [Additional information on implementation](../docs/specs/sequences/writeback.md)")
    public java.lang.String state_etag;
    /**
     * Collection of point names, defining the representative point set for this device.
     * (Required)
     * 
     */
    @JsonProperty("points")
    @JsonPropertyDescription("Collection of point names, defining the representative point set for this device.")
    public HashMap<String, PointPointsetState> points;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.state_etag == null)? 0 :this.state_etag.hashCode()));
        result = ((result* 31)+((this.points == null)? 0 :this.points.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PointsetState) == false) {
            return false;
        }
        PointsetState rhs = ((PointsetState) other);
        return (((this.state_etag == rhs.state_etag)||((this.state_etag!= null)&&this.state_etag.equals(rhs.state_etag)))&&((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points))));
    }

}
