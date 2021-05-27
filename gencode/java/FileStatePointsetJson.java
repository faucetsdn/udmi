import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * pointset state snippet
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "config_etag",
    "state_etag",
    "points"
})
@Generated("jsonschema2pojo")
public class FileStatePointsetJson {

    @JsonProperty("config_etag")
    private String configEtag;
    @JsonProperty("state_etag")
    private String stateEtag;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("points")
    private Object points;

    @JsonProperty("config_etag")
    public String getConfigEtag() {
        return configEtag;
    }

    @JsonProperty("config_etag")
    public void setConfigEtag(String configEtag) {
        this.configEtag = configEtag;
    }

    @JsonProperty("state_etag")
    public String getStateEtag() {
        return stateEtag;
    }

    @JsonProperty("state_etag")
    public void setStateEtag(String stateEtag) {
        this.stateEtag = stateEtag;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("points")
    public Object getPoints() {
        return points;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("points")
    public void setPoints(Object points) {
        this.points = points;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(FileStatePointsetJson.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("configEtag");
        sb.append('=');
        sb.append(((this.configEtag == null)?"<null>":this.configEtag));
        sb.append(',');
        sb.append("stateEtag");
        sb.append('=');
        sb.append(((this.stateEtag == null)?"<null>":this.stateEtag));
        sb.append(',');
        sb.append("points");
        sb.append('=');
        sb.append(((this.points == null)?"<null>":this.points));
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
        result = ((result* 31)+((this.stateEtag == null)? 0 :this.stateEtag.hashCode()));
        result = ((result* 31)+((this.configEtag == null)? 0 :this.configEtag.hashCode()));
        result = ((result* 31)+((this.points == null)? 0 :this.points.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof FileStatePointsetJson) == false) {
            return false;
        }
        FileStatePointsetJson rhs = ((FileStatePointsetJson) other);
        return ((((this.stateEtag == rhs.stateEtag)||((this.stateEtag!= null)&&this.stateEtag.equals(rhs.stateEtag)))&&((this.configEtag == rhs.configEtag)||((this.configEtag!= null)&&this.configEtag.equals(rhs.configEtag))))&&((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points))));
    }

}
