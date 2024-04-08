
package udmi.schema;

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
 * SystemConnection
 * <p>
 * A collection of fields which describe device connectivity
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "type",
    "model",
    "vlan_name"
})
@Generated("jsonschema2pojo")
public class SystemConnection {

    /**
     * How the device connects to the cloud
     * 
     */
    @JsonProperty("type")
    @JsonPropertyDescription("How the device connects to the cloud")
    public SystemConnection.Type type;
    /**
     * Model type classifier used for local network configuration
     * 
     */
    @JsonProperty("model")
    @JsonPropertyDescription("Model type classifier used for local network configuration")
    public String model;
    /**
     * Name for the VLAN the device is connected into
     * 
     */
    @JsonProperty("vlan_name")
    @JsonPropertyDescription("Name for the VLAN the device is connected into")
    public String vlan_name;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.model == null)? 0 :this.model.hashCode()));
        result = ((result* 31)+((this.type == null)? 0 :this.type.hashCode()));
        result = ((result* 31)+((this.vlan_name == null)? 0 :this.vlan_name.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SystemConnection) == false) {
            return false;
        }
        SystemConnection rhs = ((SystemConnection) other);
        return ((((this.model == rhs.model)||((this.model!= null)&&this.model.equals(rhs.model)))&&((this.type == rhs.type)||((this.type!= null)&&this.type.equals(rhs.type))))&&((this.vlan_name == rhs.vlan_name)||((this.vlan_name!= null)&&this.vlan_name.equals(rhs.vlan_name))));
    }


    /**
     * How the device connects to the cloud
     * 
     */
    @Generated("jsonschema2pojo")
    public enum Type {

        DIRECT("DIRECT"),
        PROXY("PROXY"),
        GATEWAY("GATEWAY");
        private final String value;
        private final static Map<String, SystemConnection.Type> CONSTANTS = new HashMap<String, SystemConnection.Type>();

        static {
            for (SystemConnection.Type c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Type(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static SystemConnection.Type fromValue(String value) {
            SystemConnection.Type constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
