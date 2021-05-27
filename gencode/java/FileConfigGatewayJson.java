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
    "proxy_ids"
})
@Generated("jsonschema2pojo")
public class FileConfigGatewayJson {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("proxy_ids")
    private List<String> proxyIds = new ArrayList<String>();

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("proxy_ids")
    public List<String> getProxyIds() {
        return proxyIds;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("proxy_ids")
    public void setProxyIds(List<String> proxyIds) {
        this.proxyIds = proxyIds;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(FileConfigGatewayJson.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("proxyIds");
        sb.append('=');
        sb.append(((this.proxyIds == null)?"<null>":this.proxyIds));
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
        result = ((result* 31)+((this.proxyIds == null)? 0 :this.proxyIds.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof FileConfigGatewayJson) == false) {
            return false;
        }
        FileConfigGatewayJson rhs = ((FileConfigGatewayJson) other);
        return ((this.proxyIds == rhs.proxyIds)||((this.proxyIds!= null)&&this.proxyIds.equals(rhs.proxyIds)));
    }

}
