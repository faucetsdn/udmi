package webapp;

import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import webapp.mqtt.MqttConnection;

import java.security.Security;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
            logMessage("Error where initialising configuration: " + e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        UDMISManager udmisManager = new UDMISManager();

        String brokerStatus = mqttConnection.getConnectionStatus();
        String statusColour = Objects.equals(brokerStatus, CONNECTED)
                ? "green"
                : Objects.equals(brokerStatus, DISCONNECTED)
                    ? "red"
                    : "yellow";

        model.put(BROKER_STATUS, brokerStatus);
        model.put("brokerStatusColour", statusColour);
        model.put(CONNECTED_CLIENT_COUNT, mqttConnection.getClientCount());
        model.put(SUBSCRIPTION_COUNT, mqttConnection.getSubscriptionCount());
        udmisManager.getDeviceStatus();

        try {
            response.setContentType("text/html");
            Template template = cfg.getTemplate("home-page.ftl");
            template.process(model, response.getWriter());
        } catch (Exception e) {
            logMessage("Error setting default model: " + e.getMessage());
        }
    }

    public static void logMessage(String message) {
        DateTimeFormatter sdf = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        System.out.println("[TOMCAT] [" + now.format(sdf) + "] " + message);
    }

    private Configuration cfg;
    public Map<String, Object> model = new HashMap<>();
}

