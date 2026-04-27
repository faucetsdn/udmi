
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Alarmset Config
 * <p>
 * [Alarmset Config Documentation](../docs/messages/alarmset.md#config)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "alarms"
})
public class AlarmsetConfig {

    /**
     * RFC 3339 UTC timestamp the configuration was generated
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 UTC timestamp the configuration was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public java.lang.String version;
    /**
     * The alarms defined in this dictionary is the authoritative source indicating the representative alarms for the device. [Alarmset doumentation](../docs/messages/alarmset.md)
     * 
     */
    @JsonProperty("alarms")
    @JsonPropertyDescription("The alarms defined in this dictionary is the authoritative source indicating the representative alarms for the device. [Alarmset doumentation](../docs/messages/alarmset.md)")
    public HashMap<String, AlarmAlarmsetConfig> alarms;

    @Override
    public int hashCode() {
        int result = 1;
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
        if ((other instanceof AlarmsetConfig) == false) {
            return false;
        }
        AlarmsetConfig rhs = ((AlarmsetConfig) other);
        return ((((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version)))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.alarms == rhs.alarms)||((this.alarms!= null)&&this.alarms.equals(rhs.alarms))));
    }

}
