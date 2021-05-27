import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "private_hash"
})
@Generated("jsonschema2pojo")
public class AuthKey__2 {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("private_hash")
    private String privateHash;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("private_hash")
    public String getPrivateHash() {
        return privateHash;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("private_hash")
    public void setPrivateHash(String privateHash) {
        this.privateHash = privateHash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(AuthKey__2 .class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("privateHash");
        sb.append('=');
        sb.append(((this.privateHash == null)?"<null>":this.privateHash));
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
        result = ((result* 31)+((this.privateHash == null)? 0 :this.privateHash.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof AuthKey__2) == false) {
            return false;
        }
        AuthKey__2 rhs = ((AuthKey__2) other);
        return ((this.privateHash == rhs.privateHash)||((this.privateHash!= null)&&this.privateHash.equals(rhs.privateHash)));
    }

}
