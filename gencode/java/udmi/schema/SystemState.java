
package udmi.schema;

import java.util.Date;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * System State
 * <p>
 * [System State Documentation](../docs/messages/system.md#state)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "last_config",
    "operational",
    "serial_no",
    "hardware",
    "software",
    "config",
    "status"
})
@Generated("jsonschema2pojo")
public class SystemState {

    /**
     * Time from the `timestamp` field of the last successfully parsed `config` message (not the timestamp the message was received/processed).
     * (Required)
     * 
     */
    @JsonProperty("last_config")
    @JsonPropertyDescription("Time from the `timestamp` field of the last successfully parsed `config` message (not the timestamp the message was received/processed).")
    public Date last_config;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("operational")
    public Boolean operational;
    /**
     * The serial number of the physical device
     * (Required)
     * 
     */
    @JsonProperty("serial_no")
    @JsonPropertyDescription("The serial number of the physical device")
    public java.lang.String serial_no;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("hardware")
    public Map<String, String> hardware;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("software")
    public Map<String, String> software;
    /**
     * Config System State
     * <p>
     * 
     * 
     */
    @JsonProperty("config")
    public Map<String, String> config;
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
        result = ((result* 31)+((this.software == null)? 0 :this.software.hashCode()));
        result = ((result* 31)+((this.operational == null)? 0 :this.operational.hashCode()));
        result = ((result* 31)+((this.serial_no == null)? 0 :this.serial_no.hashCode()));
        result = ((result* 31)+((this.config == null)? 0 :this.config.hashCode()));
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
        return ((((((((this.software == rhs.software)||((this.software!= null)&&this.software.equals(rhs.software)))&&((this.operational == rhs.operational)||((this.operational!= null)&&this.operational.equals(rhs.operational))))&&((this.serial_no == rhs.serial_no)||((this.serial_no!= null)&&this.serial_no.equals(rhs.serial_no))))&&((this.config == rhs.config)||((this.config!= null)&&this.config.equals(rhs.config))))&&((this.last_config == rhs.last_config)||((this.last_config!= null)&&this.last_config.equals(rhs.last_config))))&&((this.hardware == rhs.hardware)||((this.hardware!= null)&&this.hardware.equals(rhs.hardware))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }

}
