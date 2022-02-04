
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * System Config
 * <p>
 * [System Config Documentation](../docs/messages/system.md#config)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "min_loglevel",
    "auth_key"
})
@Generated("jsonschema2pojo")
public class SystemConfig {

    /**
     * The minimum loglevel for reporting log messages below which log entries should not be sent. Default to 300.
     * 
     */
    @JsonProperty("min_loglevel")
    @JsonPropertyDescription("The minimum loglevel for reporting log messages below which log entries should not be sent. Default to 300.")
    public Integer min_loglevel;
    @JsonProperty("auth_key")
    public Auth_key auth_key;

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
        if ((other instanceof SystemConfig) == false) {
            return false;
        }
        SystemConfig rhs = ((SystemConfig) other);
        return (((this.min_loglevel == rhs.min_loglevel)||((this.min_loglevel!= null)&&this.min_loglevel.equals(rhs.min_loglevel)))&&((this.auth_key == rhs.auth_key)||((this.auth_key!= null)&&this.auth_key.equals(rhs.auth_key))));
    }

}
