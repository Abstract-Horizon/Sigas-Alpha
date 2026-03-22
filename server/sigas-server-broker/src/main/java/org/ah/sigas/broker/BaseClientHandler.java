package org.ah.sigas.broker;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;

import org.ah.sigas.broker.game.Client;
import org.ah.sigas.broker.game.Client.Direction;

public class BaseClientHandler implements ClientHandler {

    protected Broker broker;
    protected Client client;
    protected SelectionKey associatedKey;
    protected boolean open = false;

    public BaseClientHandler(Broker broker, Client client) {
        this.broker = broker;
        this.client = client;
    }

    @Override
    public SelectionKey getAssociatedKey() { return associatedKey; }

    public Client getClient() { return client; }

    @Override
    public void open(SelectionKey associatedKey) {
        this.associatedKey = associatedKey;
        open = true;
    }

    @Override
    public void close() {
        open = false;
    }

    @Override
    public void read(SelectionKey key, ReadableByteChannel channel) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void write(SelectionKey key, WritableByteChannel channel) throws IOException {
        throw new UnsupportedOperationException();
    }

    protected void log(Direction direction, String msg) {
        client.log(direction, msg, false);
    }

    protected void log(Direction direction, String msg, boolean error) {
        client.log(direction, msg, error);
    }

}
