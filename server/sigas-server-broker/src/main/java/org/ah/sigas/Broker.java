package org.ah.sigas;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class Broker {

    public static boolean INFO = true;
    public static boolean DEBUG = true;

    private int port;

    private ServerSocketChannel serverChannel;
    private Selector selector;

    private int connectionNum;


    public Broker(int port) {
        this.port = port;
    }

    private void init() throws IOException {
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress((InetAddress)null, port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    public void loop() {
        try {
            init();
        } catch (Exception e) {
            System.err.println("Failed to start server; " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        while (true) {
            try {

                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();

                Iterator<SelectionKey> keyIterator = keys.iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();
                    try {
                        if (key.isValid()) {
                            if (key.isAcceptable()) {
                                accept();
                            } else if (key.isReadable()) {
                                read(key);
                            } else if (key.isWritable()) {
                                write(key);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Closing channel: error while handling selection key. Channel: " + key.channel() + "; " + e.getMessage());
                        e.printStackTrace();
                        closeChannel(key);
                    }
                }

                Thread.sleep(100);
            } catch (Exception e) {
                System.err.println("Got exception " + e.getMessage());
                e.printStackTrace();
            }
        }
    }



    private void accept() throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel == null) {
            System.err.println("Server channel cannot be accepted");
            return;
        }

        clientChannel.configureBlocking(false);
        SelectionKey key = clientChannel.register(selector, SelectionKey.OP_READ); // Expecting other side to send some data first
        if (DEBUG) { System.out.println("Accepting channel " + clientChannel); }

        connectionNum++;
        if (DEBUG) { System.out.println("Got new connection handler for channel: " + clientChannel + ", connection #: " + connectionNum); }

        key.attach(new HTTPRequestHandler(connectionNum));

    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();

        HTTPRequestHandler handler = (HTTPRequestHandler)key.attachment();
        if (handler == null) {
            throw new IOException("Read: Handler is missing for the channel: " + key.channel());
        }

        if (handler.read(clientChannel)) {
            key.interestOps(SelectionKey.OP_WRITE);
        }
    }

    private void write(SelectionKey key) throws IOException {
        HTTPRequestHandler handler = (HTTPRequestHandler)key.attachment();
        if (handler == null) {
            throw new IOException("Write: Handler is missing for the channel: " + key.channel());
        }

        SocketChannel clientChannel = (SocketChannel) key.channel();
        handler.write(clientChannel);

        if (handler.isCompleted()) {
            closeChannel(key);
        }
    }

    private void closeChannel(SelectionKey key) {
        connectionNum--;

        SocketChannel channel = (SocketChannel) key.channel();
        key.cancel();
        if (DEBUG) { System.out.println("Closing connection for channel: " + channel + ", active connections: " + connectionNum); }

        HTTPRequestHandler handler = (HTTPRequestHandler)key.attachment();
        if (handler != null) {
            handler.close();
        }

        try {
            channel.close();
        } catch (IOException e) {
            System.err.println("Error during closing channel: " + channel + "; " + e.getMessage());
        }
    }
}
