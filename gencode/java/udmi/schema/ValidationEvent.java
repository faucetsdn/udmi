
package udmi.schema;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Validation Event
 * <p>
 * Validation result
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "last_updated",
    "subfolder",
    "subtype",
    "status",
    "missing_points",
    "errors",
    "summary",
    "devices"
})
@Generated("jsonschema2pojo")
public class ValidationEvent {

    /**
     * RFC 3339 timestamp the discover telemetry event was generated
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 timestamp the discover telemetry event was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * (Required)
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public java.lang.String version;
    /**
     * Last time this validation report was updated
     * 
     */
    @JsonProperty("last_updated")
    @JsonPropertyDescription("Last time this validation report was updated")
    public Date last_updated;
    /**
     * Subfolder of the validated message
     * 
     */
    @JsonProperty("subfolder")
    @JsonPropertyDescription("Subfolder of the validated message")
    public java.lang.String subfolder;
    /**
     * Subtype of the validated message
     * 
     */
    @JsonProperty("subtype")
    @JsonPropertyDescription("Subtype of the validated message")
    public java.lang.String subtype;
    /**
     * Entry
     * <p>
     * 
     * 
     */
    @JsonProperty("status")
    public Entry status;
    /**
     * Missing points discovered while validating a device
     * 
     */
    @JsonProperty("missing_points")
    @JsonPropertyDescription("Missing points discovered while validating a device")
    public List<java.lang.String> missing_points = new ArrayList<java.lang.String>();
    /**
     * Errors encountered while validating a device
     * 
     */
    @JsonProperty("errors")
    @JsonPropertyDescription("Errors encountered while validating a device")
    public List<Entry> errors = new ArrayList<Entry>();
    /**
     * Validation Summary
     * <p>
     * 
     * 
     */
    @JsonProperty("summary")
    public ValidationSummary summary;
    @JsonProperty("devices")
    public Map<String, DeviceValidationEvent> devices;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.summary == null)? 0 :this.summary.hashCode()));
        result = ((result* 31)+((this.last_updated == null)? 0 :this.last_updated.hashCode()));
        result = ((result* 31)+((this.subtype == null)? 0 :this.subtype.hashCode()));
        result = ((result* 31)+((this.devices == null)? 0 :this.devices.hashCode()));
        result = ((result* 31)+((this.subfolder == null)? 0 :this.subfolder.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.missing_points == null)? 0 :this.missing_points.hashCode()));
        result = ((result* 31)+((this.errors == null)? 0 :this.errors.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ValidationEvent) == false) {
            return false;
        }
        ValidationEvent rhs = ((ValidationEvent) other);
        return (((((((((((this.summary == rhs.summary)||((this.summary!= null)&&this.summary.equals(rhs.summary)))&&((this.last_updated == rhs.last_updated)||((this.last_updated!= null)&&this.last_updated.equals(rhs.last_updated))))&&((this.subtype == rhs.subtype)||((this.subtype!= null)&&this.subtype.equals(rhs.subtype))))&&((this.devices == rhs.devices)||((this.devices!= null)&&this.devices.equals(rhs.devices))))&&((this.subfolder == rhs.subfolder)||((this.subfolder!= null)&&this.subfolder.equals(rhs.subfolder))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.missing_points == rhs.missing_points)||((this.missing_points!= null)&&this.missing_points.equals(rhs.missing_points))))&&((this.errors == rhs.errors)||((this.errors!= null)&&this.errors.equals(rhs.errors))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }

}
