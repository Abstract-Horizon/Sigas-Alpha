package org.ah.sigas.broker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;

import org.ah.sigas.broker.game.Client;

public class ClientOutboundHandlerImpl extends BaseClientHandler {

    protected ByteBuffer buffer = ByteBuffer.allocate(16384);

    public ClientOutboundHandlerImpl(Broker broker, Client client) {
        super(broker, client);
    }

    public SelectionKey getAssociatedKey() { return associatedKey; }

    public void setAssociatedKey(SelectionKey associatedKey) { this.associatedKey = associatedKey; }


    public ByteBuffer getBuffer() { return buffer; }

    @Override
    public void write(SelectionKey key, WritableByteChannel channel) throws IOException {
        // TODO Auto-generated method stub

    }
}
