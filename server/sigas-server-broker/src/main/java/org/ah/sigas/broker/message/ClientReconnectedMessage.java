package org.ah.sigas.broker.message;

public class ClientReconnectedMessage extends ZeroLenMessage {

    public ClientReconnectedMessage(String clientId) {
        this("RECN", "  ", clientId);
    }

    public ClientReconnectedMessage(String type, String flags, String clientId) {
        super(type, flags, clientId);
    }
}
