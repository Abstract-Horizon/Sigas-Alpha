package org.ah.sigas.broker;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.ah.sigas.broker.game.Game;

public class Broker {

    public static boolean INFO = true;
    public static boolean DEBUG = true;

    private int serverPort;
    private int internalPort;
    private URI hubURI;

    private ServerSocketChannel serverChannel;
    private ServerSocketChannel internalChannel;
    private Selector selector;

    private int connectionNum;

    private Map<String, Game> games = new HashMap<>();

    private boolean doStop = false;

    public Broker(int serverPort, int internalPort, URI hubURI) {
        this.serverPort = serverPort;
        this.internalPort = internalPort;
        this.hubURI = hubURI;
    }

    private void init() throws IOException {
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress((InetAddress)null, serverPort));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        internalChannel = ServerSocketChannel.open();
        internalChannel.configureBlocking(false);
        internalChannel.socket().bind(new InetSocketAddress((InetAddress)null, internalPort));
        internalChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    public Map<String, Game> getGames() { return games; }
    public URI getHubURI() { return hubURI; }

    public void loop() {
        try {
            init();
        } catch (Exception e) {
            System.err.println("Failed to start server; " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        while (!doStop) {
            try {

                selector.select(200);
                Set<SelectionKey> keys = selector.selectedKeys();

                Iterator<SelectionKey> keyIterator = keys.iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();
                    try {
                        if (key.isValid()) {
                            if (key.isAcceptable()) {
                                accept(key);
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
            } catch (Exception e) {
                System.err.println("Got exception " + e.getMessage());
                e.printStackTrace();
            }
        }
        for (SelectionKey key : selector.keys()) {
            try {
                key.channel().close();
            } catch (IOException ignore) { }
        }
    }

    public void stop() {
        doStop = true;
    }

    private void accept(SelectionKey selectedKey) throws IOException {
        if (selectedKey.channel() == serverChannel) {
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

            key.attach(new HTTPServerRequestHandler(this));
        } else {
            SocketChannel clientChannel = internalChannel.accept();
            if (clientChannel == null) {
                System.err.println("Internal channel cannot be accepted");
                return;
            }

            clientChannel.configureBlocking(false);
            SelectionKey key = clientChannel.register(selector, SelectionKey.OP_READ); // Expecting other side to send some data first
            if (DEBUG) { System.out.println("Accepting channel " + clientChannel); }

            connectionNum++;
            if (DEBUG) { System.out.println("Got new connection handler for channel: " + clientChannel + ", connection #: " + connectionNum); }

            key.attach(new HTTPInternalRequestHandler(this));
        }

    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();

        Handler handler = (Handler)key.attachment();
        if (handler == null) {
            throw new IOException("Read: Handler is missing for the channel: " + key.channel());
        }

        handler.read(key, clientChannel);
    }

    private void write(SelectionKey key) throws IOException {
        Handler handler = (Handler)key.attachment();
        if (handler == null) {
            throw new IOException("Write: Handler is missing for the channel: " + key.channel());
        }

        SocketChannel clientChannel = (SocketChannel) key.channel();
        handler.write(key, clientChannel);
    }

    public void closeChannel(SelectionKey key) {
        connectionNum--;

        SocketChannel channel = (SocketChannel) key.channel();
        key.cancel();
        if (DEBUG) { System.out.println("Closing connection for channel: " + channel + ", active connections: " + connectionNum); }

        Handler handler = (Handler)key.attachment();
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
