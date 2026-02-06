package org.ah.sigas;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.Map;

public class HTTPResponseHandler implements Handler {


    private ByteBuffer buffer = ByteBuffer.allocate(16384);

    private byte SPACE = 32;
    private byte[] CRLF = new byte[] {13, 10};
    private byte[] HEADER_SEPARATOR = new byte[] {':', ' '};

    private Broker broker;

    private int connectionNo;
    private String protocol = "HTTP/1.1";
    private int responseCode;
    private String responseMsg;

    private Map<String, String> headers;

    private byte[] content;
    private int ptr = 0;
    private boolean headersSent = false;


    public HTTPResponseHandler(Broker broker, int connectionNo, int responseCode, String responseMsg, Map<String, String> headers, byte[] content) {
        this.broker = broker;
        this.connectionNo = connectionNo;
        this.responseCode = responseCode;
        this.responseMsg = responseMsg;
        this.headers = headers;
        if (this.headers == null) {
            this.headers = new HashMap<String, String>();
        }
        this.content = content;
        if (!this.headers.containsKey("Content-Length")) {
            this.headers.put("Content-Length", Integer.toString(content.length));
        }
    }

    @Override
    public void read(SelectionKey key, ReadableByteChannel channel) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void write(SelectionKey key, WritableByteChannel channel) throws IOException {
        if (ptr < content.length || !headersSent) {
            if (!headersSent) {
                buffer.put(protocol.getBytes());
                buffer.put(SPACE);
                buffer.put(Integer.toString(responseCode).getBytes());
                buffer.put(SPACE);
                buffer.put(responseMsg.getBytes());
                buffer.put(CRLF);

                for (Map.Entry<String, String> header : headers.entrySet()) {
                    buffer.put(header.getKey().getBytes());
                    buffer.put(HEADER_SEPARATOR);
                    buffer.put(header.getValue().getBytes());
                    buffer.put(CRLF);
                }
                buffer.put(CRLF);
                headersSent = true;
            }

            buffer.put(content);
            ptr += content.length;

            buffer.flip();

            channel.write(buffer);
            buffer.clear();
        } else {
            broker.closeChannel(key);
        }
    }

    @Override
    public void close() {
    }
}
