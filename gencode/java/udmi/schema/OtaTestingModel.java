
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Ota Testing Model
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "blob_key",
    "url",
    "sha256",
    "version"
})
public class OtaTestingModel {

    /**
     * Blob key for the payload
     * 
     */
    @JsonProperty("blob_key")
    @JsonPropertyDescription("Blob key for the payload")
    public String blob_key;
    /**
     * URL for OTA update
     * 
     */
    @JsonProperty("url")
    @JsonPropertyDescription("URL for OTA update")
    public String url;
    /**
     * SHA256 hash of the payload
     * 
     */
    @JsonProperty("sha256")
    @JsonPropertyDescription("SHA256 hash of the payload")
    public String sha256;
    /**
     * Expected software version
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Expected software version")
    public String version;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.blob_key == null)? 0 :this.blob_key.hashCode()));
        result = ((result* 31)+((this.sha256 == null)? 0 :this.sha256 .hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.url == null)? 0 :this.url.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof OtaTestingModel) == false) {
            return false;
        }
        OtaTestingModel rhs = ((OtaTestingModel) other);
        return (((((this.blob_key == rhs.blob_key)||((this.blob_key!= null)&&this.blob_key.equals(rhs.blob_key)))&&((this.sha256 == rhs.sha256)||((this.sha256 != null)&&this.sha256 .equals(rhs.sha256))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.url == rhs.url)||((this.url!= null)&&this.url.equals(rhs.url))));
    }

}
