
package udmi.schema;

import java.util.HashMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Family Localnet Model
 * <p>
 * The type of network
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "addr",
    "network",
    "parent_id",
    "family"
})
public class FamilyLocalnetModel {

    /**
     * The address of a device on the fieldbus/local network
     * 
     */
    @JsonProperty("addr")
    @JsonPropertyDescription("The address of a device on the fieldbus/local network")
    public java.lang.String addr;
    @JsonProperty("network")
    public HashMap<String, String> network;
    /**
     * The device id of the network parent
     * 
     */
    @JsonProperty("parent_id")
    @JsonPropertyDescription("The device id of the network parent")
    public java.lang.String parent_id;
    @JsonProperty("family")
    public java.lang.String family;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.addr == null)? 0 :this.addr.hashCode()));
        result = ((result* 31)+((this.family == null)? 0 :this.family.hashCode()));
        result = ((result* 31)+((this.parent_id == null)? 0 :this.parent_id.hashCode()));
        result = ((result* 31)+((this.network == null)? 0 :this.network.hashCode()));
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
        return (((((this.addr == rhs.addr)||((this.addr!= null)&&this.addr.equals(rhs.addr)))&&((this.family == rhs.family)||((this.family!= null)&&this.family.equals(rhs.family))))&&((this.parent_id == rhs.parent_id)||((this.parent_id!= null)&&this.parent_id.equals(rhs.parent_id))))&&((this.network == rhs.network)||((this.network!= null)&&this.network.equals(rhs.network))));
    }

}
