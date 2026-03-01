package org.ah.sigas.broker.message;

public class HeartBeatMessage extends Message {

    public HeartBeatMessage(int sequence, String flags, String clientId) {
        super("HRTB", flags, clientId, new byte[] {(byte)(sequence & 0xFF), (byte)((sequence >> 8) & 0xFF)});
    }

    public HeartBeatMessage(String type, String flags, String clientId, int sequence) {
        super(type, flags, clientId, new byte[] {(byte)(sequence & 0xFF), (byte)((sequence >> 8) & 0xFF)});
    }

    public int getSequence() {
        int high = body[1] >= 0 ? body[1] : 256 + body[1];
        int low = body[0] >= 0 ? body[0] : 256 + body[0];

        return low | (high << 8);
    }
}
