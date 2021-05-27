import java.util.HashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Cloud configuration metadata snippet
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "auth_type",
    "device_key",
    "is_gateway"
})
@Generated("jsonschema2pojo")
public class MetadataCloud {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("auth_type")
    private MetadataCloud.AuthType authType;
    @JsonProperty("device_key")
    private Boolean deviceKey;
    @JsonProperty("is_gateway")
    private Boolean isGateway;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("auth_type")
    public MetadataCloud.AuthType getAuthType() {
        return authType;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("auth_type")
    public void setAuthType(MetadataCloud.AuthType authType) {
        this.authType = authType;
    }

    @JsonProperty("device_key")
    public Boolean getDeviceKey() {
        return deviceKey;
    }

    @JsonProperty("device_key")
    public void setDeviceKey(Boolean deviceKey) {
        this.deviceKey = deviceKey;
    }

    @JsonProperty("is_gateway")
    public Boolean getIsGateway() {
        return isGateway;
    }

    @JsonProperty("is_gateway")
    public void setIsGateway(Boolean isGateway) {
        this.isGateway = isGateway;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(MetadataCloud.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("authType");
        sb.append('=');
        sb.append(((this.authType == null)?"<null>":this.authType));
        sb.append(',');
        sb.append("deviceKey");
        sb.append('=');
        sb.append(((this.deviceKey == null)?"<null>":this.deviceKey));
        sb.append(',');
        sb.append("isGateway");
        sb.append('=');
        sb.append(((this.isGateway == null)?"<null>":this.isGateway));
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
        result = ((result* 31)+((this.deviceKey == null)? 0 :this.deviceKey.hashCode()));
        result = ((result* 31)+((this.isGateway == null)? 0 :this.isGateway.hashCode()));
        result = ((result* 31)+((this.authType == null)? 0 :this.authType.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof MetadataCloud) == false) {
            return false;
        }
        MetadataCloud rhs = ((MetadataCloud) other);
        return ((((this.deviceKey == rhs.deviceKey)||((this.deviceKey!= null)&&this.deviceKey.equals(rhs.deviceKey)))&&((this.isGateway == rhs.isGateway)||((this.isGateway!= null)&&this.isGateway.equals(rhs.isGateway))))&&((this.authType == rhs.authType)||((this.authType!= null)&&this.authType.equals(rhs.authType))));
    }

    @Generated("jsonschema2pojo")
    public enum AuthType {

        ES_256("ES256"),
        ES_256_X_509("ES256_X509"),
        RS_256("RS256"),
        RS_256_X_509("RS256_X509");
        private final String value;
        private final static Map<String, MetadataCloud.AuthType> CONSTANTS = new HashMap<String, MetadataCloud.AuthType>();

        static {
            for (MetadataCloud.AuthType c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        AuthType(String value) {
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
        public static MetadataCloud.AuthType fromValue(String value) {
            MetadataCloud.AuthType constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
