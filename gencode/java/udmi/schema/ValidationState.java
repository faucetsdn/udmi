
package udmi.schema;

import java.util.Date;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Validation State
 * <p>
 * Validation state summary
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "last_updated",
    "summary",
    "devices"
})
@Generated("jsonschema2pojo")
public class ValidationState {

    /**
     * RFC 3339 timestamp the discover telemetry event was generated
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 timestamp the discover telemetry event was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public java.lang.String version;
    /**
     * Last time this validation report was updated
     * (Required)
     * 
     */
    @JsonProperty("last_updated")
    @JsonPropertyDescription("Last time this validation report was updated")
    public Date last_updated;
    /**
     * Validation Summary
     * <p>
     * 
     * (Required)
     * 
     */
    @JsonProperty("summary")
    public ValidationSummary summary;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("devices")
    public Map<String, DeviceValidationEvent> devices;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.summary == null)? 0 :this.summary.hashCode()));
        result = ((result* 31)+((this.last_updated == null)? 0 :this.last_updated.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.devices == null)? 0 :this.devices.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ValidationState) == false) {
            return false;
        }
        ValidationState rhs = ((ValidationState) other);
        return ((((((this.summary == rhs.summary)||((this.summary!= null)&&this.summary.equals(rhs.summary)))&&((this.last_updated == rhs.last_updated)||((this.last_updated!= null)&&this.last_updated.equals(rhs.last_updated))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.devices == rhs.devices)||((this.devices!= null)&&this.devices.equals(rhs.devices))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
