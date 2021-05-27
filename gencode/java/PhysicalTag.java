import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "asset"
})
@Generated("jsonschema2pojo")
public class PhysicalTag {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("asset")
    private Asset asset;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("asset")
    public Asset getAsset() {
        return asset;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("asset")
    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(PhysicalTag.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("asset");
        sb.append('=');
        sb.append(((this.asset == null)?"<null>":this.asset));
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
        result = ((result* 31)+((this.asset == null)? 0 :this.asset.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PhysicalTag) == false) {
            return false;
        }
        PhysicalTag rhs = ((PhysicalTag) other);
        return ((this.asset == rhs.asset)||((this.asset!= null)&&this.asset.equals(rhs.asset)));
    }

}
