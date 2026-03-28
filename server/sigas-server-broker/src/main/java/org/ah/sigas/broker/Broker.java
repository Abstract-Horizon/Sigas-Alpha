package org.ah.sigas.broker;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.ah.sigas.broker.game.Client;
import org.ah.sigas.broker.game.Client.Direction;
import org.ah.sigas.broker.game.Game;

public class Broker {

    public static long CHECK_FOR_TIMEOUT = 500;

    public static boolean INFO = true;
    public static boolean DEBUG = true;
    public static boolean TRACE = true;
    public static boolean HEADERS = false;

    private int serverPort;
    private int internalPort;
    private URI hubURI;

    private ServerSocketChannel serverChannel;
    private ServerSocketChannel internalChannel;
    private Selector selector;

    private Map<String, Game> games = new HashMap<>();

    private boolean doStop = false;

    private SelectionKey serverKey;
    private SelectionKey internalKey;

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
        serverKey = serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        serverKey.attach("Server Key");

        internalChannel = ServerSocketChannel.open();
        internalChannel.configureBlocking(false);
        internalChannel.socket().bind(new InetSocketAddress((InetAddress)null, internalPort));
        internalKey = internalChannel.register(selector, SelectionKey.OP_ACCEPT);
        internalKey.attach("Internal Key");
    }

    public Map<String, Game> getGames() { return games; }
    public URI getHubURI() { return hubURI; }

    public void loop() {
        try {
            init();
        } catch (Throwable e) {
            System.err.println("Failed to start server; " + e.getMessage());
            e.printStackTrace();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            System.exit(1);
        }

        long lastCheckedForTimeout = System.currentTimeMillis();
        while (!doStop) {
            try {
                selector.select(CHECK_FOR_TIMEOUT);
                Set<SelectionKey> keys = selector.selectedKeys();

                if (!keys.isEmpty()) {
                    Iterator<SelectionKey> keyIterator = keys.iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        keyIterator.remove();
                        try {
                            if (key.isValid()) {
                                if (key.isAcceptable()) {
                                    accept(key);
                                } else if (key.isConnectable()) {
                                    System.out.println("Key " + key + " got connectable selection");

                                } else if (key.isReadable()) {
                                    read(key);
                                } else if (key.isWritable()) {
                                    write(key);
                                }
                            }
                        } catch (Throwable e) {
                            System.err.println("*** Closing channel: error while handling selection key. Channel: " + key.channel() + "; " + e.getClass().getCanonicalName() + "(" + (e.getMessage() != null ? e.getMessage() : "") + ")");
                            e.printStackTrace();
                            closeChannel(key);
                        }
                    }
                }
                long now = System.currentTimeMillis();
                if (now - lastCheckedForTimeout > CHECK_FOR_TIMEOUT) {
                    for (Game game : games.values()) {
                        game.checkForTimeout(now);
                    }
                }
            } catch (Throwable e) {
                System.err.println("Got exception in broker loop " + e.getMessage());
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
            if (DEBUG) { System.out.println("*** Accepting channel " + logChannel(clientChannel)); }

            key.attach(new HTTPServerRequestHandler(this));
        } else if (selectedKey.channel() == internalChannel) {
            SocketChannel clientChannel = internalChannel.accept();
            if (clientChannel == null) {
                System.err.println("Internal channel cannot be accepted");
                return;
            }

            clientChannel.configureBlocking(false);
            SelectionKey key = clientChannel.register(selector, SelectionKey.OP_READ); // Expecting other side to send some data first
            if (DEBUG) { System.out.println("*** Accepting channel " + logChannel(clientChannel)); }

            key.attach(new HTTPInternalRequestHandler(this));
        } else {
            System.err.println("Selected key " + selectedKey + " is not known");
        }

    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();

        Handler handler = (Handler)key.attachment();
        if (handler == null) {
            throw new IOException("Read: Handler is missing for the channel: " + logChannel((SocketChannel)key.channel()));
        }

        handler.read(key, clientChannel);
    }

    private void write(SelectionKey key) throws IOException {
        Handler handler = (Handler)key.attachment();
        if (handler == null) {
            throw new IOException("Write: Handler is missing for the channel: " + logChannel((SocketChannel)key.channel()));
        }

        SocketChannel clientChannel = (SocketChannel) key.channel();
        handler.write(key, clientChannel);
    }

    public void closeChannel(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        key.cancel();

        String prefix = "***";
        Client client = null;
        Direction direction = null;

        Object attachment = key.attachment();
        if (attachment instanceof ClientInboundHandlerImpl) {
            ClientInboundHandlerImpl handler = (ClientInboundHandlerImpl)attachment;
            client = handler.getClient();
            direction = Direction.IN;
        } else if (attachment instanceof ClientOutboundHandlerImpl) {
            ClientOutboundHandlerImpl handler = (ClientOutboundHandlerImpl)attachment;
            client = handler.getClient();
            direction = Direction.OUT;
        }
        if (client != null && direction != null) {
            prefix = client.getGame().getGameId() + direction.asStirng() + client.getClientId();
        }

        if (DEBUG) { System.out.println(prefix + " Closing connection for channel: " + logChannel(channel)); }

        Handler handler = (Handler)key.attachment();
        if (handler != null) {
            handler.close();
        }

        try {
            channel.close();
        } catch (SocketException ignore) {
            // ignore
        } catch (IOException e) {
            System.err.println("*** Error during closing channel: " + logChannel(channel) + "; " + e.getMessage());
        }
    }

    public static String logChannel(SocketChannel channel) throws IOException {
        if (channel.isOpen()) {
            InetSocketAddress local = ((InetSocketAddress)channel.getLocalAddress());
            InetSocketAddress remote = ((InetSocketAddress)channel.getRemoteAddress());
            return local.getPort() + ":" + remote.getAddress() + ":" + remote.getPort();
        } else {
            return "closed channel " + channel;
        }
    }

}
