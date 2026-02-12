package org.ah.sigas.broker.game;

import java.io.IOException;
import java.util.LinkedList;

import org.ah.sigas.broker.Broker;
import org.ah.sigas.broker.ClientHandler;
import org.ah.sigas.broker.ClientOutboundHandlerImpl;
import org.ah.sigas.broker.message.Message;

public class Client {

    private Game game;
    private boolean master;
    private String token;
    private String clientId;
    private final long createdTimestamp = System.currentTimeMillis();
    private long lastActivity;
    private ClientHandler clientInboundHandler;
    private ClientHandler clientOutboundHandler;

    // private LinkedList <Message> receivedMessages = new LinkedList<>();
    private LinkedList <Message> messagesToSend = new LinkedList<>();

    public Client(Game game, String token, String clientId, boolean master) {
        this.game = game;
        this.token = token;
        this.clientId = clientId;
        this.master = master;
        lastActivity = createdTimestamp;
    }

    public Game getGame() { return game; }
    public boolean isMaster() { return master; }
    public String getToken() { return token; }
    public long getCreatedTimestamp() { return createdTimestamp; }
    public long getLastActivity() { return lastActivity; }
    public String getClientId() { return clientId; }

    public ClientHandler getInboundHandler() { return clientInboundHandler; }
    public void setInboundHandler(ClientHandler clientInboundHandler) { this.clientInboundHandler = clientInboundHandler; }

    public ClientHandler getOutboundHandler() { return clientOutboundHandler; }
    public void setOutboundHandler(ClientHandler clientOutboundHandler) { this.clientOutboundHandler = clientOutboundHandler; }

    public void touch() { lastActivity = System.currentTimeMillis(); }

    public LinkedList<Message> getMessagesToSend() { return messagesToSend; }

    public void receivedMessage(String type, byte[] body) throws IOException {
        if (body.length < 2) {
            if (Broker.INFO) { log("ERROR: Received message from client btu size is less than 2!"); }
            return;
        }
        if (Broker.TRACE) { log("Received message '" + type + "': \n" + new String(body)); }

        if (!master) {
            body[0] = (byte)clientId.charAt(0);
            body[1] = (byte)clientId.charAt(1);
        }

        game.receivedMessage(this, Message.createMessage(type, body));
    }

    public void sendMessage(Message message) throws IOException {
        if (clientOutboundHandler != null) {
            messagesToSend.add(message);
            ((ClientOutboundHandlerImpl)clientOutboundHandler).clientHasMessages();
        }
    }

    public void log(String msg) {
        log(msg, false);
    }

    public void log(String msg, boolean error) {
        String prefix = game.getGameId() + ":" + token + " ";

        if (error) {
            System.err.println(prefix + msg);
        } else {
            System.out.println(prefix + msg);
        }
    }
}
