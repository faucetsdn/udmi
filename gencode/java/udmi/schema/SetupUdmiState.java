
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
    "udmi_version",
    "udmi_commit",
    "udmi_ref",
    "udmi_timever",
    "msg_source",
    "update_to",
    "tool_name",
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
     * Semantic tagged version of udmis install
     * 
     */
    @JsonProperty("udmi_version")
    @JsonPropertyDescription("Semantic tagged version of udmis install")
    public String udmi_version;
    /**
     * Commit hash of this udmis install
     * 
     */
    @JsonProperty("udmi_commit")
    @JsonPropertyDescription("Commit hash of this udmis install")
    public String udmi_commit;
    /**
     * Complete reference of udmis install
     * 
     */
    @JsonProperty("udmi_ref")
    @JsonPropertyDescription("Complete reference of udmis install")
    public String udmi_ref;
    /**
     * Timestamp version id of udmis install
     * 
     */
    @JsonProperty("udmi_timever")
    @JsonPropertyDescription("Timestamp version id of udmis install")
    public String udmi_timever;
    /**
     * Source parameter to use for this connection stream
     * 
     */
    @JsonProperty("msg_source")
    @JsonPropertyDescription("Source parameter to use for this connection stream")
    public String msg_source;
    /**
     * Optional version for a udmis update trigger
     * 
     */
    @JsonProperty("update_to")
    @JsonPropertyDescription("Optional version for a udmis update trigger")
    public String update_to;
    /**
     * Name of the tool being used
     * 
     */
    @JsonProperty("tool_name")
    @JsonPropertyDescription("Name of the tool being used")
    public String tool_name;
    @JsonProperty("transaction_id")
    public String transaction_id;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.update_to == null)? 0 :this.update_to.hashCode()));
        result = ((result* 31)+((this.tool_name == null)? 0 :this.tool_name.hashCode()));
        result = ((result* 31)+((this.transaction_id == null)? 0 :this.transaction_id.hashCode()));
        result = ((result* 31)+((this.udmi_ref == null)? 0 :this.udmi_ref.hashCode()));
        result = ((result* 31)+((this.udmi_version == null)? 0 :this.udmi_version.hashCode()));
        result = ((result* 31)+((this.msg_source == null)? 0 :this.msg_source.hashCode()));
        result = ((result* 31)+((this.udmi_commit == null)? 0 :this.udmi_commit.hashCode()));
        result = ((result* 31)+((this.user == null)? 0 :this.user.hashCode()));
        result = ((result* 31)+((this.udmi_timever == null)? 0 :this.udmi_timever.hashCode()));
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
        return ((((((((((this.update_to == rhs.update_to)||((this.update_to!= null)&&this.update_to.equals(rhs.update_to)))&&((this.tool_name == rhs.tool_name)||((this.tool_name!= null)&&this.tool_name.equals(rhs.tool_name))))&&((this.transaction_id == rhs.transaction_id)||((this.transaction_id!= null)&&this.transaction_id.equals(rhs.transaction_id))))&&((this.udmi_ref == rhs.udmi_ref)||((this.udmi_ref!= null)&&this.udmi_ref.equals(rhs.udmi_ref))))&&((this.udmi_version == rhs.udmi_version)||((this.udmi_version!= null)&&this.udmi_version.equals(rhs.udmi_version))))&&((this.msg_source == rhs.msg_source)||((this.msg_source!= null)&&this.msg_source.equals(rhs.msg_source))))&&((this.udmi_commit == rhs.udmi_commit)||((this.udmi_commit!= null)&&this.udmi_commit.equals(rhs.udmi_commit))))&&((this.user == rhs.user)||((this.user!= null)&&this.user.equals(rhs.user))))&&((this.udmi_timever == rhs.udmi_timever)||((this.udmi_timever!= null)&&this.udmi_timever.equals(rhs.udmi_timever))));
    }

}
