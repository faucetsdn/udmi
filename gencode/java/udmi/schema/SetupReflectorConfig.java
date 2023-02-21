
package udmi.schema;

import java.util.Date;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Setup Reflector Config
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "functions_min",
    "functions_max",
    "udmi_version",
    "last_state",
    "deployed_at",
    "deployed_by"
})
@Generated("jsonschema2pojo")
public class SetupReflectorConfig {

    @JsonProperty("functions_min")
    public Integer functions_min;
    @JsonProperty("functions_max")
    public Integer functions_max;
    @JsonProperty("udmi_version")
    public String udmi_version;
    @JsonProperty("last_state")
    public Date last_state;
    @JsonProperty("deployed_at")
    public Date deployed_at;
    @JsonProperty("deployed_by")
    public String deployed_by;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.last_state == null)? 0 :this.last_state.hashCode()));
        result = ((result* 31)+((this.udmi_version == null)? 0 :this.udmi_version.hashCode()));
        result = ((result* 31)+((this.functions_min == null)? 0 :this.functions_min.hashCode()));
        result = ((result* 31)+((this.deployed_at == null)? 0 :this.deployed_at.hashCode()));
        result = ((result* 31)+((this.functions_max == null)? 0 :this.functions_max.hashCode()));
        result = ((result* 31)+((this.deployed_by == null)? 0 :this.deployed_by.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SetupReflectorConfig) == false) {
            return false;
        }
        SetupReflectorConfig rhs = ((SetupReflectorConfig) other);
        return (((((((this.last_state == rhs.last_state)||((this.last_state!= null)&&this.last_state.equals(rhs.last_state)))&&((this.udmi_version == rhs.udmi_version)||((this.udmi_version!= null)&&this.udmi_version.equals(rhs.udmi_version))))&&((this.functions_min == rhs.functions_min)||((this.functions_min!= null)&&this.functions_min.equals(rhs.functions_min))))&&((this.deployed_at == rhs.deployed_at)||((this.deployed_at!= null)&&this.deployed_at.equals(rhs.deployed_at))))&&((this.functions_max == rhs.functions_max)||((this.functions_max!= null)&&this.functions_max.equals(rhs.functions_max))))&&((this.deployed_by == rhs.deployed_by)||((this.deployed_by!= null)&&this.deployed_by.equals(rhs.deployed_by))));
    }

}
