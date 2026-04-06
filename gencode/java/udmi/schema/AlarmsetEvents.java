
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Alarmset Events
 * <p>
 * A set of alarms reporting telemetry data. [Alarmset Events Documentation](../docs/messages/alarmset.md#telemetry)
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "upgraded_from",
    "partial_update",
    "alarms"
})
public class AlarmsetEvents {

    /**
     * RFC 3339 UTC timestamp the telemetry event was generated
     * (Required)
     *
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 UTC timestamp the telemetry event was generated")
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
     * Original version of schema pre-upgrade
     *
     */
    @JsonProperty("upgraded_from")
    @JsonPropertyDescription("Original version of schema pre-upgrade")
    public java.lang.String upgraded_from;
    /**
     * Indicates if this is a partial update (only some alarms may be included)
     *
     */
    @JsonProperty("partial_update")
    @JsonPropertyDescription("Indicates if this is a partial update (only some alarms may be included)")
    public Boolean partial_update;
    /**
     * Collection of alarm names, defining the representative alarm set for this device.
     * (Required)
     *
     */
    @JsonProperty("alarms")
    @JsonPropertyDescription("Collection of alarm names, defining the representative alarm set for this device.")
    public HashMap<String, AlarmAlarmsetEvents> alarms;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.partial_update == null)? 0 :this.partial_update.hashCode()));
        result = ((result* 31)+((this.upgraded_from == null)? 0 :this.upgraded_from.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        result = ((result* 31)+((this.alarms == null)? 0 :this.alarms.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof AlarmsetEvents) == false) {
            return false;
        }
        AlarmsetEvents rhs = ((AlarmsetEvents) other);
        return ((((((this.partial_update == rhs.partial_update)||((this.partial_update!= null)&&this.partial_update.equals(rhs.partial_update)))&&((this.upgraded_from == rhs.upgraded_from)||((this.upgraded_from!= null)&&this.upgraded_from.equals(rhs.upgraded_from))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.alarms == rhs.alarms)||((this.alarms!= null)&&this.alarms.equals(rhs.alarms))));
    }

}
