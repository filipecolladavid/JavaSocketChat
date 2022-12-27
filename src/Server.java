import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.time.LocalTime;
import java.util.*;

public class Server {

    static private final String ERROR = "ERROR";
    static private final String OK = "OK";
    static private final String BYE = "BYE";

    // A pre-allocated buffer for the received data
    static private final ByteBuffer buffer = ByteBuffer.allocate(16384);

    // Decoder for incoming text -- assume UTF-8
    static private final Charset charset = StandardCharsets.UTF_8;
    static private final CharsetDecoder decoder = charset.newDecoder();

    // Users and their respective socket
    static private HashMap<SocketChannel, User> users = new HashMap<>();

    static public void main(String[] args) throws Exception {
        // Parse port from command line
        int port = Integer.parseInt(args[0]);

        try {
            // Instead of creating a ServerSocket, create a ServerSocketChannel
            ServerSocketChannel ssc = ServerSocketChannel.open();

            // Set it to non-blocking, so we can use select
            ssc.configureBlocking(false);

            // Get the Socket connected to this channel, and bind it to the
            // listening port
            ServerSocket ss = ssc.socket();
            InetSocketAddress isa = new InetSocketAddress(port);
            ss.bind(isa);

            // Create a new Selector for selecting
            Selector selector = Selector.open();

            // Register the ServerSocketChannel, so we can listen for incoming
            // connections
            ssc.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Listening on port " + port);

            while (true) {
                // See if we've had any activity -- either an incoming connection,
                // or incoming data on an existing connection
                int num = selector.select();

                // If we don't have any activity, loop around and wait again
                if (num == 0) {
                    continue;
                }

                // Get the keys corresponding to the activity that has been
                // detected, and process them one by one
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();
                while (it.hasNext()) {
                    // Get a key representing one of bits of I/O activity
                    SelectionKey key = it.next();

                    // What kind of activity is it?
                    if (key.isAcceptable()) {

                        // It's an incoming connection.  Register this socket with
                        // the Selector, so we can listen for input on it
                        Socket s = ss.accept();
                        System.out.println("Got connection from " + s);

                        // Make sure to make it non-blocking, so we can use a selector
                        // on it.
                        SocketChannel sc = s.getChannel();
                        sc.configureBlocking(false);

                        //Create and save new user to users
                        User newUser = new User("user_" + LocalTime.now().toString());
                        System.out.println(newUser);
                        users.put(sc, newUser);

                        // Register it with the selector, for reading with the newUser as att
                        sc.register(selector, SelectionKey.OP_READ, newUser);

                    } else if (key.isReadable()) {

                        SocketChannel sc = null;

                        try {

                            // Its incoming data on a connection -- process it
                            sc = (SocketChannel) key.channel();
                            boolean ok = processInput(sc);

                            // If the connection is dead, remove it from the selector
                            // and close it
                            if (!ok) {
                                key.cancel();

                                Socket s = null;
                                try {
                                    s = sc.socket();
                                    System.out.println("Closing connection to " + s);
                                    s.close();
                                } catch (IOException ie) {
                                    System.err.println("Error closing socket " + s + ": " + ie);
                                }
                            }

                        } catch (IOException ie) {

                            // On exception, remove this channel from the selector
                            key.cancel();

                            try {
                                sc.close();
                            } catch (IOException ie2) {
                                System.out.println(ie2);
                            }

                            System.out.println("Closed " + sc);
                        }
                    }
                }

                // We remove the selected keys, because we've dealt with them.
                keys.clear();
            }
        } catch (IOException ie) {
            System.err.println(ie);
        }
    }


    // Sends individual response to users
    static private void sendResponseSc(SocketChannel sc, String message) {

        byte[] info = (message).getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(info);
        while (buffer.hasRemaining()) {
            try {
                sc.write(buffer);
            } catch (IOException e) {
                // An I/O error occurred while writing to the channel
                e.printStackTrace();
            }
        }
    }

    //Check the availability of the username inserted
    static private boolean nickAvailable(String nick) {
        for (Map.Entry<SocketChannel, User> set :
                users.entrySet()) {
            // Printing all elements of a Map
            if (set.getValue().getNick().equals(nick)) return false;
        }
        return true;
    }

    // Process the command
    static private void processCommand(String command, SocketChannel sc) {


        String[] args = command.split(" ");
        if (args.length == 1 || args[1].equals("")) return;

        String cmd = args[0];
        String value = args[1];


        switch (cmd) {
            case "nick":
                if (nickAvailable(value)) {
                    users.get(sc).setNick(value);
                    users.get(sc);
                    if(users.get(sc).getState().equals(User.INIT))
                        users.get(sc).setStateOutside();
                    if(users.get(sc).getState().equals(User.INSIDE)) {
                        // Send message to all NEW_NICK nome_antigo nome
                        // ...
                    }
                    sendResponseSc(sc,OK);
                }
                else sendResponseSc(sc, ERROR);
                break;
            case "bye":
                sendResponseSc(sc, BYE);
                break;
        }
    }

    // Just read the message from the socket and send it to stdout
    static private boolean processInput(SocketChannel sc) throws IOException {
        // Read the message to the buffer
        buffer.clear();
        sc.read(buffer);
        buffer.flip();

        // If no data, close the connection
        if (buffer.limit() == 0) {
            return false;
        }

        // Decode and print the message to stdout
        String message = decoder.decode(buffer).toString();
        Boolean valid = false;
        if (message.charAt(0) == '/')
            processCommand(message.substring(1), sc);

        // System.out.print( message );

        return true;
    }
}
