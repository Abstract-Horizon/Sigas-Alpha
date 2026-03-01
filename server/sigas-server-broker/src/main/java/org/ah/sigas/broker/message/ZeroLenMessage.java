package org.ah.sigas.broker.message;

public class ZeroLenMessage extends Message {

    public static byte[] EMPTY_BODY = new byte[0];

    public ZeroLenMessage(String type, String flags, String clientId) {
        super(type, flags, clientId, EMPTY_BODY);
    }

}
