
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Pubber Options
 * <p>
 * Pubber runtime options
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "noHardware",
    "noConfigAck",
    "messageTrace",
    "extraPoint",
    "missingPoint",
    "extraField",
    "redirectRegistry"
})
@Generated("jsonschema2pojo")
public class PubberOptions {

    @JsonProperty("noHardware")
    public Boolean noHardware;
    @JsonProperty("noConfigAck")
    public Boolean noConfigAck;
    @JsonProperty("messageTrace")
    public Boolean messageTrace;
    @JsonProperty("extraPoint")
    public String extraPoint;
    @JsonProperty("missingPoint")
    public String missingPoint;
    @JsonProperty("extraField")
    public String extraField;
    @JsonProperty("redirectRegistry")
    public String redirectRegistry;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.redirectRegistry == null)? 0 :this.redirectRegistry.hashCode()));
        result = ((result* 31)+((this.noHardware == null)? 0 :this.noHardware.hashCode()));
        result = ((result* 31)+((this.extraField == null)? 0 :this.extraField.hashCode()));
        result = ((result* 31)+((this.messageTrace == null)? 0 :this.messageTrace.hashCode()));
        result = ((result* 31)+((this.missingPoint == null)? 0 :this.missingPoint.hashCode()));
        result = ((result* 31)+((this.noConfigAck == null)? 0 :this.noConfigAck.hashCode()));
        result = ((result* 31)+((this.extraPoint == null)? 0 :this.extraPoint.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PubberOptions) == false) {
            return false;
        }
        PubberOptions rhs = ((PubberOptions) other);
        return ((((((((this.redirectRegistry == rhs.redirectRegistry)||((this.redirectRegistry!= null)&&this.redirectRegistry.equals(rhs.redirectRegistry)))&&((this.noHardware == rhs.noHardware)||((this.noHardware!= null)&&this.noHardware.equals(rhs.noHardware))))&&((this.extraField == rhs.extraField)||((this.extraField!= null)&&this.extraField.equals(rhs.extraField))))&&((this.messageTrace == rhs.messageTrace)||((this.messageTrace!= null)&&this.messageTrace.equals(rhs.messageTrace))))&&((this.missingPoint == rhs.missingPoint)||((this.missingPoint!= null)&&this.missingPoint.equals(rhs.missingPoint))))&&((this.noConfigAck == rhs.noConfigAck)||((this.noConfigAck!= null)&&this.noConfigAck.equals(rhs.noConfigAck))))&&((this.extraPoint == rhs.extraPoint)||((this.extraPoint!= null)&&this.extraPoint.equals(rhs.extraPoint))));
    }

}
