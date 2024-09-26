
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Setup Udmi State
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "user",
    "update_to",
    "transaction_id"
})
public class SetupUdmiState {

    /**
     * User id of the person running the tool
     * 
     */
    @JsonProperty("user")
    @JsonPropertyDescription("User id of the person running the tool")
    public String user;
    /**
     * Optional version for a udmis update trigger
     * 
     */
    @JsonProperty("update_to")
    @JsonPropertyDescription("Optional version for a udmis update trigger")
    public String update_to;
    @JsonProperty("transaction_id")
    public String transaction_id;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.update_to == null)? 0 :this.update_to.hashCode()));
        result = ((result* 31)+((this.transaction_id == null)? 0 :this.transaction_id.hashCode()));
        result = ((result* 31)+((this.user == null)? 0 :this.user.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SetupUdmiState) == false) {
            return false;
        }
        SetupUdmiState rhs = ((SetupUdmiState) other);
        return ((((this.update_to == rhs.update_to)||((this.update_to!= null)&&this.update_to.equals(rhs.update_to)))&&((this.transaction_id == rhs.transaction_id)||((this.transaction_id!= null)&&this.transaction_id.equals(rhs.transaction_id))))&&((this.user == rhs.user)||((this.user!= null)&&this.user.equals(rhs.user))));
    }

}
