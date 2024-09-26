
package udmi.schema;

import java.util.Date;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Udmi State
 * <p>
 * State of a UDMI reflector client
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "source",
    "regions",
    "setup"
})
public class UdmiState {

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
    public java.lang.String version;
    /**
     * Indicator of where this source update came from
     * 
     */
    @JsonProperty("source")
    @JsonPropertyDescription("Indicator of where this source update came from")
    public java.lang.String source;
    /**
     * Regions Udmi State
     * <p>
     * 
     * 
     */
    @JsonProperty("regions")
    public Map<String, String> regions;
    /**
     * Setup Udmi State
     * <p>
     * 
     * 
     */
    @JsonProperty("setup")
    public SetupUdmiState setup;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.setup == null)? 0 :this.setup.hashCode()));
        result = ((result* 31)+((this.source == null)? 0 :this.source.hashCode()));
        result = ((result* 31)+((this.regions == null)? 0 :this.regions.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof UdmiState) == false) {
            return false;
        }
        UdmiState rhs = ((UdmiState) other);
        return ((((((this.setup == rhs.setup)||((this.setup!= null)&&this.setup.equals(rhs.setup)))&&((this.source == rhs.source)||((this.source!= null)&&this.source.equals(rhs.source))))&&((this.regions == rhs.regions)||((this.regions!= null)&&this.regions.equals(rhs.regions))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
