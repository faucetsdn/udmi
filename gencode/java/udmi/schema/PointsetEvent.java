
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Pointset Event
 * <p>
 * A set of points reporting telemetry data
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "partial_update",
    "points"
})
@Generated("jsonschema2pojo")
public class PointsetEvent {

    /**
     * RFC 3339 timestamp the telemetry event was generated
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 timestamp the telemetry event was generated")
    public Date timestamp;
    /**
     * Major version of the UDMI schema
     * (Required)
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Major version of the UDMI schema")
    public Integer version;
    /**
     * Indicates if this is a partial update (only some points may be included)
     * 
     */
    @JsonProperty("partial_update")
    @JsonPropertyDescription("Indicates if this is a partial update (only some points may be included)")
    public Boolean partial_update;
    /**
     * Collection of point names, defining the representative point set for this device.
     * (Required)
     * 
     */
    @JsonProperty("points")
    @JsonPropertyDescription("Collection of point names, defining the representative point set for this device.")
    public HashMap<String, PointPointsetEvent> points;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.partial_update == null)? 0 :this.partial_update.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
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
        return (((((this.partial_update == rhs.partial_update)||((this.partial_update!= null)&&this.partial_update.equals(rhs.partial_update)))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points))));
    }

}
