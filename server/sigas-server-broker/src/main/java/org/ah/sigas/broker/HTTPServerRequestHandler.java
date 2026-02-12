package org.ah.sigas.broker;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;

import org.ah.sigas.broker.game.Client;
import org.ah.sigas.broker.game.Game;

public class HTTPServerRequestHandler extends HTTPRequestHandler {

    public HTTPServerRequestHandler(Broker broker) {
        super(broker);
    }

    @Override
    protected void processRequest(SelectionKey key, ReadableByteChannel channel) throws IOException {

        if (!method.contentEquals("POST") && !method.equals("GET")) {
            if (Broker.INFO) { System.out.println("Bad request - wrong method " + method); }
            createSimpleResponse(key, 400, "BAD REQUEST", "");
            return;
        }

        if (path.startsWith("/game/")) {

            path = path.substring(6);
            int i = path.indexOf('/');
            if (i < 1) {
                if (Broker.INFO) { System.out.println("Bad request - no game id and token in path '" + path + "'"); }
                createSimpleResponse(key, 400, "BAD REQUEST", "Need game id and token");
                return;
            }

            String gameId = path.substring(0, i);
            String token = path.substring(i + 1);

            String prefix = gameId + ":" + token + " ";

            Game game = broker.getGames().get(gameId);

            if (game == null) {
                if (Broker.INFO) { System.out.println(prefix + "Bad request - game not found"); }
                createSimpleResponse(key, 404, "NOT FOUND", "");
                return;
            }

            for (Client client : game.getClients().values()) {
                if (client.getToken().equals(token)) {
                    if (method.equals("POST")) {
                        String transferEncoding = headers.get("transfer-encoding");
                        if (transferEncoding == null) {
                            if (Broker.INFO) { System.out.println(prefix + "Bad inbound request, no Transfer-Encoding header"); }
                            createSimpleResponse(key, 400, "BAD REQUEST", "Bad inbound request, no Transfer-Encoding header");
                            return;
                        }
                        if (!"chunked".equals(transferEncoding)) {
                            if (Broker.INFO) { System.out.println(prefix + "Bad inbound request, Transfer-Encoding is not set to chunked; '" + transferEncoding + "'"); }
                            createSimpleResponse(key, 400, "BAD REQUEST", "Bad inbound request, Transfer-Encoding must be 'chunked'");
                            return;
                        }

                        ClientHandler inboundHandler = client.getInboundHandler();
                        if (inboundHandler == null) {
                            inboundHandler = new ClientInboundHandlerImpl(broker, client);
                            client.setInboundHandler(inboundHandler);
                        } else {
                            SelectionKey oldKey = inboundHandler.getAssociatedKey();
                            broker.closeChannel(oldKey);
                        }

                        key.attach(inboundHandler);
                        inboundHandler.open(key);
                        if (pos < max) {
                            int initalReadCount = max - pos;

                            ClientInboundHandlerImpl clientInboundHandlerImpl = (ClientInboundHandlerImpl)inboundHandler;

                            clientInboundHandlerImpl.getBuffer().put(bytes, pos, initalReadCount);
                            clientInboundHandlerImpl.processInput(key, channel, initalReadCount);
                        }
                        if (Broker.DEBUG) { System.out.println(gameId + ":" + token + " Got inbound connection"); }

                    } else if (method.equals("GET")) {
                        String contentLength = headers.get("content-length");
                        if (contentLength != null && !"0".equals(contentLength)) {
                            if (Broker.INFO) { System.out.println(prefix + "Bad outbound request, Content-Length if present must be zero; '" + contentLength + "'"); }
                            createSimpleResponse(key, 400, "BAD REQUEST", "Bad outbound request, Content-Length if present must be zero");
                            return;
                        }

                        ClientHandler outboundHandler = client.getOutboundHandler();
                        if (outboundHandler == null) {
                            outboundHandler = new ClientOutboundHandlerImpl(broker, client);
                            client.setOutboundHandler(outboundHandler);
                        } else {
                            SelectionKey oldKey = outboundHandler.getAssociatedKey();
                            broker.closeChannel(oldKey);
                        }

                        key.attach(outboundHandler);
                        outboundHandler.open(key);
                        if (Broker.DEBUG) { System.out.println(gameId + ":" + token + " Got outbound connection"); }
                    }
                    return;
                }
            }

            if (Broker.INFO) { System.out.println("Bad request - client not found for game '" + gameId + "', token '" + token + "'"); }
            createSimpleResponse(key, 404, "NOT FOUND", "");
            return;
        }

        createSimpleResponse(key, 404, "NOT FOUND", "Method " + method + ", path " + path + " not found");
    }

}
