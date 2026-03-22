package org.ah.sigas.broker;

import static org.ah.sigas.broker.SimpleHTTPResponseHandler.CRLF;

import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;

import org.ah.sigas.broker.game.Client;
import org.ah.sigas.broker.game.Client.Direction;
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
    public void read(SelectionKey key, ReadableByteChannel channel) throws IOException {
        if (!channel.isOpen()) {
            log(Direction.OUT, "Got read and channel is closed");
        } else {
            int read = channel.read(buffer);
            if (read > 0) {
                log(Direction.OUT, "Got read on inbound channel. It is invalid state, closing...");
            }
        }
        broker.closeChannel(key);
        client.setHasOutboundChannel(false);
   }

    @Override
    public void write(SelectionKey key, WritableByteChannel channel) throws IOException {
        try {
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
                    if (Broker.DEBUG) { log(Direction.OUT, "Sent headers out - no messages"); }
                    key.interestOps(SelectionKey.OP_READ);
                } else {
                    if (Broker.DEBUG) { log(Direction.OUT, "Sent headers out - next messages"); }
                }
                return;
            }

            Message message = client.getMessagesToSend().peekFirst(); // First check if message is available
            if (message != null) {
                try {
                    byte[] body = message.getBody();
                    buffer.clear();
                    buffer.put(Integer.toString(body.length + 12, 16).getBytes()).put(CRLF);
                    buffer.put(message.getType().getBytes());
                    buffer.put(message.getFlags().getBytes());
                    buffer.put(message.getClientId().getBytes());
                    buffer.putInt(body.length);
                    buffer.put(body);
                    buffer.put(CRLF);
                    buffer.flip();

                    channel.write(buffer);

                    client.getMessagesToSend().pollFirst(); // remove it if successfully sent

                    if (Broker.DEBUG) { log(Direction.OUT, "Sent message " + message.getType() + " out."); }

                    message = client.getMessagesToSend().peekFirst();
                    if (message != null) {
                        key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
                        return;
                    }
                } catch (IOException e) {
                    broker.closeChannel(key);
                    client.setHasOutboundChannel(false);
                    return;
                }
            } else {
                if (Broker.DEBUG) { log(Direction.OUT, "Asked to send but no messages."); }
            }
            key.interestOps(SelectionKey.OP_READ);
        } catch (SocketException e) {
            // TODO do we do it in any case or only when closing channel
            if (!channel.isOpen()) {
                // TODO why this - when would this happen?
                throw e;
            } else {
                broker.closeChannel(key);
                client.setHasOutboundChannel(false);
            }
        }
    }

    @Override
    public void open(SelectionKey associatedKey) {
        super.open(associatedKey);
        associatedKey.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        buffer.clear();
        headersSent = false;
    }

    public void clientHasMessages() throws IOException {
        if (Broker.TRACE) { log(Direction.OUT, "   Have messages ready"); }
        write(associatedKey, ((WritableByteChannel)associatedKey.channel()));
    }
}
