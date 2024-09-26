
package udmi.schema;

import java.util.Date;
import java.util.Map;
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
    "cloud_version",
    "udmi_version",
    "last_updated",
    "start_time",
    "status",
    "features",
    "schemas",
    "summary",
    "devices"
})
public class ValidationState {

    /**
     * Not included in messages published by devices. Part of message subblocks within cloud pipeline. RFC 3339 Timestamp the payload was generated
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("Not included in messages published by devices. Part of message subblocks within cloud pipeline. RFC 3339 Timestamp the payload was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public java.lang.String version;
    /**
     * Setup Udmi Config
     * <p>
     * 
     * 
     */
    @JsonProperty("cloud_version")
    public SetupUdmiConfig cloud_version;
    /**
     * Version of the local udmi tools
     * 
     */
    @JsonProperty("udmi_version")
    @JsonPropertyDescription("Version of the local udmi tools")
    public java.lang.String udmi_version;
    /**
     * Last time this validation report was updated
     * 
     */
    @JsonProperty("last_updated")
    @JsonPropertyDescription("Last time this validation report was updated")
    public Date last_updated;
    /**
     * timestamp of when the validation report was started
     * 
     */
    @JsonProperty("start_time")
    @JsonPropertyDescription("timestamp of when the validation report was started")
    public Date start_time;
    /**
     * Entry
     * <p>
     * 
     * 
     */
    @JsonProperty("status")
    public Entry status;
    @JsonProperty("features")
    public Map<String, FeatureValidationState> features;
    @JsonProperty("schemas")
    public Map<String, SchemaValidationState> schemas;
    /**
     * Validation Summary
     * <p>
     * 
     * 
     */
    @JsonProperty("summary")
    public ValidationSummary summary;
    @JsonProperty("devices")
    public Map<String, DeviceValidationEvents> devices;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.summary == null)? 0 :this.summary.hashCode()));
        result = ((result* 31)+((this.start_time == null)? 0 :this.start_time.hashCode()));
        result = ((result* 31)+((this.features == null)? 0 :this.features.hashCode()));
        result = ((result* 31)+((this.last_updated == null)? 0 :this.last_updated.hashCode()));
        result = ((result* 31)+((this.udmi_version == null)? 0 :this.udmi_version.hashCode()));
        result = ((result* 31)+((this.devices == null)? 0 :this.devices.hashCode()));
        result = ((result* 31)+((this.schemas == null)? 0 :this.schemas.hashCode()));
        result = ((result* 31)+((this.cloud_version == null)? 0 :this.cloud_version.hashCode()));
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
        if ((other instanceof ValidationState) == false) {
            return false;
        }
        ValidationState rhs = ((ValidationState) other);
        return ((((((((((((this.summary == rhs.summary)||((this.summary!= null)&&this.summary.equals(rhs.summary)))&&((this.start_time == rhs.start_time)||((this.start_time!= null)&&this.start_time.equals(rhs.start_time))))&&((this.features == rhs.features)||((this.features!= null)&&this.features.equals(rhs.features))))&&((this.last_updated == rhs.last_updated)||((this.last_updated!= null)&&this.last_updated.equals(rhs.last_updated))))&&((this.udmi_version == rhs.udmi_version)||((this.udmi_version!= null)&&this.udmi_version.equals(rhs.udmi_version))))&&((this.devices == rhs.devices)||((this.devices!= null)&&this.devices.equals(rhs.devices))))&&((this.schemas == rhs.schemas)||((this.schemas!= null)&&this.schemas.equals(rhs.schemas))))&&((this.cloud_version == rhs.cloud_version)||((this.cloud_version!= null)&&this.cloud_version.equals(rhs.cloud_version))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }

}
