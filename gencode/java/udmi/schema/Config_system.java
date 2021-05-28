
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * System Config snippet
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "min_loglevel",
    "auth_key"
})
@Generated("jsonschema2pojo")
public class Config_system {

    @JsonProperty("min_loglevel")
    public Double min_loglevel;
    @JsonProperty("auth_key")
    public Auth_key__1 auth_key;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.min_loglevel == null)? 0 :this.min_loglevel.hashCode()));
        result = ((result* 31)+((this.auth_key == null)? 0 :this.auth_key.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Config_system) == false) {
            return false;
        }
        Config_system rhs = ((Config_system) other);
        return (((this.min_loglevel == rhs.min_loglevel)||((this.min_loglevel!= null)&&this.min_loglevel.equals(rhs.min_loglevel)))&&((this.auth_key == rhs.auth_key)||((this.auth_key!= null)&&this.auth_key.equals(rhs.auth_key))));
    }

}
