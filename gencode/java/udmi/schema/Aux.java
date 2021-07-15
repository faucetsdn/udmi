
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "suffix"
})
@Generated("jsonschema2pojo")
public class Aux {

    @JsonProperty("suffix")
    public String suffix;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.suffix == null)? 0 :this.suffix.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Aux) == false) {
            return false;
        }
        Aux rhs = ((Aux) other);
        return ((this.suffix == rhs.suffix)||((this.suffix!= null)&&this.suffix.equals(rhs.suffix)));
    }

}
