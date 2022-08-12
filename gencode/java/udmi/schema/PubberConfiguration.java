
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Pubber Configuration
 * <p>
 * Parameters to define a pubber runtime instance
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "endpoint",
    "deviceId",
    "gatewayId",
    "sitePath",
    "keyFile",
    "algorithm",
    "serialNo",
    "macAddr",
    "keyBytes",
    "options"
})
@Generated("jsonschema2pojo")
public class PubberConfiguration {

    /**
     * Endpoint Configuration
     * <p>
     * Parameters to define an MQTT endpoint
     * 
     */
    @JsonProperty("endpoint")
    @JsonPropertyDescription("Parameters to define an MQTT endpoint")
    public EndpointConfiguration endpoint;
    @JsonProperty("deviceId")
    public String deviceId;
    @JsonProperty("gatewayId")
    public String gatewayId;
    @JsonProperty("sitePath")
    public String sitePath;
    @JsonProperty("keyFile")
    public String keyFile = "local/rsa_private.pkcs8";
    @JsonProperty("algorithm")
    public String algorithm = "RS256";
    @JsonProperty("serialNo")
    public String serialNo;
    @JsonProperty("macAddr")
    public String macAddr;
    @JsonProperty("keyBytes")
    public Object keyBytes;
    /**
     * Pubber Options
     * <p>
     * Pubber runtime options
     * 
     */
    @JsonProperty("options")
    @JsonPropertyDescription("Pubber runtime options")
    public PubberOptions options;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.sitePath == null)? 0 :this.sitePath.hashCode()));
        result = ((result* 31)+((this.endpoint == null)? 0 :this.endpoint.hashCode()));
        result = ((result* 31)+((this.keyBytes == null)? 0 :this.keyBytes.hashCode()));
        result = ((result* 31)+((this.keyFile == null)? 0 :this.keyFile.hashCode()));
        result = ((result* 31)+((this.options == null)? 0 :this.options.hashCode()));
        result = ((result* 31)+((this.deviceId == null)? 0 :this.deviceId.hashCode()));
        result = ((result* 31)+((this.gatewayId == null)? 0 :this.gatewayId.hashCode()));
        result = ((result* 31)+((this.algorithm == null)? 0 :this.algorithm.hashCode()));
        result = ((result* 31)+((this.serialNo == null)? 0 :this.serialNo.hashCode()));
        result = ((result* 31)+((this.macAddr == null)? 0 :this.macAddr.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PubberConfiguration) == false) {
            return false;
        }
        PubberConfiguration rhs = ((PubberConfiguration) other);
        return (((((((((((this.sitePath == rhs.sitePath)||((this.sitePath!= null)&&this.sitePath.equals(rhs.sitePath)))&&((this.endpoint == rhs.endpoint)||((this.endpoint!= null)&&this.endpoint.equals(rhs.endpoint))))&&((this.keyBytes == rhs.keyBytes)||((this.keyBytes!= null)&&this.keyBytes.equals(rhs.keyBytes))))&&((this.keyFile == rhs.keyFile)||((this.keyFile!= null)&&this.keyFile.equals(rhs.keyFile))))&&((this.options == rhs.options)||((this.options!= null)&&this.options.equals(rhs.options))))&&((this.deviceId == rhs.deviceId)||((this.deviceId!= null)&&this.deviceId.equals(rhs.deviceId))))&&((this.gatewayId == rhs.gatewayId)||((this.gatewayId!= null)&&this.gatewayId.equals(rhs.gatewayId))))&&((this.algorithm == rhs.algorithm)||((this.algorithm!= null)&&this.algorithm.equals(rhs.algorithm))))&&((this.serialNo == rhs.serialNo)||((this.serialNo!= null)&&this.serialNo.equals(rhs.serialNo))))&&((this.macAddr == rhs.macAddr)||((this.macAddr!= null)&&this.macAddr.equals(rhs.macAddr))));
    }

}
