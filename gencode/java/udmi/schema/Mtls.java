
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Mtls {

    /**
     * PEM-encoded private key material for mTLS authentication
     * 
     */
    @JsonProperty("private_key")
    @JsonPropertyDescription("PEM-encoded private key material for mTLS authentication")
    public String private_key;
    /**
     * PEM-encoded client certificate for mTLS authentication
     * 
     */
    @JsonProperty("certificate")
    @JsonPropertyDescription("PEM-encoded client certificate for mTLS authentication")
    public String certificate;
    /**
     * PEM-encoded CA certificate bundle for peer verification
     * 
     */
    @JsonProperty("ca_certificate")
    @JsonPropertyDescription("PEM-encoded CA certificate bundle for peer verification")
    public String ca_certificate;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.certificate == null)? 0 :this.certificate.hashCode()));
        result = ((result* 31)+((this.ca_certificate == null)? 0 :this.ca_certificate.hashCode()));
        result = ((result* 31)+((this.private_key == null)? 0 :this.private_key.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Mtls) == false) {
            return false;
        }
        Mtls rhs = ((Mtls) other);
        return ((((this.certificate == rhs.certificate)||((this.certificate!= null)&&this.certificate.equals(rhs.certificate)))&&((this.ca_certificate == rhs.ca_certificate)||((this.ca_certificate!= null)&&this.ca_certificate.equals(rhs.ca_certificate))))&&((this.private_key == rhs.private_key)||((this.private_key!= null)&&this.private_key.equals(rhs.private_key))));
    }

}
