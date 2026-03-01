package org.ah.sigas.broker;

import java.net.URI;

import org.ah.sigas.broker.message.Messages;

public class Main {

    public static void main(String[] args) throws Exception {
        int serverPort = -1;
        int internalPort = -1;
        URI uri = null;

        int ptr = 0;

        while (ptr < args.length) {
            if (args[ptr].equals("--server-port") || args[ptr].equals("-s")) {
                ptr++;
                if (ptr >= args.length) { error("Missing value for switch " + args[ptr - 1]); }
                try {
                    serverPort = Integer.parseInt(args[ptr]);
                } catch (NumberFormatException e) {
                    error("Expected port number but got '" + args[0] + "'");
                }
            } else if (args[ptr].equals("--internal-port") || args[ptr].equals("-i")) {
                ptr++;
                if (ptr >= args.length) { error("Missing value for switch " + args[ptr - 1]); }
                try {
                    internalPort = Integer.parseInt(args[ptr]);
                } catch (NumberFormatException e) {
                    error("Expected port number but got '" + args[0] + "'");
                }
            } else if (args[ptr].equals("--hub-url") || args[ptr].equals("-u")) {
                ptr++;
                if (ptr >= args.length) { error("Missing value for switch " + args[ptr - 1]); }
                try {
                    uri = URI.create(args[ptr]);
                } catch (IllegalArgumentException e) {
                    error("Expected URL/URI got '" + args[0] + "'");
                }
            } else {
                error("Unknown parameter " + args[ptr - 1]);
            }
            ptr++;
        }

        if (serverPort == -1) { error("Missing --server-port parameter"); }
        if (internalPort == -1) { error("Missing --internal-port parameter"); }
        if (uri == null) { error("Missing --hub-url parameter"); }


        System.out.println("Starting server on port " + serverPort + " and internal port " + internalPort);
        System.out.println("Configured hub uri as " +  uri);

        Messages.registerAll();

        Broker broker = new Broker(serverPort, internalPort, uri);
        broker.loop();
    }

    private static void error(String msg) {
        System.err.println(msg);
        System.err.println();
        printHelp();
        System.exit(-1);
    }

    private static void printHelp() {
        System.out.println("Usage: java -jar sigas-broker.jar --server-port <server-port> --local-port <local-port> --hub-url <hub-url>");
        System.out.println("");
        System.out.println("  Options:");
        System.out.println("    --server-port <server-port>      port to listen for external connections");
        System.out.println("    --internal-port <internal-port>  port to listen for internal hub connections");
        System.out.println("    --hub-url <hub-url>              url of hub for callbacks");
        System.out.println("");
        System.out.println("  Order of switches is not important");
    }

}
