package org.ah.sigas.broker.message;

public class Message {

    private String type;
    private byte[] bytes;

    public Message(String type, byte[] bytes) {
        this.type = type;
        this.bytes = bytes;
    }

    public String getType() { return type; }
    public byte[] getBytes() { return bytes; }

}
