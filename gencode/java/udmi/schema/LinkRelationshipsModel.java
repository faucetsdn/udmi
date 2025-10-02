
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Link Relationships Model
 * <p>
 * Information about how this device relates to another device
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "kind"
})
public class LinkRelationshipsModel {

    /**
     * The kind of relationship between the two nodes
     * (Required)
     * 
     */
    @JsonProperty("kind")
    @JsonPropertyDescription("The kind of relationship between the two nodes")
    public String kind;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.kind == null)? 0 :this.kind.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof LinkRelationshipsModel) == false) {
            return false;
        }
        LinkRelationshipsModel rhs = ((LinkRelationshipsModel) other);
        return ((this.kind == rhs.kind)||((this.kind!= null)&&this.kind.equals(rhs.kind)));
    }

}
