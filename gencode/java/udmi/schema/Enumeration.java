
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "implicit"
})
@Generated("jsonschema2pojo")
public class Enumeration {

    @JsonProperty("implicit")
    public Boolean implicit;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.implicit == null)? 0 :this.implicit.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Enumeration) == false) {
            return false;
        }
        Enumeration rhs = ((Enumeration) other);
        return ((this.implicit == rhs.implicit)||((this.implicit!= null)&&this.implicit.equals(rhs.implicit)));
    }

}
