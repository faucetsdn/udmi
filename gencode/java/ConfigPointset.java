import java.util.Date;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * pointset config snippet
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "state_etag",
    "set_value_expiry",
    "sample_limit_sec",
    "sample_rate_sec",
    "points"
})
@Generated("jsonschema2pojo")
public class ConfigPointset {

    @JsonProperty("state_etag")
    private String stateEtag;
    @JsonProperty("set_value_expiry")
    private Date setValueExpiry;
    @JsonProperty("sample_limit_sec")
    private Double sampleLimitSec;
    @JsonProperty("sample_rate_sec")
    private Double sampleRateSec;
    @JsonProperty("points")
    private Object points;

    @JsonProperty("state_etag")
    public String getStateEtag() {
        return stateEtag;
    }

    @JsonProperty("state_etag")
    public void setStateEtag(String stateEtag) {
        this.stateEtag = stateEtag;
    }

    @JsonProperty("set_value_expiry")
    public Date getSetValueExpiry() {
        return setValueExpiry;
    }

    @JsonProperty("set_value_expiry")
    public void setSetValueExpiry(Date setValueExpiry) {
        this.setValueExpiry = setValueExpiry;
    }

    @JsonProperty("sample_limit_sec")
    public Double getSampleLimitSec() {
        return sampleLimitSec;
    }

    @JsonProperty("sample_limit_sec")
    public void setSampleLimitSec(Double sampleLimitSec) {
        this.sampleLimitSec = sampleLimitSec;
    }

    @JsonProperty("sample_rate_sec")
    public Double getSampleRateSec() {
        return sampleRateSec;
    }

    @JsonProperty("sample_rate_sec")
    public void setSampleRateSec(Double sampleRateSec) {
        this.sampleRateSec = sampleRateSec;
    }

    @JsonProperty("points")
    public Object getPoints() {
        return points;
    }

    @JsonProperty("points")
    public void setPoints(Object points) {
        this.points = points;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(ConfigPointset.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("stateEtag");
        sb.append('=');
        sb.append(((this.stateEtag == null)?"<null>":this.stateEtag));
        sb.append(',');
        sb.append("setValueExpiry");
        sb.append('=');
        sb.append(((this.setValueExpiry == null)?"<null>":this.setValueExpiry));
        sb.append(',');
        sb.append("sampleLimitSec");
        sb.append('=');
        sb.append(((this.sampleLimitSec == null)?"<null>":this.sampleLimitSec));
        sb.append(',');
        sb.append("sampleRateSec");
        sb.append('=');
        sb.append(((this.sampleRateSec == null)?"<null>":this.sampleRateSec));
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
        result = ((result* 31)+((this.sampleLimitSec == null)? 0 :this.sampleLimitSec.hashCode()));
        result = ((result* 31)+((this.stateEtag == null)? 0 :this.stateEtag.hashCode()));
        result = ((result* 31)+((this.sampleRateSec == null)? 0 :this.sampleRateSec.hashCode()));
        result = ((result* 31)+((this.setValueExpiry == null)? 0 :this.setValueExpiry.hashCode()));
        result = ((result* 31)+((this.points == null)? 0 :this.points.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ConfigPointset) == false) {
            return false;
        }
        ConfigPointset rhs = ((ConfigPointset) other);
        return ((((((this.sampleLimitSec == rhs.sampleLimitSec)||((this.sampleLimitSec!= null)&&this.sampleLimitSec.equals(rhs.sampleLimitSec)))&&((this.stateEtag == rhs.stateEtag)||((this.stateEtag!= null)&&this.stateEtag.equals(rhs.stateEtag))))&&((this.sampleRateSec == rhs.sampleRateSec)||((this.sampleRateSec!= null)&&this.sampleRateSec.equals(rhs.sampleRateSec))))&&((this.setValueExpiry == rhs.setValueExpiry)||((this.setValueExpiry!= null)&&this.setValueExpiry.equals(rhs.setValueExpiry))))&&((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points))));
    }

}
