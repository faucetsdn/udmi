
package udmi.schema;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Setup Udmi Config
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "hostname",
    "functions_min",
    "functions_max",
    "udmi_version",
    "udmi_ref",
    "udmi_timever",
    "built_at",
    "built_by",
    "deployed_at",
    "deployed_by"
})
public class SetupUdmiConfig {

    @JsonProperty("hostname")
    public String hostname;
    @JsonProperty("functions_min")
    public Integer functions_min;
    @JsonProperty("functions_max")
    public Integer functions_max;
    /**
     * Semantic tagged version of udmis install
     * 
     */
    @JsonProperty("udmi_version")
    @JsonPropertyDescription("Semantic tagged version of udmis install")
    public String udmi_version;
    /**
     * Exact reference hash of udmis install
     * 
     */
    @JsonProperty("udmi_ref")
    @JsonPropertyDescription("Exact reference hash of udmis install")
    public String udmi_ref;
    /**
     * Timestamp version id of udmis install
     * 
     */
    @JsonProperty("udmi_timever")
    @JsonPropertyDescription("Timestamp version id of udmis install")
    public String udmi_timever;
    @JsonProperty("built_at")
    public Date built_at;
    @JsonProperty("built_by")
    public String built_by;
    @JsonProperty("deployed_at")
    public Date deployed_at;
    @JsonProperty("deployed_by")
    public String deployed_by;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.hostname == null)? 0 :this.hostname.hashCode()));
        result = ((result* 31)+((this.udmi_ref == null)? 0 :this.udmi_ref.hashCode()));
        result = ((result* 31)+((this.udmi_version == null)? 0 :this.udmi_version.hashCode()));
        result = ((result* 31)+((this.built_at == null)? 0 :this.built_at.hashCode()));
        result = ((result* 31)+((this.functions_min == null)? 0 :this.functions_min.hashCode()));
        result = ((result* 31)+((this.deployed_at == null)? 0 :this.deployed_at.hashCode()));
        result = ((result* 31)+((this.functions_max == null)? 0 :this.functions_max.hashCode()));
        result = ((result* 31)+((this.built_by == null)? 0 :this.built_by.hashCode()));
        result = ((result* 31)+((this.deployed_by == null)? 0 :this.deployed_by.hashCode()));
        result = ((result* 31)+((this.udmi_timever == null)? 0 :this.udmi_timever.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SetupUdmiConfig) == false) {
            return false;
        }
        SetupUdmiConfig rhs = ((SetupUdmiConfig) other);
        return (((((((((((this.hostname == rhs.hostname)||((this.hostname!= null)&&this.hostname.equals(rhs.hostname)))&&((this.udmi_ref == rhs.udmi_ref)||((this.udmi_ref!= null)&&this.udmi_ref.equals(rhs.udmi_ref))))&&((this.udmi_version == rhs.udmi_version)||((this.udmi_version!= null)&&this.udmi_version.equals(rhs.udmi_version))))&&((this.built_at == rhs.built_at)||((this.built_at!= null)&&this.built_at.equals(rhs.built_at))))&&((this.functions_min == rhs.functions_min)||((this.functions_min!= null)&&this.functions_min.equals(rhs.functions_min))))&&((this.deployed_at == rhs.deployed_at)||((this.deployed_at!= null)&&this.deployed_at.equals(rhs.deployed_at))))&&((this.functions_max == rhs.functions_max)||((this.functions_max!= null)&&this.functions_max.equals(rhs.functions_max))))&&((this.built_by == rhs.built_by)||((this.built_by!= null)&&this.built_by.equals(rhs.built_by))))&&((this.deployed_by == rhs.deployed_by)||((this.deployed_by!= null)&&this.deployed_by.equals(rhs.deployed_by))))&&((this.udmi_timever == rhs.udmi_timever)||((this.udmi_timever!= null)&&this.udmi_timever.equals(rhs.udmi_timever))));
    }

}
