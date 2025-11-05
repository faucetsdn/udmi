
package udmi.schema;

import java.util.Map;
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
    "adjunct",
    "shadow_id",
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
    /**
     * The network designator for this device in the family-defined format
     * 
     */
    @JsonProperty("network")
    @JsonPropertyDescription("The network designator for this device in the family-defined format")
    public java.lang.String network;
    @JsonProperty("adjunct")
    public Map<String, String> adjunct;
    /**
     * Specifies that this is a shadow of the indicated device
     * 
     */
    @JsonProperty("shadow_id")
    @JsonPropertyDescription("Specifies that this is a shadow of the indicated device")
    public java.lang.String shadow_id;
    /**
     * The device id of the node's parent
     * 
     */
    @JsonProperty("parent_id")
    @JsonPropertyDescription("The device id of the node's parent")
    public java.lang.String parent_id;
    /**
     * The family designator, used only when the entry is not keyed in a family map
     * 
     */
    @JsonProperty("family")
    @JsonPropertyDescription("The family designator, used only when the entry is not keyed in a family map")
    public java.lang.String family;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.addr == null)? 0 :this.addr.hashCode()));
        result = ((result* 31)+((this.adjunct == null)? 0 :this.adjunct.hashCode()));
        result = ((result* 31)+((this.family == null)? 0 :this.family.hashCode()));
        result = ((result* 31)+((this.shadow_id == null)? 0 :this.shadow_id.hashCode()));
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
        return (((((((this.addr == rhs.addr)||((this.addr!= null)&&this.addr.equals(rhs.addr)))&&((this.adjunct == rhs.adjunct)||((this.adjunct!= null)&&this.adjunct.equals(rhs.adjunct))))&&((this.family == rhs.family)||((this.family!= null)&&this.family.equals(rhs.family))))&&((this.shadow_id == rhs.shadow_id)||((this.shadow_id!= null)&&this.shadow_id.equals(rhs.shadow_id))))&&((this.parent_id == rhs.parent_id)||((this.parent_id!= null)&&this.parent_id.equals(rhs.parent_id))))&&((this.network == rhs.network)||((this.network!= null)&&this.network.equals(rhs.network))));
    }

}
