
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Alarmset State
 * <p>
 * A set of alarms reporting telemetry data.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "state_etag",
    "status",
    "alarms",
    "upgraded_from"
})
public class AlarmsetState {

    /**
     * Not included in messages published by devices. Part of message subblocks within cloud pipeline. RFC 3339 Timestamp the payload was generated
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("Not included in messages published by devices. Part of message subblocks within cloud pipeline. RFC 3339 Timestamp the payload was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema, not included in messages published by devices
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema, not included in messages published by devices")
    public java.lang.String version;
    /**
     * An identifier which uniquely represents the state, and used by a device avoid race conditions where the incoming config is based off an obsolete state. [Additional information on implementation](../docs/specs/sequences/writeback.md)
     * 
     */
    @JsonProperty("state_etag")
    @JsonPropertyDescription("An identifier which uniquely represents the state, and used by a device avoid race conditions where the incoming config is based off an obsolete state. [Additional information on implementation](../docs/specs/sequences/writeback.md)")
    public java.lang.String state_etag;
    /**
     * Entry
     * <p>
     * 
     * 
     */
    @JsonProperty("status")
    public Entry status;
    /**
     * Collection of alarm names, defining the representative alarm set for this device.
     * (Required)
     * 
     */
    @JsonProperty("alarms")
    @JsonPropertyDescription("Collection of alarm names, defining the representative alarm set for this device.")
    public HashMap<String, AlarmAlarmsetState> alarms;
    /**
     * Original version of schema pre-upgrade
     * 
     */
    @JsonProperty("upgraded_from")
    @JsonPropertyDescription("Original version of schema pre-upgrade")
    public java.lang.String upgraded_from;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.upgraded_from == null)? 0 :this.upgraded_from.hashCode()));
        result = ((result* 31)+((this.state_etag == null)? 0 :this.state_etag.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        result = ((result* 31)+((this.alarms == null)? 0 :this.alarms.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof AlarmsetState) == false) {
            return false;
        }
        AlarmsetState rhs = ((AlarmsetState) other);
        return (((((((this.upgraded_from == rhs.upgraded_from)||((this.upgraded_from!= null)&&this.upgraded_from.equals(rhs.upgraded_from)))&&((this.state_etag == rhs.state_etag)||((this.state_etag!= null)&&this.state_etag.equals(rhs.state_etag))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))))&&((this.alarms == rhs.alarms)||((this.alarms!= null)&&this.alarms.equals(rhs.alarms))));
    }

}
