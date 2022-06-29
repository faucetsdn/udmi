
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * System State
 * <p>
 * [System State Documentation](../docs/messages/system.md#state)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "last_config",
    "operational",
    "mode",
    "serial_no",
    "hardware",
    "software",
    "params",
    "status"
})
@Generated("jsonschema2pojo")
public class SystemState {

    /**
     * RFC 3339 timestamp the configuration was generated
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 timestamp the configuration was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public java.lang.String version;
    /**
     * Time from the `timestamp` field of the last successfully parsed `config` message (not the timestamp the message was received/processed). Part of the [config state sequence](../docs/specs/sequences/config.md)
     * (Required)
     * 
     */
    @JsonProperty("last_config")
    @JsonPropertyDescription("Time from the `timestamp` field of the last successfully parsed `config` message (not the timestamp the message was received/processed). Part of the [config state sequence](../docs/specs/sequences/config.md)")
    public Date last_config;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("operational")
    public Boolean operational;
    /**
     * Current operating mode for the device. Defaults to 'active'.
     * 
     */
    @JsonProperty("mode")
    @JsonPropertyDescription("Current operating mode for the device. Defaults to 'active'.")
    public SystemState.Mode mode;
    /**
     * The serial number of the physical device
     * (Required)
     * 
     */
    @JsonProperty("serial_no")
    @JsonPropertyDescription("The serial number of the physical device")
    public java.lang.String serial_no;
    /**
     * SystemHardware
     * <p>
     * A collection of fields which describe the physical hardware of the device.
     * (Required)
     * 
     */
    @JsonProperty("hardware")
    @JsonPropertyDescription("A collection of fields which describe the physical hardware of the device.")
    public SystemHardware hardware;
    /**
     * A collection of items which can be used to describe version of software running on a device
     * (Required)
     * 
     */
    @JsonProperty("software")
    @JsonPropertyDescription("A collection of items which can be used to describe version of software running on a device")
    public Map<String, String> software;
    @JsonProperty("params")
    public Map<String, String> params;
    /**
     * Entry
     * <p>
     * 
     * 
     */
    @JsonProperty("status")
    public Entry status;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.mode == null)? 0 :this.mode.hashCode()));
        result = ((result* 31)+((this.software == null)? 0 :this.software.hashCode()));
        result = ((result* 31)+((this.operational == null)? 0 :this.operational.hashCode()));
        result = ((result* 31)+((this.params == null)? 0 :this.params.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.serial_no == null)? 0 :this.serial_no.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        result = ((result* 31)+((this.last_config == null)? 0 :this.last_config.hashCode()));
        result = ((result* 31)+((this.hardware == null)? 0 :this.hardware.hashCode()));
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SystemState) == false) {
            return false;
        }
        SystemState rhs = ((SystemState) other);
        return (((((((((((this.mode == rhs.mode)||((this.mode!= null)&&this.mode.equals(rhs.mode)))&&((this.software == rhs.software)||((this.software!= null)&&this.software.equals(rhs.software))))&&((this.operational == rhs.operational)||((this.operational!= null)&&this.operational.equals(rhs.operational))))&&((this.params == rhs.params)||((this.params!= null)&&this.params.equals(rhs.params))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.serial_no == rhs.serial_no)||((this.serial_no!= null)&&this.serial_no.equals(rhs.serial_no))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.last_config == rhs.last_config)||((this.last_config!= null)&&this.last_config.equals(rhs.last_config))))&&((this.hardware == rhs.hardware)||((this.hardware!= null)&&this.hardware.equals(rhs.hardware))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }


    /**
     * Current operating mode for the device. Defaults to 'active'.
     * 
     */
    @Generated("jsonschema2pojo")
    public enum Mode {

        ACTIVE("active"),
        READY("ready"),
        RESTARTING("restarting");
        private final java.lang.String value;
        private final static Map<java.lang.String, SystemState.Mode> CONSTANTS = new HashMap<java.lang.String, SystemState.Mode>();

        static {
            for (SystemState.Mode c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Mode(java.lang.String value) {
            this.value = value;
        }

        @Override
        public java.lang.String toString() {
            return this.value;
        }

        @JsonValue
        public java.lang.String value() {
            return this.value;
        }

        @JsonCreator
        public static SystemState.Mode fromValue(java.lang.String value) {
            SystemState.Mode constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
