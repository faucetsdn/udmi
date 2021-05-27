import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * System Config snippet
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "min_loglevel",
    "auth_key"
})
@Generated("jsonschema2pojo")
public class ConfigSystem {

    @JsonProperty("min_loglevel")
    private Double minLoglevel;
    @JsonProperty("auth_key")
    private AuthKey__1 authKey;

    @JsonProperty("min_loglevel")
    public Double getMinLoglevel() {
        return minLoglevel;
    }

    @JsonProperty("min_loglevel")
    public void setMinLoglevel(Double minLoglevel) {
        this.minLoglevel = minLoglevel;
    }

    @JsonProperty("auth_key")
    public AuthKey__1 getAuthKey() {
        return authKey;
    }

    @JsonProperty("auth_key")
    public void setAuthKey(AuthKey__1 authKey) {
        this.authKey = authKey;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(ConfigSystem.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("minLoglevel");
        sb.append('=');
        sb.append(((this.minLoglevel == null)?"<null>":this.minLoglevel));
        sb.append(',');
        sb.append("authKey");
        sb.append('=');
        sb.append(((this.authKey == null)?"<null>":this.authKey));
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
        result = ((result* 31)+((this.authKey == null)? 0 :this.authKey.hashCode()));
        result = ((result* 31)+((this.minLoglevel == null)? 0 :this.minLoglevel.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ConfigSystem) == false) {
            return false;
        }
        ConfigSystem rhs = ((ConfigSystem) other);
        return (((this.authKey == rhs.authKey)||((this.authKey!= null)&&this.authKey.equals(rhs.authKey)))&&((this.minLoglevel == rhs.minLoglevel)||((this.minLoglevel!= null)&&this.minLoglevel.equals(rhs.minLoglevel))));
    }

}
