
package udmi.schema;

import java.util.HashMap;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Pointset State
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "config_etag",
    "state_etag",
    "points"
})
@Generated("jsonschema2pojo")
public class PointsetState {

    @JsonProperty("config_etag")
    public java.lang.String config_etag;
    @JsonProperty("state_etag")
    public java.lang.String state_etag;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("points")
    public HashMap<String, PointPointsetState> points;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.state_etag == null)? 0 :this.state_etag.hashCode()));
        result = ((result* 31)+((this.config_etag == null)? 0 :this.config_etag.hashCode()));
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
        return ((((this.state_etag == rhs.state_etag)||((this.state_etag!= null)&&this.state_etag.equals(rhs.state_etag)))&&((this.config_etag == rhs.config_etag)||((this.config_etag!= null)&&this.config_etag.equals(rhs.config_etag))))&&((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points))));
    }

}
