
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "audience"
})
public class Jwt {

    @JsonProperty("audience")
    public String audience;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.audience == null)? 0 :this.audience.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Jwt) == false) {
            return false;
        }
        Jwt rhs = ((Jwt) other);
        return ((this.audience == rhs.audience)||((this.audience!= null)&&this.audience.equals(rhs.audience)));
    }

}
