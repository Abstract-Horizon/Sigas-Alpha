package org.ah.sigas.broker;

import static org.ah.sigas.broker.SimpleHTTPResponseHandler.CRLF;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;

import org.ah.sigas.broker.game.Client;
import org.ah.sigas.broker.message.Message;

public class ClientOutboundHandlerImpl extends BaseClientHandler {

    protected ByteBuffer buffer = ByteBuffer.allocate(16384);

    private boolean headersSent = false;

    public ClientOutboundHandlerImpl(Broker broker, Client client) {
        super(broker, client);
    }

    public SelectionKey getAssociatedKey() { return associatedKey; }

    public void setAssociatedKey(SelectionKey associatedKey) { this.associatedKey = associatedKey; }


    public ByteBuffer getBuffer() { return buffer; }

    @Override
    public void write(SelectionKey key, WritableByteChannel channel) throws IOException {
        if (!headersSent) {
            buffer.clear();
            buffer.put("HTTP/1.1 200 OK".getBytes()).put(CRLF);
            buffer.put("Transfer-Encoding: chunked".getBytes()).put(CRLF);
            buffer.put(CRLF);

            buffer.flip();
            channel.write(buffer);

            headersSent = true;

            Message message = client.getMessagesToSend().peekFirst();
            if (message == null) {
                if (Broker.DEBUG) { log("Sent headers out - no messages"); }
                key.interestOps(0);
            } else {
                if (Broker.DEBUG) { log("Sent headers out - next messages"); }
            }
            return;
        }

        Message message = client.getMessagesToSend().pollFirst();
        if (message != null) {
            byte[] body = message.getBody();
            buffer.clear();
            buffer.put(Integer.toString(body.length + 8, 16).getBytes()).put(CRLF);
            buffer.put(message.getType().getBytes());
            buffer.putInt(body.length);
            buffer.put(body);
            buffer.put(CRLF);
            buffer.flip();

            channel.write(buffer);

            if (Broker.DEBUG) { log("Sent message " + message.getType() + " out."); }

            message = client.getMessagesToSend().peekFirst();
            if (message != null) {
                return;
            }
        } else {
            if (Broker.DEBUG) { log("Asked to send but no messages."); }
        }
        key.interestOps(0);
    }

    @Override
    public void open(SelectionKey associatedKey) {
        super.open(associatedKey);
        associatedKey.interestOps(SelectionKey.OP_WRITE);
        buffer.clear();
        headersSent = false;
    }

    public void clientHasMessages() throws IOException {
        if (Broker.TRACE) { log("   Have messages ready"); }
        write(associatedKey, ((WritableByteChannel)associatedKey.channel()));
    }
}
