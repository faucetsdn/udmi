
package udmi.schema;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * System Event
 * <p>
 * Used for system events such as logging. [System Event Documentation](../docs/messages/system.md#event)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "last_config",
    "logentries",
    "metrics"
})
@Generated("jsonschema2pojo")
public class SystemEvent {

    /**
     * RFC 3339 timestamp the event payload was generated
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 timestamp the event payload was generated")
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
     * Last config received
     * 
     */
    @JsonProperty("last_config")
    @JsonPropertyDescription("Last config received")
    public Date last_config;
    @JsonProperty("logentries")
    public List<Entry> logentries = new ArrayList<Entry>();
    @JsonProperty("metrics")
    public Metrics metrics;

    @Override
    public int hashCode() {
        int result = 1;
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
        if ((other instanceof SystemEvent) == false) {
            return false;
        }
        SystemEvent rhs = ((SystemEvent) other);
        return ((((((this.metrics == rhs.metrics)||((this.metrics!= null)&&this.metrics.equals(rhs.metrics)))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.last_config == rhs.last_config)||((this.last_config!= null)&&this.last_config.equals(rhs.last_config))))&&((this.logentries == rhs.logentries)||((this.logentries!= null)&&this.logentries.equals(rhs.logentries))));
    }

}
