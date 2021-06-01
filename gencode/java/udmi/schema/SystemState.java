
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * System State
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "make_model",
    "serial_no",
    "auth_key",
    "firmware",
    "last_config",
    "operational",
    "statuses"
})
@Generated("jsonschema2pojo")
public class SystemState {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("make_model")
    public java.lang.String make_model;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("serial_no")
    public java.lang.String serial_no;
    @JsonProperty("auth_key")
    public Auth_key__2 auth_key;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("firmware")
    public Firmware firmware;
    @JsonProperty("last_config")
    public Date last_config;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("operational")
    public Boolean operational;
    @JsonProperty("statuses")
    public HashMap<String, Entry> statuses;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.operational == null)? 0 :this.operational.hashCode()));
        result = ((result* 31)+((this.statuses == null)? 0 :this.statuses.hashCode()));
        result = ((result* 31)+((this.auth_key == null)? 0 :this.auth_key.hashCode()));
        result = ((result* 31)+((this.serial_no == null)? 0 :this.serial_no.hashCode()));
        result = ((result* 31)+((this.firmware == null)? 0 :this.firmware.hashCode()));
        result = ((result* 31)+((this.make_model == null)? 0 :this.make_model.hashCode()));
        result = ((result* 31)+((this.last_config == null)? 0 :this.last_config.hashCode()));
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
        return ((((((((this.operational == rhs.operational)||((this.operational!= null)&&this.operational.equals(rhs.operational)))&&((this.statuses == rhs.statuses)||((this.statuses!= null)&&this.statuses.equals(rhs.statuses))))&&((this.auth_key == rhs.auth_key)||((this.auth_key!= null)&&this.auth_key.equals(rhs.auth_key))))&&((this.serial_no == rhs.serial_no)||((this.serial_no!= null)&&this.serial_no.equals(rhs.serial_no))))&&((this.firmware == rhs.firmware)||((this.firmware!= null)&&this.firmware.equals(rhs.firmware))))&&((this.make_model == rhs.make_model)||((this.make_model!= null)&&this.make_model.equals(rhs.make_model))))&&((this.last_config == rhs.last_config)||((this.last_config!= null)&&this.last_config.equals(rhs.last_config))));
    }

}
