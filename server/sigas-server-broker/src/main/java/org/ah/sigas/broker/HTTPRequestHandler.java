package org.ah.sigas.broker;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.Map;

public abstract class HTTPRequestHandler implements Handler {

    private static byte[] EMPTY = new byte[0];

    protected ByteBuffer buffer = ByteBuffer.allocate(16384);

    protected Broker broker;

    protected String method;
    protected String path;
    protected String protocol;

    protected Map<String, String> headers = new HashMap<String, String>();

    protected byte[] bytes;
    protected int pos = 0;
    protected int max = 0;

    // Scanner vars
    private int ss = 0;

    private boolean lineParsed = false;
    private boolean parsingError = false;
    private boolean parsingComplete = false;

    private StringBuilder tokenValue = new StringBuilder();
    private String previousHeader;

    public HTTPRequestHandler(Broker broker) {
        this.broker = broker;
    }

    private void parse() {
        while (pos < max && !parsingComplete) {
            while (pos < max && !lineParsed) {
                char ch = (char)bytes[pos];
                pos ++;

                if (ss == 0) {
                    if (ch == 13) {
                        ss = 1;
                    } else {
                        tokenValue.append(ch);
                    }
                } else if (ss == 1) {
                    if (ch == 10) {
                        lineParsed = true;
                        ss = 0;
                    } else {
                        parsingError = true;
                        lineParsed = true;
                        ss = 0;
                    }
                }
            }
            if (parsingError) {
                return;
            }

            if (lineParsed) {
                try {
                    if (tokenValue.length() == 0) {
                        parsingComplete = true;
                        return;
                    }

                    if (method == null) {
                        int firstSpace = tokenValue.indexOf(" ");
                        if (firstSpace < 1) {
                            parsingError = true;
                            return;
                        }

                        method = tokenValue.substring(0, firstSpace);

                        int secondSpace = tokenValue.indexOf(" ", firstSpace + 1);
                        if (secondSpace <=  firstSpace + 1) {
                            parsingError = true;
                            return;
                        }
                        path = tokenValue.substring(firstSpace + 1, secondSpace);
                        protocol = tokenValue.substring(secondSpace + 1);
                    } else {
                        if (tokenValue.charAt(0) == ' ') {
                            if (previousHeader == null) {
                                parsingError = true;
                                return;
                            } else {
                                String previousHeaderValue = headers.get(previousHeader);
                                previousHeaderValue += tokenValue.toString().trim();
                                headers.put(previousHeader, previousHeaderValue);
                            }
                            return;
                        }

                        int i = tokenValue.indexOf(": ");
                        if (i < 1) {
                            parsingError = true;
                            return;
                        }
                        String headerKey = tokenValue.substring(0, i);
                        previousHeader = headerKey;
                        String headerValue = tokenValue.substring(i + 2).trim();
                        headers.put(headerKey.toLowerCase(), headerValue);
                    }
                } finally {
                    tokenValue.delete(0, tokenValue.length());
                    lineParsed = false;
                }
            }
        }
    }

    @Override
    public void read(SelectionKey key, ReadableByteChannel channel) throws IOException {

        int read = channel.read(buffer);
        while (read > 0 && !parsingError && !parsingComplete) {

            buffer.flip();

            bytes = buffer.array();
            pos = 0;
            max = buffer.limit();

            parse();

            buffer.clear();

            if (!parsingComplete) {
                read = channel.read(buffer);
            }
        }

        if (parsingError) {
            System.err.println("Closing connection as got malformed request; '" + method + " " + path + " " + protocol);
            broker.closeChannel(key);
            return;
        }

        if (parsingComplete) {
            if (Broker.DEBUG) { System.out.println("--- Received request: method '" + method + "' on path '" + path + "' with protocol '" + protocol + "' on " + key.channel()); }
            if (Broker.TRACE) {
                System.out.println("    Headers:");
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    System.out.println("      " + header.getKey() + ": " + header.getValue());
                }
            }

            processRequest(key, channel);
        }
    }

    protected abstract void processRequest(SelectionKey key, ReadableByteChannel channel) throws IOException;

    @Override
    public void write(SelectionKey key, WritableByteChannel channel) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
    }

    protected void handleError(SelectionKey key, String errorString) {
        System.err.println(errorString);
        createSimpleResponse(key, 400, "ERROR", errorString + "\n");
    }

    protected void handleError(SelectionKey key, Exception e) {
        StringWriter error = new StringWriter();
        PrintWriter printWriter = new PrintWriter(error);
        printWriter.println("Got exception " + e.getMessage());
        printWriter.println();
        e.printStackTrace(printWriter);
        String errorString = error.toString();
        System.err.print(errorString);

        createSimpleResponse(key, 400, "ERROR", errorString);
    }

    protected void createSimpleResponse(SelectionKey key, int responseCode, String responseMessage) {
        createSimpleResponse(key, responseCode, responseMessage, null);
    }

    protected void createSimpleResponse(SelectionKey key, int responseCode, String responseMessage, String body) {
        key.attach(new SimpleHTTPResponseHandler(
                broker, responseCode, responseMessage,
                new HashMap<String, String>() {{
                    put("Content-Type", "text/plain");
                    if (body != null) {
                        put("Content-Length", Integer.toString(body.length()));
                    }
                }},
                body != null ? body.getBytes() : EMPTY));
        key.interestOps(SelectionKey.OP_WRITE);
    }
}
