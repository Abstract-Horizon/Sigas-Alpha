package org.ah.sigas.broker;

import static org.ah.sigas.broker.SimpleHTTPResponseHandler.CRLF;

import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;

import org.ah.sigas.broker.game.Client;

public class ClientInboundHandlerImpl extends BaseClientHandler {


    protected ByteBuffer buffer = ByteBuffer.allocate(16384);

    private byte[] bytes;
    private int pos = 0;
    private int max = 0;

    private int cs = 0;  // chunk state
    private int chunkLen = 0;

    private int ms = 0; // message state
    private int msgLen = 0;
    private boolean error = false;
    private boolean gracefulEnd = false;

    private byte[] messageType = new byte[4];
    private byte[] messageBytes;
    private int ptr = 0;

    public ClientInboundHandlerImpl(Broker broker, Client client) {
        super(broker, client);
    }

    public ByteBuffer getBuffer() { return buffer; }

    @Override
    public void open(SelectionKey associatedKey) {
        super.open(associatedKey);
        buffer.clear();

        pos = 0;
        max = 0;

        cs = 0;
        chunkLen = 0;

        ms = 0;
        msgLen = 0;
        error = false;
        gracefulEnd = false;

        ptr = 0;
    }

    @Override
    public void read(SelectionKey key, ReadableByteChannel channel) throws IOException {
        try {
            int read = channel.read(buffer);
            processInput(key, channel, read);
        } catch (SocketException e) {
            if (!channel.isOpen()) {
                throw e;
            } else {
                broker.closeChannel(key);
            }
        }
    }

    private boolean readMessage() throws IOException {
        int l = msgLen - ptr;
        if (l > max - pos) {
            l = max - pos;
        }
        if (l > chunkLen) {
            l = chunkLen;
        }
        System.arraycopy(bytes, pos, messageBytes, ptr, l);

        pos += l;
        ptr += l;
        chunkLen -= l;

        if (ptr >= msgLen) {
            client.receivedMessage(new String(messageType), messageBytes);
            return true;
        }
        return false;
    }

    private void parseMessage() throws IOException {
        while (pos < max && chunkLen > 0) {
            byte b = bytes[pos];
            if (ms != 8) {
                pos++;
                chunkLen--;
            }

            if (ms == 0) {
                messageType[0] = b;
                ms = 1;
            } else if (ms == 1) {
                messageType[1] = b;
                ms = 2;
            } else if (ms == 2) {
                messageType[2] = b;
                ms = 3;
            } else if (ms == 3) {
                messageType[3] = b;
                ms = 4;
            } else if (ms == 4) {
                ptr = 0;
                msgLen = b;
                ms = 5;
            } else if (ms == 5) {
                msgLen = msgLen * 8 + b;
                ms = 6;
            } else if (ms == 6) {
                msgLen = msgLen * 8 + b;
                ms = 7;
            } else if (ms == 7) {
                msgLen = msgLen * 8 + b;

                messageBytes = new byte[msgLen];

                if (readMessage()) {
                    ms = 0;
                } else {
                    ms = 8;
                }
            } else if (ms == 8) {
                if (readMessage()) {
                    ms = 0;
                }
            }
        }
    }

    private void parse() throws IOException {
        while (pos < max && !error) {
            byte b = bytes[pos];
            pos++;
            if (cs == 0) {
                // beginning of chunk
                if (b >= '0' && b <= '9') {
                    chunkLen = chunkLen * 16 + (b - '0');
                } else if (b >= 'A' && b <= 'F') {
                    chunkLen = chunkLen * 16 + (b - 'A' + 10);
                } else if (b >= 'a' && b <= 'f') {
                    chunkLen = chunkLen * 16 + (b - 'a' + 10);
                } else if (b == 13) {
                    cs = 1;
                } else  {
                    if (Broker.DEBUG) { log("Wrong input in Chunked-Encoding size, expected '0-9A-F' or CR; got '" + Integer.toString(b) + "'", true); }
                    error = true;
                }
            } else if (cs == 1) {
                if (b == 10) {
                    if (Broker.TRACE) { log("Got chunk of " + chunkLen + " bytes"); }
                    if (chunkLen == 0) {
                        error = true;
                        gracefulEnd = true;
                    } else {
                        parseMessage();
                        if (chunkLen == 0) {
                            cs = 3;
                        } else {
                            cs = 2;
                        }
                    }
                } else {
                    if (Broker.DEBUG) { log("Wrong input after CR in Chunked-Encoding size; expected LF and got '" + Integer.toString(b) + "'", true); }
                    error = true;
                }
            } else if (cs == 2) {
                parseMessage();

                if (chunkLen == 0) {
                    cs = 3;
                }
            } else if (cs == 3) {
                if (b == 13) {
                    cs = 4;
                } else {
                    if (Broker.DEBUG) { log("Expected chunk end (LF) but got '" + Integer.toString(b) + "'", true); }
                    error = true;
                }
            } else if (cs == 4) {
                if (b == 10) {
                    if (Broker.TRACE) { log("Completed chunk"); }
                    cs = 0;
                } else {
                    if (Broker.DEBUG) { log("Expected chunk (CR) end but got '" + Integer.toString(b) + "'", true); }
                    error = true;
                }
            }
        }
    }

    public void processInput(SelectionKey key, ReadableByteChannel channel, int initalReadCount) throws IOException {
        int read = initalReadCount;
        while (read > 0) {

            buffer.flip();

            bytes = buffer.array();
            pos = 0;
            max = buffer.limit();

            parse();

            buffer.clear();
            if (error) {
                if (gracefulEnd) {
                    buffer.put("HTTP/1.1 204 OK".getBytes()).put(CRLF);
                    buffer.put(CRLF);

                    buffer.flip();
                    ((WritableByteChannel)channel).write(buffer);
                } else {
                    broker.closeChannel(key);
                }
                return;
            }

            read = channel.read(buffer);
        }
    }
}
