
package udmi.schema;

import java.util.Date;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Reflector Config
 * <p>
 * Config for a reflector client
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "udmis"
})
@Generated("jsonschema2pojo")
public class ReflectorConfig {

    /**
     * RFC 3339 Timestamp the state payload was generated
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 Timestamp the state payload was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public String version;
    /**
     * Setup Reflector Config
     * <p>
     * 
     * 
     */
    @JsonProperty("udmis")
    public SetupReflectorConfig udmis;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        result = ((result* 31)+((this.udmis == null)? 0 :this.udmis.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ReflectorConfig) == false) {
            return false;
        }
        ReflectorConfig rhs = ((ReflectorConfig) other);
        return ((((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version)))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.udmis == rhs.udmis)||((this.udmis!= null)&&this.udmis.equals(rhs.udmis))));
    }

}
