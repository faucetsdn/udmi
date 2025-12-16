
package udmi.schema;

import java.util.Set;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;


/**
 * Detail Relationships Model
 * <p>
 * Information for modeling noun-verb-noun relationships between devices
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "tags"
})
public class DetailRelationshipsModel {

    /**
     * Tags associated with the device
     * 
     */
    @JsonProperty("tags")
    @JsonDeserialize(as = java.util.LinkedHashSet.class)
    @JsonPropertyDescription("Tags associated with the device")
    public Set<String> tags;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.tags == null)? 0 :this.tags.hashCode()));
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
        return ((this.tags == rhs.tags)||((this.tags!= null)&&this.tags.equals(rhs.tags)));
    }

}
