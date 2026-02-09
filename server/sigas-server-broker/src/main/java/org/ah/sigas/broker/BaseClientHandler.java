package org.ah.sigas.broker;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;

import org.ah.sigas.broker.game.Client;

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

    protected void println(String msg) {
        println(msg, false);
    }

    protected void println(String msg, boolean error) {
        String prefix = client.getGame().getGameId() + ":" + client.getToken() + " ";

        if (error) {
            System.err.println(prefix + msg);
        } else {
            System.out.println(prefix + msg);
        }
    }
}
