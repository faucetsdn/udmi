import java.util.Date;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * System state snippet
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "make_model",
    "serial_no",
    "auth_key",
    "firmware",
    "last_config",
    "operational",
    "statuses"
})
@Generated("jsonschema2pojo")
public class FileStateSystemJson {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("make_model")
    private String makeModel;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("serial_no")
    private String serialNo;
    @JsonProperty("auth_key")
    private AuthKey__2 authKey;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("firmware")
    private Firmware firmware;
    @JsonProperty("last_config")
    private Date lastConfig;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("operational")
    private Boolean operational;
    @JsonProperty("statuses")
    private Statuses statuses;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("make_model")
    public String getMakeModel() {
        return makeModel;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("make_model")
    public void setMakeModel(String makeModel) {
        this.makeModel = makeModel;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("serial_no")
    public String getSerialNo() {
        return serialNo;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("serial_no")
    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
    }

    @JsonProperty("auth_key")
    public AuthKey__2 getAuthKey() {
        return authKey;
    }

    @JsonProperty("auth_key")
    public void setAuthKey(AuthKey__2 authKey) {
        this.authKey = authKey;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("firmware")
    public Firmware getFirmware() {
        return firmware;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("firmware")
    public void setFirmware(Firmware firmware) {
        this.firmware = firmware;
    }

    @JsonProperty("last_config")
    public Date getLastConfig() {
        return lastConfig;
    }

    @JsonProperty("last_config")
    public void setLastConfig(Date lastConfig) {
        this.lastConfig = lastConfig;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("operational")
    public Boolean getOperational() {
        return operational;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("operational")
    public void setOperational(Boolean operational) {
        this.operational = operational;
    }

    @JsonProperty("statuses")
    public Statuses getStatuses() {
        return statuses;
    }

    @JsonProperty("statuses")
    public void setStatuses(Statuses statuses) {
        this.statuses = statuses;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(FileStateSystemJson.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("makeModel");
        sb.append('=');
        sb.append(((this.makeModel == null)?"<null>":this.makeModel));
        sb.append(',');
        sb.append("serialNo");
        sb.append('=');
        sb.append(((this.serialNo == null)?"<null>":this.serialNo));
        sb.append(',');
        sb.append("authKey");
        sb.append('=');
        sb.append(((this.authKey == null)?"<null>":this.authKey));
        sb.append(',');
        sb.append("firmware");
        sb.append('=');
        sb.append(((this.firmware == null)?"<null>":this.firmware));
        sb.append(',');
        sb.append("lastConfig");
        sb.append('=');
        sb.append(((this.lastConfig == null)?"<null>":this.lastConfig));
        sb.append(',');
        sb.append("operational");
        sb.append('=');
        sb.append(((this.operational == null)?"<null>":this.operational));
        sb.append(',');
        sb.append("statuses");
        sb.append('=');
        sb.append(((this.statuses == null)?"<null>":this.statuses));
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
        result = ((result* 31)+((this.lastConfig == null)? 0 :this.lastConfig.hashCode()));
        result = ((result* 31)+((this.makeModel == null)? 0 :this.makeModel.hashCode()));
        result = ((result* 31)+((this.authKey == null)? 0 :this.authKey.hashCode()));
        result = ((result* 31)+((this.operational == null)? 0 :this.operational.hashCode()));
        result = ((result* 31)+((this.statuses == null)? 0 :this.statuses.hashCode()));
        result = ((result* 31)+((this.firmware == null)? 0 :this.firmware.hashCode()));
        result = ((result* 31)+((this.serialNo == null)? 0 :this.serialNo.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof FileStateSystemJson) == false) {
            return false;
        }
        FileStateSystemJson rhs = ((FileStateSystemJson) other);
        return ((((((((this.lastConfig == rhs.lastConfig)||((this.lastConfig!= null)&&this.lastConfig.equals(rhs.lastConfig)))&&((this.makeModel == rhs.makeModel)||((this.makeModel!= null)&&this.makeModel.equals(rhs.makeModel))))&&((this.authKey == rhs.authKey)||((this.authKey!= null)&&this.authKey.equals(rhs.authKey))))&&((this.operational == rhs.operational)||((this.operational!= null)&&this.operational.equals(rhs.operational))))&&((this.statuses == rhs.statuses)||((this.statuses!= null)&&this.statuses.equals(rhs.statuses))))&&((this.firmware == rhs.firmware)||((this.firmware!= null)&&this.firmware.equals(rhs.firmware))))&&((this.serialNo == rhs.serialNo)||((this.serialNo!= null)&&this.serialNo.equals(rhs.serialNo))));
    }

}
