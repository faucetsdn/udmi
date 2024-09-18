
package udmi.schema;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Properties
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "key_type",
    "version",
    "connect"
})
public class Properties {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("key_type")
    public Properties.Key_type key_type;
    /**
     * Version of the UDMI schema
     * (Required)
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public String version;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("connect")
    public Properties.Connect connect;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.key_type == null)? 0 :this.key_type.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.connect == null)? 0 :this.connect.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Properties) == false) {
            return false;
        }
        Properties rhs = ((Properties) other);
        return ((((this.key_type == rhs.key_type)||((this.key_type!= null)&&this.key_type.equals(rhs.key_type)))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.connect == rhs.connect)||((this.connect!= null)&&this.connect.equals(rhs.connect))));
    }

    public enum Connect {

        DIRECT("direct");
        private final String value;
        private final static Map<String, Properties.Connect> CONSTANTS = new HashMap<String, Properties.Connect>();

        static {
            for (Properties.Connect c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Connect(String value) {
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
        public static Properties.Connect fromValue(String value) {
            Properties.Connect constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

    public enum Key_type {

        RSA_PEM("RSA_PEM"),
        RSA_X_509_PEM("RSA_X509_PEM");
        private final String value;
        private final static Map<String, Properties.Key_type> CONSTANTS = new HashMap<String, Properties.Key_type>();

        static {
            for (Properties.Key_type c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Key_type(String value) {
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
        public static Properties.Key_type fromValue(String value) {
            Properties.Key_type constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
