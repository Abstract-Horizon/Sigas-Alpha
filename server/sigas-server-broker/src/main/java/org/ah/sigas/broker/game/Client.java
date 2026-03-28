package org.ah.sigas.broker.game;

import java.io.IOException;
import java.util.LinkedList;

import org.ah.sigas.broker.Broker;
import org.ah.sigas.broker.ClientHandler;
import org.ah.sigas.broker.ClientOutboundHandlerImpl;
import org.ah.sigas.broker.message.ClientDisconnectedMessage;
import org.ah.sigas.broker.message.ClientReconnectedMessage;
import org.ah.sigas.broker.message.HeartBeatMessage;
import org.ah.sigas.broker.message.JoinedMessage;
import org.ah.sigas.broker.message.Message;

public class Client {

    public enum State {
        CONNECTED,
        DISCONNECTED,
        LEFT
    }

    public static enum Direction {
        IN(">"),
        OUT("<");

        private String s;

        Direction(String s) {
            this.s = s;
        }

        public String asStirng() { return s; }
    }

    private Game game;
    private State state = State.CONNECTED;
    private boolean master;
    private String token;
    private String clientId;
    private String alias;
    private final long createdTimestamp = System.currentTimeMillis();
    private long lastActivity;
    private ClientHandler clientInboundHandler;
    private ClientHandler clientOutboundHandler;
    private boolean inboundChannelPresent;
    private boolean outboundChannelPresent;
    private int maxQueueSize;

    // private LinkedList <Message> receivedMessages = new LinkedList<>();
    private LinkedList <Message> messagesToSend = new LinkedList<>();

    public Client(Game game, String token, String clientId, String alias, boolean master, int maxQueueSize) {
        this.game = game;
        this.token = token;
        this.clientId = clientId;
        this.alias = alias;
        this.master = master;
        this.maxQueueSize = maxQueueSize;
        lastActivity = createdTimestamp;
    }

    public Game getGame() { return game; }
    public State getState() { return state; }
    public boolean isMaster() { return master; }
    public String getToken() { return token; }
    public long getCreatedTimestamp() { return createdTimestamp; }
    public long getLastActivity() { return lastActivity; }
    public String getClientId() { return clientId; }
    public String getAlias() { return alias; }

    public ClientHandler getInboundHandler() { return clientInboundHandler; }
    public void setInboundHandler(ClientHandler clientInboundHandler) { this.clientInboundHandler = clientInboundHandler; }

    public ClientHandler getOutboundHandler() { return clientOutboundHandler; }
    public void setOutboundHandler(ClientHandler clientOutboundHandler) { this.clientOutboundHandler = clientOutboundHandler; }

    public boolean hasInboutChannel() { return inboundChannelPresent; }
    public void setHasInboundChannel(boolean hasInboutChannel) { inboundChannelPresent = hasInboutChannel; }

    public boolean hasOutboutChannel() { return outboundChannelPresent; }
    public void setHasOutboundChannel(boolean hasOutboutChannel) { outboundChannelPresent = hasOutboutChannel; }

    public void touch() { lastActivity = System.currentTimeMillis(); }

    public LinkedList<Message> getMessagesToSend() { return messagesToSend; }

    public void receivedMessage(String type, String header, byte[] body) throws IOException {
        touch();
        if (Broker.TRACE) { log(Direction.IN, "Received message '" + type + "'(" + header + "): \n" + new String(body)); }

        if (!master || "HRTB".equals(type)) {
            // Overwrite client ID
            header = header.substring(0, 2) + clientId.substring(0, 2);
        }

        Message message = Message.createMessage(type, header, body);
        if (message instanceof HeartBeatMessage) {
            sendMessage(message);
        } else {
            game.receivedMessage(this, Message.createMessage(type, header, body));
        }
    }

    public void sendMessage(Message message) throws IOException {
        if (state == State.CONNECTED) {
            touch();
            messagesToSend.add(message);
            if (clientOutboundHandler != null && outboundChannelPresent) {
                ((ClientOutboundHandlerImpl)clientOutboundHandler).clientHasMessages();
                if (Broker.TRACE) { log(Direction.IN, "Added new message getting size of " + messagesToSend.size() + " of  " + maxQueueSize); }
            } else {
                if (messagesToSend.size() > maxQueueSize) {
                    clientDisconnected();
                    if (Broker.TRACE) { log(Direction.IN, "No client: Removing messages as over max queue size " + maxQueueSize); }
                } else {
                    if (Broker.TRACE) { log(Direction.IN, "No client: Added new message getting size of " + messagesToSend.size() + " of  " + maxQueueSize); }
                }
            }
        }
    }

    public void newOutboundConnection() throws IOException {
        touch();
        if (!messagesToSend.isEmpty()) {
            String[] messageTypes = messagesToSend.stream().map(m -> m.getType()).toArray(size -> new String[size]);
            if (Broker.TRACE) { log(Direction.IN, "   new connection to existing client - have messages: " + String.join(", ", messageTypes)); }
            ((ClientOutboundHandlerImpl)clientOutboundHandler).clientHasMessages();
        } else if (state == State.DISCONNECTED) {
            clientReconnected();
            if (Broker.TRACE) { log(Direction.IN, "   new connection to existing client - previously removed messages"); }
        } else {
            if (Broker.TRACE) { log(Direction.IN, "   new connection to existing client - no messages and no messages were removed"); }
        }
    }

    public void sendSystemMessageToAll(Message message) throws IOException {
        for (Client destinationClient : game.getClients().values()) {
            destinationClient.sendMessage(message);
        }
    }

    public void clientJoined() throws IOException {
        JoinedMessage joinedMessage = new JoinedMessage(getClientId(), getAlias());
        for (Client c : game.getClients().values()) {
            if (c != this) {
                if (Broker.TRACE) { log(Direction.IN, "Sending JOIN(" + this.getClientId() + ") to " + c.getClientId()); }
                c.sendMessage(joinedMessage);
            }
        }
        // TODO do we need to tell this to the HUB?
    }

    public void clientReconnected() throws IOException {
        state = State.CONNECTED;
        ClientReconnectedMessage clientReconnectedMessage = new ClientReconnectedMessage(clientId);
        if (game.getGameOptions().isPlayerStatusToAll()) {
            sendSystemMessageToAll(clientReconnectedMessage);
        } else {
            game.getMasterClient().sendMessage(clientReconnectedMessage);
        }
        // TODO tell this to the hub
    }

    public void clientDisconnected() throws IOException {
        messagesToSend.clear();
        state = State.DISCONNECTED;

        ClientDisconnectedMessage clientDisconnectedMessage = new ClientDisconnectedMessage(clientId);
        if (game.getGameOptions().isPlayerStatusToAll()) {
            sendSystemMessageToAll(clientDisconnectedMessage);
        } else {
            game.getMasterClient().sendMessage(clientDisconnectedMessage);
        }
        // TODO tell this to the hub
    }

    public void clientLeft() {
        // TODO tell this to the hub
    }

    public void log(Direction direction, String msg) {
        log(direction, msg, false);
    }

    public void log(Direction direction, String msg, boolean error) {
        String prefix = game.getGameId() + direction.asStirng() + clientId + " ";

        if (error) {
            System.err.println(prefix + msg);
        } else {
            System.out.println(prefix + msg);
        }
    }
}
