
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Building Config Entity
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "code",
    "etag",
    "type"
})
public class BuildingConfigEntity {

    @JsonProperty("code")
    public String code;
    @JsonProperty("etag")
    public String etag;
    @JsonProperty("type")
    public String type;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.type == null)? 0 :this.type.hashCode()));
        result = ((result* 31)+((this.etag == null)? 0 :this.etag.hashCode()));
        result = ((result* 31)+((this.code == null)? 0 :this.code.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof BuildingConfigEntity) == false) {
            return false;
        }
        BuildingConfigEntity rhs = ((BuildingConfigEntity) other);
        return ((((this.type == rhs.type)||((this.type!= null)&&this.type.equals(rhs.type)))&&((this.etag == rhs.etag)||((this.etag!= null)&&this.etag.equals(rhs.etag))))&&((this.code == rhs.code)||((this.code!= null)&&this.code.equals(rhs.code))));
    }

}
