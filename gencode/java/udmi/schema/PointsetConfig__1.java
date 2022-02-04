
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Pointset Config
 * <p>
 * [Pointset Config Documentation](../docs/messages/pointset.md#config)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "state_etag",
    "set_value_expiry",
    "sample_limit_sec",
    "sample_rate_sec",
    "points"
})
@Generated("jsonschema2pojo")
public class PointsetConfig__1 {

    /**
     * The `state_etag` of the last _state_ message sent by the device. [Writeback documentation](../docs/specs/sequences/writeback.md)
     * 
     */
    @JsonProperty("state_etag")
    @JsonPropertyDescription("The `state_etag` of the last _state_ message sent by the device. [Writeback documentation](../docs/specs/sequences/writeback.md)")
    public java.lang.String state_etag;
    /**
     * An expiry for the the device reverts to its operational state, and sends a state update to notify the cloud of the state change. [Writeback documentation](../docs/specs/sequences/writeback.md)
     * 
     */
    @JsonProperty("set_value_expiry")
    @JsonPropertyDescription("An expiry for the the device reverts to its operational state, and sends a state update to notify the cloud of the state change. [Writeback documentation](../docs/specs/sequences/writeback.md)")
    public Date set_value_expiry;
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
    /**
     * The points defined in this dictionary is the authoritative source indicating the representative points for the device (in both `telemetry` and `state` messages). [Pointset doumentation](../docs/messages/pointset.md)
     * 
     */
    @JsonProperty("points")
    @JsonPropertyDescription("The points defined in this dictionary is the authoritative source indicating the representative points for the device (in both `telemetry` and `state` messages). [Pointset doumentation](../docs/messages/pointset.md)")
    public HashMap<String, PointPointsetConfig> points;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.sample_rate_sec == null)? 0 :this.sample_rate_sec.hashCode()));
        result = ((result* 31)+((this.state_etag == null)? 0 :this.state_etag.hashCode()));
        result = ((result* 31)+((this.set_value_expiry == null)? 0 :this.set_value_expiry.hashCode()));
        result = ((result* 31)+((this.sample_limit_sec == null)? 0 :this.sample_limit_sec.hashCode()));
        result = ((result* 31)+((this.points == null)? 0 :this.points.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PointsetConfig__1) == false) {
            return false;
        }
        PointsetConfig__1 rhs = ((PointsetConfig__1) other);
        return ((((((this.sample_rate_sec == rhs.sample_rate_sec)||((this.sample_rate_sec!= null)&&this.sample_rate_sec.equals(rhs.sample_rate_sec)))&&((this.state_etag == rhs.state_etag)||((this.state_etag!= null)&&this.state_etag.equals(rhs.state_etag))))&&((this.set_value_expiry == rhs.set_value_expiry)||((this.set_value_expiry!= null)&&this.set_value_expiry.equals(rhs.set_value_expiry))))&&((this.sample_limit_sec == rhs.sample_limit_sec)||((this.sample_limit_sec!= null)&&this.sample_limit_sec.equals(rhs.sample_limit_sec))))&&((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points))));
    }

}
