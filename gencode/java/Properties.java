import java.util.HashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Device Properties Schema
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
@Generated("jsonschema2pojo")
public class Properties {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("key_type")
    private Properties.KeyType keyType;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    private Properties.Version version;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("connect")
    private Properties.Connect connect;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("key_type")
    public Properties.KeyType getKeyType() {
        return keyType;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("key_type")
    public void setKeyType(Properties.KeyType keyType) {
        this.keyType = keyType;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    public Properties.Version getVersion() {
        return version;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    public void setVersion(Properties.Version version) {
        this.version = version;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("connect")
    public Properties.Connect getConnect() {
        return connect;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("connect")
    public void setConnect(Properties.Connect connect) {
        this.connect = connect;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Properties.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("keyType");
        sb.append('=');
        sb.append(((this.keyType == null)?"<null>":this.keyType));
        sb.append(',');
        sb.append("version");
        sb.append('=');
        sb.append(((this.version == null)?"<null>":this.version));
        sb.append(',');
        sb.append("connect");
        sb.append('=');
        sb.append(((this.connect == null)?"<null>":this.connect));
        sb.append(',');
        if (sb.charAt((sb.length()- 1)) == ',') {
            sb.setCharAt((sb.length()- 1), ']');
        } else {
            sb.append(']');
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.keyType == null)? 0 :this.keyType.hashCode()));
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
        return ((((this.keyType == rhs.keyType)||((this.keyType!= null)&&this.keyType.equals(rhs.keyType)))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.connect == rhs.connect)||((this.connect!= null)&&this.connect.equals(rhs.connect))));
    }

    @Generated("jsonschema2pojo")
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

    @Generated("jsonschema2pojo")
    public enum KeyType {

        RSA_PEM("RSA_PEM"),
        RSA_X_509_PEM("RSA_X509_PEM");
        private final String value;
        private final static Map<String, Properties.KeyType> CONSTANTS = new HashMap<String, Properties.KeyType>();

        static {
            for (Properties.KeyType c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        KeyType(String value) {
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
        public static Properties.KeyType fromValue(String value) {
            Properties.KeyType constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

    @Generated("jsonschema2pojo")
    public enum Version {

        _1("1");
        private final String value;
        private final static Map<String, Properties.Version> CONSTANTS = new HashMap<String, Properties.Version>();

        static {
            for (Properties.Version c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Version(String value) {
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
        public static Properties.Version fromValue(String value) {
            Properties.Version constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
