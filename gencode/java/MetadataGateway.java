import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Gateway metadata snippet
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "gateway_id",
    "subsystem",
    "proxy_ids"
})
@Generated("jsonschema2pojo")
public class MetadataGateway {

    @JsonProperty("gateway_id")
    private String gatewayId;
    @JsonProperty("subsystem")
    private String subsystem;
    @JsonProperty("proxy_ids")
    private List<String> proxyIds = new ArrayList<String>();

    @JsonProperty("gateway_id")
    public String getGatewayId() {
        return gatewayId;
    }

    @JsonProperty("gateway_id")
    public void setGatewayId(String gatewayId) {
        this.gatewayId = gatewayId;
    }

    @JsonProperty("subsystem")
    public String getSubsystem() {
        return subsystem;
    }

    @JsonProperty("subsystem")
    public void setSubsystem(String subsystem) {
        this.subsystem = subsystem;
    }

    @JsonProperty("proxy_ids")
    public List<String> getProxyIds() {
        return proxyIds;
    }

    @JsonProperty("proxy_ids")
    public void setProxyIds(List<String> proxyIds) {
        this.proxyIds = proxyIds;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(MetadataGateway.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("gatewayId");
        sb.append('=');
        sb.append(((this.gatewayId == null)?"<null>":this.gatewayId));
        sb.append(',');
        sb.append("subsystem");
        sb.append('=');
        sb.append(((this.subsystem == null)?"<null>":this.subsystem));
        sb.append(',');
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
        result = ((result* 31)+((this.subsystem == null)? 0 :this.subsystem.hashCode()));
        result = ((result* 31)+((this.proxyIds == null)? 0 :this.proxyIds.hashCode()));
        result = ((result* 31)+((this.gatewayId == null)? 0 :this.gatewayId.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof MetadataGateway) == false) {
            return false;
        }
        MetadataGateway rhs = ((MetadataGateway) other);
        return ((((this.subsystem == rhs.subsystem)||((this.subsystem!= null)&&this.subsystem.equals(rhs.subsystem)))&&((this.proxyIds == rhs.proxyIds)||((this.proxyIds!= null)&&this.proxyIds.equals(rhs.proxyIds))))&&((this.gatewayId == rhs.gatewayId)||((this.gatewayId!= null)&&this.gatewayId.equals(rhs.gatewayId))));
    }

}
