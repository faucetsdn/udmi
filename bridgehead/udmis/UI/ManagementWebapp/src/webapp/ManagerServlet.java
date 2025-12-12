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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static webapp.UDMISManager.NOT_STARTED;
import static webapp.UDMISManager.RUNNING;
import static webapp.mqtt.MqttConnection.*;

public class ManagerServlet extends HttpServlet {

    MqttConnection mqttConnection = new MqttConnection();

    @Override
    public void init() {
        Security.addProvider(new BouncyCastleProvider());

        try {
            cfg = new Configuration(Configuration.VERSION_2_3_31);
            cfg.setServletContextForTemplateLoading(getServletContext(), "WEB-INF/templates");
            cfg.setDefaultEncoding("UTF-8");
        } catch (Exception e) {
            logMessage("Error when initialising configuration: " + e.getMessage());
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
                logMessage("received metadata : " + metadata.toString());
            } catch (IOException e) {
                reply.addProperty("messageType", "error");
                reply.addProperty("message", "Unable to save file: Invalid json");
                logMessage("Error parsing request body: " + e.getMessage());
            }

            if( metadata != null){
                String path = request.getParameter("path");
                reply = udmisManager.updateMetadata(path, metadata);
            }

            logMessage(reply.toString());

            try {
                response.setContentType("application/json");
                response.getWriter().write(String.valueOf(reply));
            } catch (Exception e) {
                logMessage("Error returning save file reply': " + e.getMessage());
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

        response.setContentType("text/html");
        switch (action) {
            case DEVICE_STATUS_SEARCH: {
                String deviceName = request.getParameter("deviceName");
                String tableData = udmisManager.getDeviceStatus(deviceName);

                try {
                    response.getWriter().write(tableData);
                } catch (Exception e) {
                    logMessage("Error return device status': " + e.getMessage());
                }
            }
            break;
            case DEVICE_METADATA_SEARCH:{
                String deviceName = request.getParameter("deviceName");
                String deviceList = udmisManager.getDevices(deviceName);
                try {
                    response.getWriter().write(deviceList);
                } catch (Exception e) {
                    logMessage("Error return device list': " + e.getMessage());
                }
            }break;
            case GET_DEVICE_METADATA:{
                String devicePath = request.getParameter("path");
                JsonObject deviceMetadata = udmisManager.getDeviceMetadata(devicePath);
                try {
                    response.getWriter().write(deviceMetadata.toString());
                } catch (Exception e) {
                    logMessage("Error return device list': " + e.getMessage());
                }
            }break;
            case RUN_REGISTRAR:{
                udmisManager.runRegistrar();
                try {
                    response.getWriter().write( udmisManager.getLastRegistrarRun());
                } catch (Exception e) {
                    logMessage("Error returning registrar result': " + e.getMessage());
                }
            }break;
            case RUN_VALIDATOR:{

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

                try {
                    Template template = cfg.getTemplate("home-page.ftl");
                    template.process(model, response.getWriter());
                } catch (Exception e) {
                    logMessage("Error setting default model: " + e.getMessage());
                }
            }
        }
    }

    public static void logMessage(String message) {
        DateTimeFormatter sdf = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        System.out.println("[TOMCAT] [" + now.format(sdf) + "] " + message);
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

    private static final String DEVICE_STATUS_SEARCH = "deviceStatusSearch";
    private static final String DEVICE_METADATA_SEARCH = "deviceMetadataSearch";
    private static final String GET_DEVICE_METADATA = "getMetadata";
    private static final String RUN_REGISTRAR = "runRegistrar";
    private static final String RUN_VALIDATOR = "runValidator";
}

