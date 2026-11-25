
package udmi.schema;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Uufi Control
 * <p>
 * Control-plane payload for UUFI status, progress, and error replies
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UufiControl {

    /**
     * RFC 3339 UTC timestamp the control message was generated
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 UTC timestamp the control message was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * (Required)
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public String version;
    /**
     * Uufi Control Block
     * <p>
     * UUFI control-plane payload block
     * (Required)
     * 
     */
    @JsonProperty("control")
    @JsonPropertyDescription("UUFI control-plane payload block")
    public UufiControlBlock control;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.control == null)? 0 :this.control.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof UufiControl) == false) {
            return false;
        }
        UufiControl rhs = ((UufiControl) other);
        return ((((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version)))&&((this.control == rhs.control)||((this.control!= null)&&this.control.equals(rhs.control))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
