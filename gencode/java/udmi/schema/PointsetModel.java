
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
    "points",
    "sample_limit_sec",
    "sample_rate_sec"
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
    public HashMap<String, PointPointsetMetadata> points;
    /**
     * Minimum time between sample updates for the device (including complete and COV updates). Updates more frequent than this should be coalesced into one update.
     * 
     */
    @JsonProperty("sample_limit_sec")
    @JsonPropertyDescription("Minimum time between sample updates for the device (including complete and COV updates). Updates more frequent than this should be coalesced into one update.")
    public Integer sample_limit_sec;
    /**
     * Maximum time between samples for the device to send out a complete update. It can send out updates more frequently than this. Default to 600.
     * 
     */
    @JsonProperty("sample_rate_sec")
    @JsonPropertyDescription("Maximum time between samples for the device to send out a complete update. It can send out updates more frequently than this. Default to 600.")
    public Integer sample_rate_sec;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.sample_rate_sec == null)? 0 :this.sample_rate_sec.hashCode()));
        result = ((result* 31)+((this.sample_limit_sec == null)? 0 :this.sample_limit_sec.hashCode()));
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
        PointsetMetadata rhs = ((PointsetMetadata) other);
        return ((((this.sample_rate_sec == rhs.sample_rate_sec)||((this.sample_rate_sec!= null)&&this.sample_rate_sec.equals(rhs.sample_rate_sec)))&&((this.sample_limit_sec == rhs.sample_limit_sec)||((this.sample_limit_sec!= null)&&this.sample_limit_sec.equals(rhs.sample_limit_sec))))&&((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points))));
    }

}
