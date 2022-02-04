
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * System Metadata
 * <p>
 * High-level system information about the device. [System Metadata Documentation](../docs/messages/system.md)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "location",
    "physical_tag",
    "aux"
})
@Generated("jsonschema2pojo")
public class SystemMetadata__1 {

    /**
     * Properties the expected physical location of the device.
     * (Required)
     * 
     */
    @JsonProperty("location")
    @JsonPropertyDescription("Properties the expected physical location of the device.")
    public Location__1 location;
    /**
     * Information used to print a physical QR code label.
     * (Required)
     * 
     */
    @JsonProperty("physical_tag")
    @JsonPropertyDescription("Information used to print a physical QR code label.")
    public Physical_tag__1 physical_tag;
    @JsonProperty("aux")
    public Aux__1 aux;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.location == null)? 0 :this.location.hashCode()));
        result = ((result* 31)+((this.physical_tag == null)? 0 :this.physical_tag.hashCode()));
        result = ((result* 31)+((this.aux == null)? 0 :this.aux.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SystemMetadata__1) == false) {
            return false;
        }
        SystemMetadata__1 rhs = ((SystemMetadata__1) other);
        return ((((this.location == rhs.location)||((this.location!= null)&&this.location.equals(rhs.location)))&&((this.physical_tag == rhs.physical_tag)||((this.physical_tag!= null)&&this.physical_tag.equals(rhs.physical_tag))))&&((this.aux == rhs.aux)||((this.aux!= null)&&this.aux.equals(rhs.aux))));
    }

}
