
package udmi.schema;

import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * System Model
 * <p>
 * High-level system information about the device. [System Model Documentation](../docs/messages/system.md)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "location",
    "hardware",
    "software",
    "physical_tag",
    "aux",
    "min_loglevel"
})
@Generated("jsonschema2pojo")
public class SystemModel {

    /**
     * Properties the expected physical location of the device.
     * (Required)
     * 
     */
    @JsonProperty("location")
    @JsonPropertyDescription("Properties the expected physical location of the device.")
    public Location location;
    /**
     * SystemHardware
     * <p>
     * A collection of fields which describe the physical hardware of the device.
     * 
     */
    @JsonProperty("hardware")
    @JsonPropertyDescription("A collection of fields which describe the physical hardware of the device.")
    public SystemHardware hardware;
    /**
     * A collection of items which can be used to describe version of software running on a device
     * 
     */
    @JsonProperty("software")
    @JsonPropertyDescription("A collection of items which can be used to describe version of software running on a device")
    public Map<String, String> software;
    /**
     * Information used to print a physical QR code label.
     * (Required)
     * 
     */
    @JsonProperty("physical_tag")
    @JsonPropertyDescription("Information used to print a physical QR code label.")
    public Physical_tag physical_tag;
    @JsonProperty("aux")
    public Aux aux;
    /**
     * The minimum loglevel for reporting log messages below which log entries should not be sent. Default to 300.
     * 
     */
    @JsonProperty("min_loglevel")
    @JsonPropertyDescription("The minimum loglevel for reporting log messages below which log entries should not be sent. Default to 300.")
    public Integer min_loglevel = 300;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.software == null)? 0 :this.software.hashCode()));
        result = ((result* 31)+((this.aux == null)? 0 :this.aux.hashCode()));
        result = ((result* 31)+((this.min_loglevel == null)? 0 :this.min_loglevel.hashCode()));
        result = ((result* 31)+((this.location == null)? 0 :this.location.hashCode()));
        result = ((result* 31)+((this.physical_tag == null)? 0 :this.physical_tag.hashCode()));
        result = ((result* 31)+((this.hardware == null)? 0 :this.hardware.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SystemModel) == false) {
            return false;
        }
        SystemModel rhs = ((SystemModel) other);
        return (((((((this.software == rhs.software)||((this.software!= null)&&this.software.equals(rhs.software)))&&((this.aux == rhs.aux)||((this.aux!= null)&&this.aux.equals(rhs.aux))))&&((this.min_loglevel == rhs.min_loglevel)||((this.min_loglevel!= null)&&this.min_loglevel.equals(rhs.min_loglevel))))&&((this.location == rhs.location)||((this.location!= null)&&this.location.equals(rhs.location))))&&((this.physical_tag == rhs.physical_tag)||((this.physical_tag!= null)&&this.physical_tag.equals(rhs.physical_tag))))&&((this.hardware == rhs.hardware)||((this.hardware!= null)&&this.hardware.equals(rhs.hardware))));
    }

}
