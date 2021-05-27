import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Gateway Config Snippet
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "error_ids"
})
@Generated("jsonschema2pojo")
public class StateGateway {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("error_ids")
    private List<String> errorIds = new ArrayList<String>();

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("error_ids")
    public List<String> getErrorIds() {
        return errorIds;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("error_ids")
    public void setErrorIds(List<String> errorIds) {
        this.errorIds = errorIds;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(StateGateway.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("errorIds");
        sb.append('=');
        sb.append(((this.errorIds == null)?"<null>":this.errorIds));
        sb.append(',');
        if (sb.charAt((sb.length()- 1)) == ',') {
            sb.setCharAt((sb.length()- 1), ']');
        } else {
            sb.append(']');
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.errorIds == null)? 0 :this.errorIds.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof StateGateway) == false) {
            return false;
        }
        StateGateway rhs = ((StateGateway) other);
        return ((this.errorIds == rhs.errorIds)||((this.errorIds!= null)&&this.errorIds.equals(rhs.errorIds)));
    }

}
