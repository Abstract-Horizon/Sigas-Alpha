package org.ah.sigas.broker.message;

public class SimpleBytesMessage extends Message {

    public SimpleBytesMessage(String type, byte[] body) {
        super(type, body);
    }

}
