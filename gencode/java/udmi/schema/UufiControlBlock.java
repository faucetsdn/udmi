
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Uufi Control Block
 * <p>
 * UUFI control-plane payload block
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UufiControlBlock {

    /**
     * Entry
     * <p>
     * 
     * 
     */
    @JsonProperty("status")
    public Entry status;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof UufiControlBlock) == false) {
            return false;
        }
        UufiControlBlock rhs = ((UufiControlBlock) other);
        return ((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status)));
    }

}
