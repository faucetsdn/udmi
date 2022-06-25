
package udmi.schema;

import java.util.HashMap;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * System Discovery Event
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "serial_no",
    "ancillary",
    "hardware"
})
@Generated("jsonschema2pojo")
public class SystemDiscoveryEvent {

    /**
     * The serial number of the physical device
     * 
     */
    @JsonProperty("serial_no")
    @JsonPropertyDescription("The serial number of the physical device")
    public java.lang.String serial_no;
    /**
     * Ancillary Properties
     * <p>
     * Arbitrary blob of json associated with this point
     * 
     */
    @JsonProperty("ancillary")
    @JsonPropertyDescription("Arbitrary blob of json associated with this point")
    public HashMap<String, Object> ancillary;
    /**
     * SystemHardware
     * <p>
     * A collection of fields which describe the physical hardware of the device.
     * 
     */
    @JsonProperty("hardware")
    @JsonPropertyDescription("A collection of fields which describe the physical hardware of the device.")
    public SystemHardware hardware;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.ancillary == null)? 0 :this.ancillary.hashCode()));
        result = ((result* 31)+((this.serial_no == null)? 0 :this.serial_no.hashCode()));
        result = ((result* 31)+((this.hardware == null)? 0 :this.hardware.hashCode()));
        return result;
    }

    @Override
    public boolean equals(java.lang.Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SystemDiscoveryEvent) == false) {
            return false;
        }
        SystemDiscoveryEvent rhs = ((SystemDiscoveryEvent) other);
        return ((((this.ancillary == rhs.ancillary)||((this.ancillary!= null)&&this.ancillary.equals(rhs.ancillary)))&&((this.serial_no == rhs.serial_no)||((this.serial_no!= null)&&this.serial_no.equals(rhs.serial_no))))&&((this.hardware == rhs.hardware)||((this.hardware!= null)&&this.hardware.equals(rhs.hardware))));
    }

}
