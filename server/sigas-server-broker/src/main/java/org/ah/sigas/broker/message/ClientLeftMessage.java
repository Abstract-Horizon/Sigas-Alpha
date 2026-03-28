package org.ah.sigas.broker.message;

public class ClientLeftMessage extends ZeroLenMessage {

    public ClientLeftMessage(String clientId) {
        this("LEFT", "  ", clientId);
    }

    public ClientLeftMessage(String type, String flags, String clientId) {
        super(type, flags, clientId);
    }
}
