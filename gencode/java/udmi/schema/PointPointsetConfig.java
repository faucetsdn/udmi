
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Point Pointset Config
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "ref",
    "units",
    "set_value",
    "min_loglevel",
    "sample_limit_sec",
    "sample_rate_sec"
})
@Generated("jsonschema2pojo")
public class PointPointsetConfig {

    /**
     * Mapping for the point to an internal resource (e.g. BACnet object reference)
     * 
     */
    @JsonProperty("ref")
    @JsonPropertyDescription("Mapping for the point to an internal resource (e.g. BACnet object reference)")
    public String ref;
    /**
     * If specified, indicates the units the device should report the data in.
     * 
     */
    @JsonProperty("units")
    @JsonPropertyDescription("If specified, indicates the units the device should report the data in.")
    public String units;
    /**
     * Used for cloud writeback functionality, this field specifies the value for a given point in the device's current units.
     * 
     */
    @JsonProperty("set_value")
    @JsonPropertyDescription("Used for cloud writeback functionality, this field specifies the value for a given point in the device's current units.")
    public Object set_value;
    /**
     * The minimum loglevel for reporting log messages below which log entries should not be sent. Default to 300.
     * 
     */
    @JsonProperty("min_loglevel")
    @JsonPropertyDescription("The minimum loglevel for reporting log messages below which log entries should not be sent. Default to 300.")
    public Integer min_loglevel;
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
        result = ((result* 31)+((this.ref == null)? 0 :this.ref.hashCode()));
        result = ((result* 31)+((this.set_value == null)? 0 :this.set_value.hashCode()));
        result = ((result* 31)+((this.min_loglevel == null)? 0 :this.min_loglevel.hashCode()));
        result = ((result* 31)+((this.units == null)? 0 :this.units.hashCode()));
        result = ((result* 31)+((this.sample_limit_sec == null)? 0 :this.sample_limit_sec.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PointPointsetConfig) == false) {
            return false;
        }
        PointPointsetConfig rhs = ((PointPointsetConfig) other);
        return (((((((this.sample_rate_sec == rhs.sample_rate_sec)||((this.sample_rate_sec!= null)&&this.sample_rate_sec.equals(rhs.sample_rate_sec)))&&((this.ref == rhs.ref)||((this.ref!= null)&&this.ref.equals(rhs.ref))))&&((this.set_value == rhs.set_value)||((this.set_value!= null)&&this.set_value.equals(rhs.set_value))))&&((this.min_loglevel == rhs.min_loglevel)||((this.min_loglevel!= null)&&this.min_loglevel.equals(rhs.min_loglevel))))&&((this.units == rhs.units)||((this.units!= null)&&this.units.equals(rhs.units))))&&((this.sample_limit_sec == rhs.sample_limit_sec)||((this.sample_limit_sec!= null)&&this.sample_limit_sec.equals(rhs.sample_limit_sec))));
    }

}
