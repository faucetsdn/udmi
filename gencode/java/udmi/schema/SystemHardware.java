
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * SystemHardware
 * <p>
 * A collection of fields which describe the physical hardware of the device.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "make",
    "model",
    "sku",
    "rev"
})
public class SystemHardware {

    /**
     * The manufacturer of the device
     * (Required)
     * 
     */
    @JsonProperty("make")
    @JsonPropertyDescription("The manufacturer of the device")
    public String make;
    /**
     * The model of the device
     * (Required)
     * 
     */
    @JsonProperty("model")
    @JsonPropertyDescription("The model of the device")
    public String model;
    /**
     * A stock keeping unit which identifies the unique composition of a device, for example those with different hardware configurations
     * 
     */
    @JsonProperty("sku")
    @JsonPropertyDescription("A stock keeping unit which identifies the unique composition of a device, for example those with different hardware configurations")
    public String sku;
    /**
     * Hardware revision of the device
     * 
     */
    @JsonProperty("rev")
    @JsonPropertyDescription("Hardware revision of the device")
    public String rev;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.model == null)? 0 :this.model.hashCode()));
        result = ((result* 31)+((this.rev == null)? 0 :this.rev.hashCode()));
        result = ((result* 31)+((this.sku == null)? 0 :this.sku.hashCode()));
        result = ((result* 31)+((this.make == null)? 0 :this.make.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SystemHardware) == false) {
            return false;
        }
        SystemHardware rhs = ((SystemHardware) other);
        return (((((this.model == rhs.model)||((this.model!= null)&&this.model.equals(rhs.model)))&&((this.rev == rhs.rev)||((this.rev!= null)&&this.rev.equals(rhs.rev))))&&((this.sku == rhs.sku)||((this.sku!= null)&&this.sku.equals(rhs.sku))))&&((this.make == rhs.make)||((this.make!= null)&&this.make.equals(rhs.make))));
    }

}
