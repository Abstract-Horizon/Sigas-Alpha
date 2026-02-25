package org.ah.sigas.broker.message;

public class SimpleBytesMessage extends Message {

    public SimpleBytesMessage(String type, String header, byte[] body) {
        super(type, header, body);
    }

}
