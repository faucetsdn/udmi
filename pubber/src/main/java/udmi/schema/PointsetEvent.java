
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Pointset Event
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "config_etag",
    "points"
})
@Generated("jsonschema2pojo")
public class PointsetEvent {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    public Date timestamp;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    public Integer version;
    @JsonProperty("config_etag")
    public java.lang.String config_etag;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("points")
    public HashMap<String, PointPointsetEvent> points;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.config_etag == null)? 0 :this.config_etag.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        result = ((result* 31)+((this.points == null)? 0 :this.points.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PointsetEvent) == false) {
            return false;
        }
        PointsetEvent rhs = ((PointsetEvent) other);
        return (((((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version)))&&((this.config_etag == rhs.config_etag)||((this.config_etag!= null)&&this.config_etag.equals(rhs.config_etag))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points))));
    }

}
