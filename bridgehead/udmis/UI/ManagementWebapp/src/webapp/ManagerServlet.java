package webapp;

import com.google.gson.*;
import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import webapp.mqtt.MqttConnection;
import webapp.mqtt.SiteModelManager;

import java.io.IOException;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import static webapp.ValidatorManager.NOT_STARTED;
import static webapp.ValidatorManager.RUNNING;
import static webapp.mqtt.MqttConnection.*;

public class ManagerServlet extends HttpServlet {

    @Override
    public void init() {
        Security.addProvider(new BouncyCastleProvider());

        try {
            cfg = new Configuration(Configuration.VERSION_2_3_31);
            cfg.setServletContextForTemplateLoading(getServletContext(), "WEB-INF/templates");
            cfg.setDefaultEncoding("UTF-8");
        } catch (Exception e) {
            LOGGER.severe("Error when initialising configuration: " + e.getMessage());
        }
    }

    /**********************************************************************/

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response){
        String action = request.getParameter("action");

        if(Objects.equals(action, "saveMetadata")){
            SiteModelManager modelManager = new SiteModelManager();
            JsonObject metadata = null;
            JsonObject reply = new JsonObject();
            reply.addProperty("messageType", "success");

            try{
                JsonElement jsonElement = JsonParser.parseReader(request.getReader());
                metadata = jsonElement.getAsJsonObject();
            } catch (IOException e) {
                reply.addProperty("messageType", "error");
                reply.addProperty("message", "Unable to save file: Invalid json");
                LOGGER.severe("Error parsing request body: " + e.getMessage());
            }

            if( metadata != null){
                String path = request.getParameter("path");
                reply = modelManager.updateMetadata(path, metadata);
            }

            try {
                response.setContentType("application/json");
                response.getWriter().write(String.valueOf(reply));
            } catch (Exception e) {
                LOGGER.severe("Error returning save file reply': " + e.getMessage());
            }
        }
    }

    /**********************************************************************/

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        ValidatorManager validatorManager = new ValidatorManager();
        SiteModelManager modelManager = new SiteModelManager();
        RegistrarManager registrarManager = new RegistrarManager();

        String action = request.getParameter("action");
        if (action == null) {
            action = "";
        }

        String reply = null;
        switch (action) {
            case VALIDATION_DEVICE_STATUS_SEARCH: {
                String deviceName = request.getParameter("deviceName");
                reply = validatorManager.getDeviceStatus(deviceName);
            }
            break;
            case REGISTRAR_DEVICE_STATUS_SEARCH: {
                String deviceName = request.getParameter("deviceName");
                reply = registrarManager.getDeviceStatus(deviceName);
            }
            break;
            case DEVICE_METADATA_SEARCH:{
                String deviceName = request.getParameter("deviceName");
                reply = modelManager.getDevices(deviceName);
            }break;
            case GET_DEVICE_METADATA:{
                String devicePath = request.getParameter("path");
                JsonObject deviceMetadata = modelManager.getDeviceMetadata(devicePath);
                reply = deviceMetadata.toString();
            }break;
            case RUN_REGISTRAR:{
                validatorManager.runRegistrar();
                JsonObject registrarResponse = new JsonObject();
                registrarResponse.addProperty("time", registrarManager.getLastRegistrarRun());
                registrarResponse.addProperty("devices", registrarManager.getDeviceStatus());
                reply = registrarResponse.toString();
            }break;
            case RUN_VALIDATOR:{
                reply = validatorManager.startValidator();
            }break;
            default: {
                String brokerStatus = mqttConnection.getConnectionStatus();
                model.put(BROKER_STATUS, brokerStatus);
                model.put("brokerStatusColour", getBadgeColour(brokerStatus));

                String validatorStatus = validatorManager.getStatus();
                model.put("validatorBtnText", Objects.equals(validatorStatus, RUNNING) ? "Restart Validator" : "Start Validator");
                model.put("validatorStatus", validatorStatus);
                model.put("validatorStatusColour", getBadgeColour(validatorStatus));

                model.put(CONNECTED_CLIENT_COUNT, mqttConnection.getClientCount());
                model.put(SUBSCRIPTION_COUNT, mqttConnection.getSubscriptionCount());
                model.put("validatorDeviceStatusBody", validatorManager.getDeviceStatus());
                model.put("registrarDeviceStatusBody", registrarManager.getDeviceStatus());
                model.put("registrarRun", registrarManager.getLastRegistrarRun());
                model.put("page", "summary");
            }
        }

        try{
            if (reply == null) {
                Template template = cfg.getTemplate("home-page.ftl");
                template.process(model, response.getWriter());
            }else {
                response.setContentType("text/html");
                response.getWriter().write(reply);
            }
        }catch (Exception e){
            LOGGER.severe("Error returning get request: " + e.getMessage());
        }
    }

    /**********************************************************************/

    private String getBadgeColour(String status){
        if(Objects.equals(status, RUNNING) || Objects.equals(status, CONNECTED)){
            return "green";
        }else if(Objects.equals(status, DISCONNECTED) || Objects.equals(status, NOT_STARTED)){
            return "red";
        }
        return "yellow";
    }

    /**********************************************************************/

    // region Json Helpers
    public static JsonObject getJsonObject(String key, JsonObject parent) {

        if (key != null && parent != null && parent.has(key) && parent.get(key).isJsonObject()) {
            return parent.getAsJsonObject(key);
        }
        return new JsonObject();
    }

    public static JsonArray getJsonArray(String key, JsonObject parent) {
        if (key != null && parent != null && parent.has(key) && parent.get(key).isJsonArray()) {
            return parent.getAsJsonArray(key);
        }
        return new JsonArray();
    }

    public static String getString(String key, JsonObject parent) {
        if (key != null && parent != null && parent.has(key)) {
            try {
                return parent.get(key).getAsString();
            } catch (Exception ignore) {
            }
        }
        return null;
    }
    // endregion Json Helpers

    /**********************************************************************/

    public static String getFormattedTimestamp(String ISOString) {
        String formattedLastSeen;
        if (ISOString == null || ISOString.isEmpty()) {
            formattedLastSeen = " --- ";
        } else {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss, dd MMM yyyy");
                Instant instant = Instant.parse(ISOString);
                formattedLastSeen = sdf.format(new Date(instant.toEpochMilli()));
            } catch (Exception ignore) {
                formattedLastSeen = " --- ";
            }
        }
        return formattedLastSeen;
    }

    /**********************************************************************/

    public static boolean isValidString(String string) {
        return string != null && !string.isEmpty() && !string.isBlank();
    }

    /**********************************************************************/

    public static String getEmptyTableRow() {
        return """
                <tr>
                    <td colspan="3">
                        <div id="empty-table-message">
                            No results to show
                        </div>
                    </td>
                </tr>
                """;
    }

    /**********************************************************************/

    private Configuration cfg;
    public Map<String, Object> model = new HashMap<>();
    MqttConnection mqttConnection = new MqttConnection();
    private final static Logger LOGGER = Logger.getLogger(ManagerServlet.class.getName());

    /**********************************************************************/

    private static final String VALIDATION_DEVICE_STATUS_SEARCH = "validatorDeviceStatusSearch";
    private static final String REGISTRAR_DEVICE_STATUS_SEARCH = "registrarDeviceStatusSearch";
    private static final String DEVICE_METADATA_SEARCH = "deviceMetadataSearch";
    private static final String GET_DEVICE_METADATA = "getMetadata";
    private static final String RUN_REGISTRAR = "runRegistrar";
    private static final String RUN_VALIDATOR = "runValidator";
}

