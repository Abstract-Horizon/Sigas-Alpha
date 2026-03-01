package org.ah.sigas.broker.message;

public class PrivateMsgMessage extends Message {

    public PrivateMsgMessage(String clientId, int sequence) {
        super("PMSG", "  ", clientId, new byte[] {(byte)(sequence & 0xFF), (byte)((sequence >> 8) & 0xFF)});
    }

    public PrivateMsgMessage(String type, String flags, String clientId, int sequence) {
        super(type, flags, clientId, new byte[] {(byte)(sequence & 0xFF), (byte)((sequence >> 8) & 0xFF)});
    }
}
