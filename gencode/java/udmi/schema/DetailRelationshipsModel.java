
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Detail Relationships Model
 * <p>
 * Information for modeling noun-verb-noun relationships between devices
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({

})
public class DetailRelationshipsModel {


    @Override
    public int hashCode() {
        int result = 1;
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof DetailRelationshipsModel) == false) {
            return false;
        }
        DetailRelationshipsModel rhs = ((DetailRelationshipsModel) other);
        return true;
    }

}
