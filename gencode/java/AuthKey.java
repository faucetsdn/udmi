import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "private"
})
@Generated("jsonschema2pojo")
public class AuthKey {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("private")
    private String _private;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("private")
    public String getPrivate() {
        return _private;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("private")
    public void setPrivate(String _private) {
        this._private = _private;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(AuthKey.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("_private");
        sb.append('=');
        sb.append(((this._private == null)?"<null>":this._private));
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
        result = ((result* 31)+((this._private == null)? 0 :this._private.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof AuthKey) == false) {
            return false;
        }
        AuthKey rhs = ((AuthKey) other);
        return ((this._private == rhs._private)||((this._private!= null)&&this._private.equals(rhs._private)));
    }

}
