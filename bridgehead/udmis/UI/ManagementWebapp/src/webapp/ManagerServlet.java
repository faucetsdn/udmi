package webapp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import webapp.mqtt.MqttConnection;

import java.io.IOException;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import static webapp.UDMISManager.NOT_STARTED;
import static webapp.UDMISManager.RUNNING;
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

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response){
        UDMISManager udmisManager = new UDMISManager();
        String action = request.getParameter("action");

        if(Objects.equals(action, "saveMetadata")){
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
                reply = udmisManager.updateMetadata(path, metadata);
            }

            try {
                response.setContentType("application/json");
                response.getWriter().write(String.valueOf(reply));
            } catch (Exception e) {
                LOGGER.severe("Error returning save file reply': " + e.getMessage());
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        UDMISManager udmisManager = new UDMISManager();

        String action = request.getParameter("action");
        if (action == null) {
            action = "";
        }

        String reply = null;
        switch (action) {
            case DEVICE_STATUS_SEARCH: {
                String deviceName = request.getParameter("deviceName");
                reply = udmisManager.getDeviceStatus(deviceName);
            }
            break;
            case DEVICE_METADATA_SEARCH:{
                String deviceName = request.getParameter("deviceName");
                reply = udmisManager.getDevices(deviceName);
            }break;
            case GET_DEVICE_METADATA:{
                String devicePath = request.getParameter("path");
                JsonObject deviceMetadata = udmisManager.getDeviceMetadata(devicePath);
                reply = deviceMetadata.toString();
            }break;
            case RUN_REGISTRAR:{
                reply = udmisManager.runRegistrar();
            }break;
            case RUN_VALIDATOR:{
                reply = udmisManager.startValidator();
            }break;
            default: {
                String brokerStatus = mqttConnection.getConnectionStatus();
                model.put(BROKER_STATUS, brokerStatus);
                model.put("brokerStatusColour", getBadgeColour(brokerStatus));

                String validatorStatus = udmisManager.getValidatorStatus();
                model.put("validatorStatus", validatorStatus);
                model.put("validatorStatusColour", getBadgeColour(validatorStatus));

                model.put(CONNECTED_CLIENT_COUNT, mqttConnection.getClientCount());
                model.put(SUBSCRIPTION_COUNT, mqttConnection.getSubscriptionCount());
                model.put("deviceStatusBody", udmisManager.getDeviceStatus());
                model.put("registrarRun", udmisManager.getLastRegistrarRun());
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

    private String getBadgeColour(String status){
        if(Objects.equals(status, RUNNING) || Objects.equals(status, CONNECTED)){
            return "green";
        }else if(Objects.equals(status, DISCONNECTED) || Objects.equals(status, NOT_STARTED)){
            return "red";
        }
        return "yellow";
    }

    private Configuration cfg;
    public Map<String, Object> model = new HashMap<>();
    MqttConnection mqttConnection = new MqttConnection();
    private final static Logger LOGGER = Logger.getLogger(ManagerServlet.class.getName());

    private static final String DEVICE_STATUS_SEARCH = "deviceStatusSearch";
    private static final String DEVICE_METADATA_SEARCH = "deviceMetadataSearch";
    private static final String GET_DEVICE_METADATA = "getMetadata";
    private static final String RUN_REGISTRAR = "runRegistrar";
    private static final String RUN_VALIDATOR = "runValidator";
}

