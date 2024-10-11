
package udmi.schema;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Udmi Config
 * <p>
 * Config for a UDMI reflector client
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "last_state",
    "reply",
    "setup"
})
public class UdmiConfig {

    /**
     * RFC 3339 UTC timestamp the configuration was generated
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 UTC timestamp the configuration was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public String version;
    @JsonProperty("last_state")
    public Date last_state;
    /**
     * Setup Udmi State
     * <p>
     * 
     * 
     */
    @JsonProperty("reply")
    public SetupUdmiState reply;
    /**
     * Setup Udmi Config
     * <p>
     * 
     * 
     */
    @JsonProperty("setup")
    public SetupUdmiConfig setup;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.setup == null)? 0 :this.setup.hashCode()));
        result = ((result* 31)+((this.last_state == null)? 0 :this.last_state.hashCode()));
        result = ((result* 31)+((this.reply == null)? 0 :this.reply.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof UdmiConfig) == false) {
            return false;
        }
        UdmiConfig rhs = ((UdmiConfig) other);
        return ((((((this.setup == rhs.setup)||((this.setup!= null)&&this.setup.equals(rhs.setup)))&&((this.last_state == rhs.last_state)||((this.last_state!= null)&&this.last_state.equals(rhs.last_state))))&&((this.reply == rhs.reply)||((this.reply!= null)&&this.reply.equals(rhs.reply))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
