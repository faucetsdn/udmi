
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
    "functions",
    "last_state",
    "deployed_at"
})
@Generated("jsonschema2pojo")
public class SetupReflectorConfig {

    @JsonProperty("functions")
    public Integer functions;
    @JsonProperty("last_state")
    public Date last_state;
    @JsonProperty("deployed_at")
    public Date deployed_at;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.last_state == null)? 0 :this.last_state.hashCode()));
        result = ((result* 31)+((this.functions == null)? 0 :this.functions.hashCode()));
        result = ((result* 31)+((this.deployed_at == null)? 0 :this.deployed_at.hashCode()));
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
        return ((((this.last_state == rhs.last_state)||((this.last_state!= null)&&this.last_state.equals(rhs.last_state)))&&((this.functions == rhs.functions)||((this.functions!= null)&&this.functions.equals(rhs.functions))))&&((this.deployed_at == rhs.deployed_at)||((this.deployed_at!= null)&&this.deployed_at.equals(rhs.deployed_at))));
    }

}
