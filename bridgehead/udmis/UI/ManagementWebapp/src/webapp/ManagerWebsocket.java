package webapp;

import com.google.gson.JsonObject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static webapp.ManagerServlet.logMessage;


@ServerEndpoint(value = "/agent")
public class ManagerWebsocket {

    private static final Logger log = LoggerFactory.getLogger(ManagerWebsocket.class);

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        sessions.add(session);
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        sessions.remove(session);
        try {
            session.close();
        } catch (IOException e) {
            logMessage("Failed to close session: " + e.getMessage());
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        logMessage("Websocket Error: " + throwable.getMessage());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        logMessage("Message received: " + message);
    }

    public static void sendWebsocketMessage(String subject, String data){

        JsonObject message = new JsonObject();
        message.addProperty("subject", subject);
        message.addProperty("data", data);

        messageQueue.add(message);
        if (messageScheduled.compareAndSet(false, true)){
            messageScheduler.schedule(send, 3, TimeUnit.SECONDS);
        }
    }

    private static final Runnable send = new Runnable() {
        @Override
        public void run() {
            Session session = sessions.stream().findFirst().orElse(null);

            while(session != null && !messageQueue.isEmpty()){
                JsonObject message = messageQueue.poll();

                if (message != null){
                    try {
                        session.getBasicRemote().sendText(message.toString());
                    } catch (IOException e) {
                        logMessage("Error sending websocket message: " + e.getMessage());
                        messageQueue.offer(message);
                        session = null;
                        break;
                    }
                }
            }

            if(!messageQueue.isEmpty()){
                messageScheduler.schedule(this, 100, TimeUnit.MILLISECONDS);
            }else{
                messageScheduled.set(false);
            }
        }
    };

    public static final Set<Session> sessions = new CopyOnWriteArraySet<>();
    private static final Queue<JsonObject> messageQueue = new ConcurrentLinkedQueue<>();
    private static final ScheduledExecutorService messageScheduler = Executors.newSingleThreadScheduledExecutor();
    private static AtomicBoolean messageScheduled = new AtomicBoolean(false);;
}