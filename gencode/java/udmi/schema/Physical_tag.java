
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "asset"
})
@Generated("jsonschema2pojo")
public class Physical_tag {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("asset")
    public Asset asset;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.asset == null)? 0 :this.asset.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Physical_tag) == false) {
            return false;
        }
        Physical_tag rhs = ((Physical_tag) other);
        return ((this.asset == rhs.asset)||((this.asset!= null)&&this.asset.equals(rhs.asset)));
    }

}
