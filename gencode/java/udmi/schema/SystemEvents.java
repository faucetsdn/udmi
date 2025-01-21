
package udmi.schema;

import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * System Events
 * <p>
 * Used for system events such as logging. [System Event Documentation](../docs/messages/system.md#event)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "upgraded_from",
    "last_config",
    "logentries",
    "event_no",
    "metrics"
})
public class SystemEvents {

    /**
     * RFC 3339 UTC timestamp the event payload was generated
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 UTC timestamp the event payload was generated")
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
     * Original version of schema pre-upgrade
     * 
     */
    @JsonProperty("upgraded_from")
    @JsonPropertyDescription("Original version of schema pre-upgrade")
    public String upgraded_from;
    /**
     * Last config received
     * 
     */
    @JsonProperty("last_config")
    @JsonPropertyDescription("Last config received")
    public Date last_config;
    @JsonProperty("logentries")
    public List<Entry> logentries;
    /**
     * Sequence number for this event
     * 
     */
    @JsonProperty("event_no")
    @JsonPropertyDescription("Sequence number for this event")
    public Integer event_no;
    @JsonProperty("metrics")
    public Metrics metrics;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.event_no == null)? 0 :this.event_no.hashCode()));
        result = ((result* 31)+((this.upgraded_from == null)? 0 :this.upgraded_from.hashCode()));
        result = ((result* 31)+((this.metrics == null)? 0 :this.metrics.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        result = ((result* 31)+((this.last_config == null)? 0 :this.last_config.hashCode()));
        result = ((result* 31)+((this.logentries == null)? 0 :this.logentries.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SystemEvents) == false) {
            return false;
        }
        SystemEvents rhs = ((SystemEvents) other);
        return ((((((((this.event_no == rhs.event_no)||((this.event_no!= null)&&this.event_no.equals(rhs.event_no)))&&((this.upgraded_from == rhs.upgraded_from)||((this.upgraded_from!= null)&&this.upgraded_from.equals(rhs.upgraded_from))))&&((this.metrics == rhs.metrics)||((this.metrics!= null)&&this.metrics.equals(rhs.metrics))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.last_config == rhs.last_config)||((this.last_config!= null)&&this.last_config.equals(rhs.last_config))))&&((this.logentries == rhs.logentries)||((this.logentries!= null)&&this.logentries.equals(rhs.logentries))));
    }

}
