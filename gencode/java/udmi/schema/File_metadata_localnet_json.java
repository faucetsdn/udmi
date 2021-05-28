
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Local network metadata snippet
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "subsystem"
})
@Generated("jsonschema2pojo")
public class File_metadata_localnet_json {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("subsystem")
    public Subsystem__2 subsystem;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.subsystem == null)? 0 :this.subsystem.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof File_metadata_localnet_json) == false) {
            return false;
        }
        File_metadata_localnet_json rhs = ((File_metadata_localnet_json) other);
        return ((this.subsystem == rhs.subsystem)||((this.subsystem!= null)&&this.subsystem.equals(rhs.subsystem)));
    }

}
