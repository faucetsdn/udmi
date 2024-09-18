
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "basic",
    "jwt"
})
public class Auth_provider {

    @JsonProperty("basic")
    public Basic basic;
    @JsonProperty("jwt")
    public Jwt jwt;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.basic == null)? 0 :this.basic.hashCode()));
        result = ((result* 31)+((this.jwt == null)? 0 :this.jwt.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Auth_provider) == false) {
            return false;
        }
        Auth_provider rhs = ((Auth_provider) other);
        return (((this.basic == rhs.basic)||((this.basic!= null)&&this.basic.equals(rhs.basic)))&&((this.jwt == rhs.jwt)||((this.jwt!= null)&&this.jwt.equals(rhs.jwt))));
    }

}
