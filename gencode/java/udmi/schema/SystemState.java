
package udmi.schema;

import java.util.Date;
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
    "make_model",
    "serial_no",
    "firmware",
    "last_config",
    "operational",
    "status"
})
@Generated("jsonschema2pojo")
public class SystemState {

    /**
     * The make and model of the physical device
     * (Required)
     * 
     */
    @JsonProperty("make_model")
    @JsonPropertyDescription("The make and model of the physical device")
    public String make_model;
    /**
     * The serial number of the physical device
     * (Required)
     * 
     */
    @JsonProperty("serial_no")
    @JsonPropertyDescription("The serial number of the physical device")
    public String serial_no;
    /**
     * Information about the physical device firmware
     * (Required)
     * 
     */
    @JsonProperty("firmware")
    @JsonPropertyDescription("Information about the physical device firmware")
    public Firmware firmware;
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
        result = ((result* 31)+((this.operational == null)? 0 :this.operational.hashCode()));
        result = ((result* 31)+((this.serial_no == null)? 0 :this.serial_no.hashCode()));
        result = ((result* 31)+((this.firmware == null)? 0 :this.firmware.hashCode()));
        result = ((result* 31)+((this.make_model == null)? 0 :this.make_model.hashCode()));
        result = ((result* 31)+((this.last_config == null)? 0 :this.last_config.hashCode()));
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
        return (((((((this.operational == rhs.operational)||((this.operational!= null)&&this.operational.equals(rhs.operational)))&&((this.serial_no == rhs.serial_no)||((this.serial_no!= null)&&this.serial_no.equals(rhs.serial_no))))&&((this.firmware == rhs.firmware)||((this.firmware!= null)&&this.firmware.equals(rhs.firmware))))&&((this.make_model == rhs.make_model)||((this.make_model!= null)&&this.make_model.equals(rhs.make_model))))&&((this.last_config == rhs.last_config)||((this.last_config!= null)&&this.last_config.equals(rhs.last_config))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }

}
