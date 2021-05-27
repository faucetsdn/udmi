import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "site",
    "section",
    "position"
})
@Generated("jsonschema2pojo")
public class Location__1 {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("site")
    private String site;
    @JsonProperty("section")
    private String section;
    @JsonProperty("position")
    private Position__1 position;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("site")
    public String getSite() {
        return site;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("site")
    public void setSite(String site) {
        this.site = site;
    }

    @JsonProperty("section")
    public String getSection() {
        return section;
    }

    @JsonProperty("section")
    public void setSection(String section) {
        this.section = section;
    }

    @JsonProperty("position")
    public Position__1 getPosition() {
        return position;
    }

    @JsonProperty("position")
    public void setPosition(Position__1 position) {
        this.position = position;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Location__1 .class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("site");
        sb.append('=');
        sb.append(((this.site == null)?"<null>":this.site));
        sb.append(',');
        sb.append("section");
        sb.append('=');
        sb.append(((this.section == null)?"<null>":this.section));
        sb.append(',');
        sb.append("position");
        sb.append('=');
        sb.append(((this.position == null)?"<null>":this.position));
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
        result = ((result* 31)+((this.site == null)? 0 :this.site.hashCode()));
        result = ((result* 31)+((this.section == null)? 0 :this.section.hashCode()));
        result = ((result* 31)+((this.position == null)? 0 :this.position.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Location__1) == false) {
            return false;
        }
        Location__1 rhs = ((Location__1) other);
        return ((((this.site == rhs.site)||((this.site!= null)&&this.site.equals(rhs.site)))&&((this.section == rhs.section)||((this.section!= null)&&this.section.equals(rhs.section))))&&((this.position == rhs.position)||((this.position!= null)&&this.position.equals(rhs.position))));
    }

}
