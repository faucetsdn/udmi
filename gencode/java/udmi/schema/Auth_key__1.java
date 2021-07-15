
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "private"
})
@Generated("jsonschema2pojo")
public class Auth_key__1 {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("private")
    public String _private;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this._private == null)? 0 :this._private.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Auth_key__1) == false) {
            return false;
        }
        Auth_key__1 rhs = ((Auth_key__1) other);
        return ((this._private == rhs._private)||((this._private!= null)&&this._private.equals(rhs._private)));
    }

}
