
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Execution Configuration
 * <p>
 * Parameters for configuring the execution run of a UDMI tool
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "registry_id",
    "cloud_region",
    "site_name",
    "update_topic",
    "feed_name",
    "reflect_region",
    "site_model",
    "device_id",
    "project_id",
    "key_file",
    "serial_no",
    "log_level",
    "min_stage",
    "udmi_version",
    "udmi_root",
    "alt_project",
    "alt_registry",
    "block_unknown"
})
@Generated("jsonschema2pojo")
public class ExecutionConfiguration {

    @JsonProperty("registry_id")
    public String registry_id;
    @JsonProperty("cloud_region")
    public String cloud_region;
    @JsonProperty("site_name")
    public String site_name;
    @JsonProperty("update_topic")
    public String update_topic;
    @JsonProperty("feed_name")
    public String feed_name;
    @JsonProperty("reflect_region")
    public String reflect_region;
    @JsonProperty("site_model")
    public String site_model;
    @JsonProperty("device_id")
    public String device_id;
    @JsonProperty("project_id")
    public String project_id;
    @JsonProperty("key_file")
    public String key_file;
    @JsonProperty("serial_no")
    public String serial_no;
    @JsonProperty("log_level")
    public String log_level;
    @JsonProperty("min_stage")
    public String min_stage;
    @JsonProperty("udmi_version")
    public String udmi_version;
    @JsonProperty("udmi_root")
    public String udmi_root;
    @JsonProperty("alt_project")
    public String alt_project;
    @JsonProperty("alt_registry")
    public String alt_registry;
    @JsonProperty("block_unknown")
    public Boolean block_unknown;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.alt_registry == null)? 0 :this.alt_registry.hashCode()));
        result = ((result* 31)+((this.min_stage == null)? 0 :this.min_stage.hashCode()));
        result = ((result* 31)+((this.block_unknown == null)? 0 :this.block_unknown.hashCode()));
        result = ((result* 31)+((this.cloud_region == null)? 0 :this.cloud_region.hashCode()));
        result = ((result* 31)+((this.device_id == null)? 0 :this.device_id.hashCode()));
        result = ((result* 31)+((this.key_file == null)? 0 :this.key_file.hashCode()));
        result = ((result* 31)+((this.udmi_version == null)? 0 :this.udmi_version.hashCode()));
        result = ((result* 31)+((this.alt_project == null)? 0 :this.alt_project.hashCode()));
        result = ((result* 31)+((this.log_level == null)? 0 :this.log_level.hashCode()));
        result = ((result* 31)+((this.site_model == null)? 0 :this.site_model.hashCode()));
        result = ((result* 31)+((this.registry_id == null)? 0 :this.registry_id.hashCode()));
        result = ((result* 31)+((this.feed_name == null)? 0 :this.feed_name.hashCode()));
        result = ((result* 31)+((this.site_name == null)? 0 :this.site_name.hashCode()));
        result = ((result* 31)+((this.update_topic == null)? 0 :this.update_topic.hashCode()));
        result = ((result* 31)+((this.project_id == null)? 0 :this.project_id.hashCode()));
        result = ((result* 31)+((this.udmi_root == null)? 0 :this.udmi_root.hashCode()));
        result = ((result* 31)+((this.serial_no == null)? 0 :this.serial_no.hashCode()));
        result = ((result* 31)+((this.reflect_region == null)? 0 :this.reflect_region.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ExecutionConfiguration) == false) {
            return false;
        }
        ExecutionConfiguration rhs = ((ExecutionConfiguration) other);
        return (((((((((((((((((((this.alt_registry == rhs.alt_registry)||((this.alt_registry!= null)&&this.alt_registry.equals(rhs.alt_registry)))&&((this.min_stage == rhs.min_stage)||((this.min_stage!= null)&&this.min_stage.equals(rhs.min_stage))))&&((this.block_unknown == rhs.block_unknown)||((this.block_unknown!= null)&&this.block_unknown.equals(rhs.block_unknown))))&&((this.cloud_region == rhs.cloud_region)||((this.cloud_region!= null)&&this.cloud_region.equals(rhs.cloud_region))))&&((this.device_id == rhs.device_id)||((this.device_id!= null)&&this.device_id.equals(rhs.device_id))))&&((this.key_file == rhs.key_file)||((this.key_file!= null)&&this.key_file.equals(rhs.key_file))))&&((this.udmi_version == rhs.udmi_version)||((this.udmi_version!= null)&&this.udmi_version.equals(rhs.udmi_version))))&&((this.alt_project == rhs.alt_project)||((this.alt_project!= null)&&this.alt_project.equals(rhs.alt_project))))&&((this.log_level == rhs.log_level)||((this.log_level!= null)&&this.log_level.equals(rhs.log_level))))&&((this.site_model == rhs.site_model)||((this.site_model!= null)&&this.site_model.equals(rhs.site_model))))&&((this.registry_id == rhs.registry_id)||((this.registry_id!= null)&&this.registry_id.equals(rhs.registry_id))))&&((this.feed_name == rhs.feed_name)||((this.feed_name!= null)&&this.feed_name.equals(rhs.feed_name))))&&((this.site_name == rhs.site_name)||((this.site_name!= null)&&this.site_name.equals(rhs.site_name))))&&((this.update_topic == rhs.update_topic)||((this.update_topic!= null)&&this.update_topic.equals(rhs.update_topic))))&&((this.project_id == rhs.project_id)||((this.project_id!= null)&&this.project_id.equals(rhs.project_id))))&&((this.udmi_root == rhs.udmi_root)||((this.udmi_root!= null)&&this.udmi_root.equals(rhs.udmi_root))))&&((this.serial_no == rhs.serial_no)||((this.serial_no!= null)&&this.serial_no.equals(rhs.serial_no))))&&((this.reflect_region == rhs.reflect_region)||((this.reflect_region!= null)&&this.reflect_region.equals(rhs.reflect_region))));
    }

}
