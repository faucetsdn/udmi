
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Cloud Iot Config
 * <p>
 * Parameters for configuring a connection to cloud systems
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "registry_id",
    "cloud_region",
    "site_name",
    "update_topic",
    "reflect_region"
})
@Generated("jsonschema2pojo")
public class CloudIotConfig {

    @JsonProperty("registry_id")
    public String registry_id;
    @JsonProperty("cloud_region")
    public String cloud_region;
    @JsonProperty("site_name")
    public String site_name;
    @JsonProperty("update_topic")
    public String update_topic;
    @JsonProperty("reflect_region")
    public String reflect_region;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.site_name == null)? 0 :this.site_name.hashCode()));
        result = ((result* 31)+((this.cloud_region == null)? 0 :this.cloud_region.hashCode()));
        result = ((result* 31)+((this.update_topic == null)? 0 :this.update_topic.hashCode()));
        result = ((result* 31)+((this.registry_id == null)? 0 :this.registry_id.hashCode()));
        result = ((result* 31)+((this.reflect_region == null)? 0 :this.reflect_region.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof CloudIotConfig) == false) {
            return false;
        }
        CloudIotConfig rhs = ((CloudIotConfig) other);
        return ((((((this.site_name == rhs.site_name)||((this.site_name!= null)&&this.site_name.equals(rhs.site_name)))&&((this.cloud_region == rhs.cloud_region)||((this.cloud_region!= null)&&this.cloud_region.equals(rhs.cloud_region))))&&((this.update_topic == rhs.update_topic)||((this.update_topic!= null)&&this.update_topic.equals(rhs.update_topic))))&&((this.registry_id == rhs.registry_id)||((this.registry_id!= null)&&this.registry_id.equals(rhs.registry_id))))&&((this.reflect_region == rhs.reflect_region)||((this.reflect_region!= null)&&this.reflect_region.equals(rhs.reflect_region))));
    }

}
