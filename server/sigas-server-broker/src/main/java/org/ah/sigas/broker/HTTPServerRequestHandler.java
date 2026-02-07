package org.ah.sigas.broker;

import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;

import org.ah.sigas.broker.game.Client;
import org.ah.sigas.broker.game.Game;

public class HTTPServerRequestHandler extends HTTPRequestHandler {

    public HTTPServerRequestHandler(Broker broker) {
        super(broker);
    }

    @Override
    protected void processRequest(SelectionKey key, ReadableByteChannel channel) {

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

            Game game = broker.getGames().get(gameId);

            if (game == null) {
                if (Broker.INFO) { System.out.println("Bad request - game not found '" + gameId + "'"); }
                createSimpleResponse(key, 404, "NOT FOUND", "");
                return;
            }

            for (Client client : game.getClients()) {
                if (client.getToken().equals(token)) {

                    createSimpleResponse(key, 200, "OK", "OK to proceed with method " + method + "\n");
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
