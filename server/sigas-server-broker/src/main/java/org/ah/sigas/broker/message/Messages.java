package org.ah.sigas.broker.message;


public class Messages {

    public static void registerAll() {
        Message.registerMessageType("HRTB", HeartBeatMessage.class);
        Message.registerMessageType("JOIN", JoinedMessage.class);
    }
}
