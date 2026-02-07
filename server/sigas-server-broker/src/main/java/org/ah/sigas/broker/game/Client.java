package org.ah.sigas.broker.game;

public class Client {

    private boolean master;
    private String token;
    private final long createdTimestamp = System.currentTimeMillis();

    public Client(String token, boolean master) {
        this.token = token;
        this.master = master;
    }

    public boolean isMaster() { return master; }
    public String getToken() { return token; }
    public long getCreatedTimestamp() { return createdTimestamp; }
}
