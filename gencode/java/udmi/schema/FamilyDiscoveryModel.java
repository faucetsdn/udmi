
package udmi.schema;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Family Discovery Model
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "generation",
    "scan_interval_sec",
    "scan_duration_sec"
})
public class FamilyDiscoveryModel {

    /**
     * Generational marker for controlling discovery
     * 
     */
    @JsonProperty("generation")
    @JsonPropertyDescription("Generational marker for controlling discovery")
    public Date generation;
    /**
     * Period, in seconds, for automatic scanning
     * 
     */
    @JsonProperty("scan_interval_sec")
    @JsonPropertyDescription("Period, in seconds, for automatic scanning")
    public Integer scan_interval_sec;
    /**
     * Scan duration, in seconds
     * 
     */
    @JsonProperty("scan_duration_sec")
    @JsonPropertyDescription("Scan duration, in seconds")
    public Integer scan_duration_sec;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.generation == null)? 0 :this.generation.hashCode()));
        result = ((result* 31)+((this.scan_interval_sec == null)? 0 :this.scan_interval_sec.hashCode()));
        result = ((result* 31)+((this.scan_duration_sec == null)? 0 :this.scan_duration_sec.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof FamilyDiscoveryModel) == false) {
            return false;
        }
        FamilyDiscoveryModel rhs = ((FamilyDiscoveryModel) other);
        return ((((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.scan_interval_sec == rhs.scan_interval_sec)||((this.scan_interval_sec!= null)&&this.scan_interval_sec.equals(rhs.scan_interval_sec))))&&((this.scan_duration_sec == rhs.scan_duration_sec)||((this.scan_duration_sec!= null)&&this.scan_duration_sec.equals(rhs.scan_duration_sec))));
    }

}
