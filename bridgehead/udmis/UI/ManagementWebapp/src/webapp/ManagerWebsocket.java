package webapp;

import com.google.gson.JsonObject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;


@ServerEndpoint(value = "/agent")
public class ManagerWebsocket {

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
            LOGGER.severe("Failed to close session: " + e.getMessage());
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        LOGGER.severe("Websocket Error: " + throwable.getMessage());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
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
                        LOGGER.severe("Error sending websocket message: " + e.getMessage());
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
    private static final AtomicBoolean messageScheduled = new AtomicBoolean(false);
    private final static Logger LOGGER = Logger.getLogger(ManagerWebsocket.class.getName());
}