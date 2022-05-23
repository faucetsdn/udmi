
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Reflector State
 * <p>
 * State of a reflector client
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "version",
    "user",
    "timestamp"
})
@Generated("jsonschema2pojo")
public class ReflectorState {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    public String version;
    @JsonProperty("user")
    public String user;
    @JsonProperty("timestamp")
    public String timestamp;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.user == null)? 0 :this.user.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ReflectorState) == false) {
            return false;
        }
        ReflectorState rhs = ((ReflectorState) other);
        return ((((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version)))&&((this.user == rhs.user)||((this.user!= null)&&this.user.equals(rhs.user))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
