
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "private_hash"
})
@Generated("jsonschema2pojo")
public class Auth_key__2 {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("private_hash")
    public String private_hash;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.private_hash == null)? 0 :this.private_hash.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Auth_key__2) == false) {
            return false;
        }
        Auth_key__2 rhs = ((Auth_key__2) other);
        return ((this.private_hash == rhs.private_hash)||((this.private_hash!= null)&&this.private_hash.equals(rhs.private_hash)));
    }

}
