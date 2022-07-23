
package udmi.schema;

import java.util.Date;
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
    "site_id",
    "device_id",
    "subfolder",
    "subtype",
    "status",
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
     * Site id being validated
     * (Required)
     * 
     */
    @JsonProperty("site_id")
    @JsonPropertyDescription("Site id being validated")
    public java.lang.String site_id;
    /**
     * Device id that has been validated
     * 
     */
    @JsonProperty("device_id")
    @JsonPropertyDescription("Device id that has been validated")
    public java.lang.String device_id;
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
        result = ((result* 31)+((this.device_id == null)? 0 :this.device_id.hashCode()));
        result = ((result* 31)+((this.subtype == null)? 0 :this.subtype.hashCode()));
        result = ((result* 31)+((this.devices == null)? 0 :this.devices.hashCode()));
        result = ((result* 31)+((this.site_id == null)? 0 :this.site_id.hashCode()));
        result = ((result* 31)+((this.subfolder == null)? 0 :this.subfolder.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
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
        return (((((((((((this.summary == rhs.summary)||((this.summary!= null)&&this.summary.equals(rhs.summary)))&&((this.last_updated == rhs.last_updated)||((this.last_updated!= null)&&this.last_updated.equals(rhs.last_updated))))&&((this.device_id == rhs.device_id)||((this.device_id!= null)&&this.device_id.equals(rhs.device_id))))&&((this.subtype == rhs.subtype)||((this.subtype!= null)&&this.subtype.equals(rhs.subtype))))&&((this.devices == rhs.devices)||((this.devices!= null)&&this.devices.equals(rhs.devices))))&&((this.site_id == rhs.site_id)||((this.site_id!= null)&&this.site_id.equals(rhs.site_id))))&&((this.subfolder == rhs.subfolder)||((this.subfolder!= null)&&this.subfolder.equals(rhs.subfolder))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }

}
