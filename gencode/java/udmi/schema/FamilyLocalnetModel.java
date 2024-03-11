
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import udmi.schema.Common.ProtocolFamily;


/**
 * Family Localnet Model
 * <p>
 * The type of network
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "addr",
    "family"
})
@Generated("jsonschema2pojo")
public class FamilyLocalnetModel {

    /**
     * The address of a device on the fieldbus/local network
     * 
     */
    @JsonProperty("addr")
    @JsonPropertyDescription("The address of a device on the fieldbus/local network")
    public String addr;
    @JsonProperty("family")
    public ProtocolFamily family;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.addr == null)? 0 :this.addr.hashCode()));
        result = ((result* 31)+((this.family == null)? 0 :this.family.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof FamilyLocalnetModel) == false) {
            return false;
        }
        FamilyLocalnetModel rhs = ((FamilyLocalnetModel) other);
        return (((this.addr == rhs.addr)||((this.addr!= null)&&this.addr.equals(rhs.addr)))&&((this.family == rhs.family)||((this.family!= null)&&this.family.equals(rhs.family))));
    }

}
