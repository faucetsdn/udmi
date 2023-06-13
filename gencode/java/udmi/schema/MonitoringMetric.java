
package udmi.schema;

import javax.annotation.processing.Generated;
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
    "event_system",
    "envelope"
})
@Generated("jsonschema2pojo")
public class MonitoringMetric {

    /**
     * System Event
     * <p>
     * Used for system events such as logging. [System Event Documentation](../docs/messages/system.md#event)
     * 
     */
    @JsonProperty("event_system")
    @JsonPropertyDescription("Used for system events such as logging. [System Event Documentation](../docs/messages/system.md#event)")
    public SystemEvent event_system;
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
        result = ((result* 31)+((this.event_system == null)? 0 :this.event_system.hashCode()));
        result = ((result* 31)+((this.envelope == null)? 0 :this.envelope.hashCode()));
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
        return (((this.event_system == rhs.event_system)||((this.event_system!= null)&&this.event_system.equals(rhs.event_system)))&&((this.envelope == rhs.envelope)||((this.envelope!= null)&&this.envelope.equals(rhs.envelope))));
    }

}
