package org.ah.sigas.broker.message;

public class ClientDisconnectedMessage extends ZeroLenMessage {

    public ClientDisconnectedMessage(String clientId) {
        this("DISC", "  ", clientId);
    }

    public ClientDisconnectedMessage(String type, String flags, String clientId) {
        super(type, flags, clientId);
    }
}
