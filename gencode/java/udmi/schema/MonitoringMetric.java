
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Monitoring metric
 * <p>
 * One metric
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "system",
    "envelope"
})
public class MonitoringMetric {

    /**
     * System Events
     * <p>
     * Used for system events such as logging. [System Event Documentation](../docs/messages/system.md#event)
     * 
     */
    @JsonProperty("system")
    @JsonPropertyDescription("Used for system events such as logging. [System Event Documentation](../docs/messages/system.md#event)")
    public SystemEvents system;
    /**
     * Envelope
     * <p>
     * The UDMI `envelope` is not a message itself, per se, but the attributes and other information that is delivered along with a message. [Message Envelope Documentation](../docs/messages/envelope.md)
     * 
     */
    @JsonProperty("envelope")
    @JsonPropertyDescription("The UDMI `envelope` is not a message itself, per se, but the attributes and other information that is delivered along with a message. [Message Envelope Documentation](../docs/messages/envelope.md)")
    public Envelope envelope;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.envelope == null)? 0 :this.envelope.hashCode()));
        result = ((result* 31)+((this.system == null)? 0 :this.system.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof MonitoringMetric) == false) {
            return false;
        }
        MonitoringMetric rhs = ((MonitoringMetric) other);
        return (((this.envelope == rhs.envelope)||((this.envelope!= null)&&this.envelope.equals(rhs.envelope)))&&((this.system == rhs.system)||((this.system!= null)&&this.system.equals(rhs.system))));
    }

}
