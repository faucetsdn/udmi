package webapp.mqtt;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.Map;
import java.util.Objects;

import static java.lang.Thread.sleep;
import static webapp.ManagerServlet.logMessage;
import static webapp.ManagerWebsocket.sendWebsocketMessage;

public class MqttConnection implements MqttCallback {

    private synchronized void checkConnection() {
        if (client != null && client.isConnected()) {
            return;
        }

        for (int i = 0; i < 3; i++) {
            try {
                MqttSecurity security = new MqttSecurity();
                String host = "ssl://mosquitto:8883";

                MqttConnectOptions connectOptions = new MqttConnectOptions();
                connectOptions.setUserName("scrumptious");
                connectOptions.setPassword(("aardvark").toCharArray());
                connectOptions.setCleanSession(true);
                connectOptions.setSocketFactory(security.getSocketFactory());

                client = new MqttClient(host, "bridgehead-manager", new MemoryPersistence());
                client.setCallback(this);
                client.connect(connectOptions);
                for (String subscription : subscriptions.keySet()) {
                    client.subscribe(subscription, 0);
                }

                sendConnectionStatus(CONNECTED);
                logMessage("Connected to mqtt broker");
                return;
            } catch (MqttException mqttException) {
                String errorMsg;
                Throwable cause = mqttException.getCause();
                if (cause != null) {
                    errorMsg = cause.toString().replace("java.net.", "");
                } else {
                    errorMsg = "[" + mqttException.getReasonCode() + "] " + mqttException.getMessage();
                }
                logMessage("MQTT Connect failed: " + errorMsg);
            } catch (Exception e) {
                logMessage("Error whilst trying to connect to mqtt broker: " + e.getMessage());
            }
            sendConnectionStatus(CONNECTING);
            try {
                sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        sendConnectionStatus(DISCONNECTED);
    }


    public String getConnectionStatus() {
        checkConnection();
        return connectionStatus;
    }

    @Override
    public void connectionLost(Throwable cause) {
        logMessage("Connection lost: " + cause.getMessage());
        sendConnectionStatus(DISCONNECTED);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());

        if (subscriptions.containsKey(topic)) {
            if (Objects.equals(topic, SYS_SUBSCRIPTION_COUNT)) {
                subscriptionCount = payload;
            }

            if (Objects.equals(topic, SYS_CLIENT_COUNT)) {
                clientCount = payload;
            }

            sendWebsocketMessage(subscriptions.get(topic), payload);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    private void sendConnectionStatus(String status) {
        connectionStatus = status;
        sendWebsocketMessage(BROKER_STATUS, status);
    }

    public String getSubscriptionCount() {
        return subscriptionCount;
    }
    public String getClientCount() {
        return clientCount;
    }

    /**********************************************************************************************/
    private volatile MqttClient client;

    private volatile String subscriptionCount = "0";
    private volatile String clientCount = "0";
    private volatile String connectionStatus = CONNECTING;

    private final Map<String, String> subscriptions = Map.of(
            SYS_CLIENT_COUNT, CONNECTED_CLIENT_COUNT,
            SYS_SUBSCRIPTION_COUNT, SUBSCRIPTION_COUNT
    );

    public static final String CONNECTED = "Connected";
    public static final String DISCONNECTED = "Disconnected";
    private static final String CONNECTING = "Connecting...";
    private static final String SYS_CLIENT_COUNT = "$SYS/broker/clients/connected";
    private static final String SYS_SUBSCRIPTION_COUNT = "$SYS/broker/subscriptions/count";

    public static final String BROKER_STATUS = "mqttConnectionStatus";
    public static final String CONNECTED_CLIENT_COUNT = "connectedClients";
    public static final String SUBSCRIPTION_COUNT = "subscriptionCount";
}