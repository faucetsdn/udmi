
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Information about the physical device firmware
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "version"
})
@Generated("jsonschema2pojo")
public class Firmware {

    /**
     * Version of the local firmware
     * (Required)
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the local firmware")
    public String version;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Firmware) == false) {
            return false;
        }
        Firmware rhs = ((Firmware) other);
        return ((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version)));
    }

}
