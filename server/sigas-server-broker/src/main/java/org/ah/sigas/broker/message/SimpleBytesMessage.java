package org.ah.sigas.broker.message;

public class SimpleBytesMessage extends Message {

    public SimpleBytesMessage(String type, String flags, String clientId, byte[] body) {
        super(type, flags, clientId, body);
    }

}
