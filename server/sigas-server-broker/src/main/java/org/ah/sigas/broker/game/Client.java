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

    public void receivedMessage(String type, String header, byte[] body) throws IOException {
        if (Broker.TRACE) { log("Received message '" + type + "'(" + header + "): \n" + new String(body)); }

        if (!master) {
            // Overwrite client ID
            header = header.substring(0, 2) + clientId.substring(0, 2);
        }

        game.receivedMessage(this, Message.createMessage(type, header, body));
    }

    public void sendMessage(Message message) throws IOException {
        messagesToSend.add(message);
        if (clientOutboundHandler != null) {
            ((ClientOutboundHandlerImpl)clientOutboundHandler).clientHasMessages();
        // } else {
        //     log("Got message " + message.getType() + " but no clientOutboundHandler");
        }
    }

    public void log(String msg) {
        log(msg, false);
    }

    public void log(String msg, boolean error) {
        String prefix = game.getGameId() + ":" + clientId + " ";

        if (error) {
            System.err.println(prefix + msg);
        } else {
            System.out.println(prefix + msg);
        }
    }
}
