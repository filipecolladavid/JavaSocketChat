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
    static private final String LEFT = "LEFT  ";
    static private final String JOINED = "JOINED ";
    static private final String NEWNICK = "NEWNICK";
    static private final String MESSAGE = "MESSAGE ";
    static private final String BYE = "BYE";

    // A pre-allocated buffer for the received data
    static private final ByteBuffer buffer = ByteBuffer.allocate(16384);

    // String builder for separate packages
    static private StringBuilder sb = new StringBuilder();

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

    static private void writeInSocket(SocketChannel sc, ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            try {
                sc.write(buffer);
            } catch (IOException e) {
                // An I/O error occurred while writing to the channel
                e.printStackTrace();
            }
        }
    }

    //Send info to all users in the same room
    static private void sendResponseRoom(SocketChannel sc, String message) {
        String room = users.get(sc).getRoom();
        String nick = users.get(sc).getNick();

        byte[] info = (message).getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(info);

        for (Map.Entry<SocketChannel, User> set : users.entrySet()) {
            SocketChannel scOther = set.getKey();
            User u = set.getValue();
            if (u.getNick().equals(nick)) continue;
            if (u.getRoom().equals(room)) {
                buffer.rewind();
                writeInSocket(scOther, buffer);
            }
        }
    }

    // Sends individual response to users
    static private void sendResponseSc(SocketChannel sc, String message) {
        byte[] info = (message).getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(info);
        writeInSocket(sc, buffer);
    }

    //Check the availability of the username inserted
    static private boolean nickAvailable(String nick) {
        for (Map.Entry<SocketChannel, User> set :
                users.entrySet()) {
            if (set.getValue().getNick().equals(nick)) return false;
        }
        return true;
    }

    // Process the command
    static private void processCommand(String command, SocketChannel sc) {

        String name = users.get(sc).getNick();
        String state = users.get(sc).getState();

        String[] args = command.split(" ");
        String cmd = args[0];

        switch (cmd) {
            case "nick" -> {
                if (!(args.length == 1 || args[1].equals(""))) {
                    String newNick = args[1];
                    if (nickAvailable(newNick)) {
                        users.get(sc).setNick(newNick);
                        users.get(sc);
                        if (state.equals(User.INIT))
                            users.get(sc).setStateOutside();
                        if (state.equals(User.INSIDE)) {
                            sendResponseRoom(sc, NEWNICK + name + " " + newNick);
                        }
                        sendResponseSc(sc, OK);
                        return;
                    }
                }
            }
            case "join" -> {
                if (!(args.length == 1 || args[1].equals(""))) {
                    String room = args[1];
                    if (state.equals(User.INSIDE)) sendResponseRoom(sc, LEFT + name);
                    users.get(sc).setRoom(room);
                    sendResponseSc(sc, OK);
                    sendResponseRoom(sc, JOINED + name);
                    return;
                }
            }
            case "leave" -> {
                if (state.equals(User.INSIDE)) {
                    sendResponseSc(sc, OK);
                    sendResponseRoom(sc, LEFT + name);
                    users.get(sc).setRoom("");
                    users.get(sc).setStateOutside();
                }
            }
            case "bye" -> {
                if (state.equals(User.INSIDE)) sendResponseRoom(sc, LEFT + name);
                sendResponseSc(sc, BYE);
            }
        }
        sendResponseSc(sc, ERROR);
    }

    // Just read the message from the socket and send it to stdout
    static private boolean processInput(SocketChannel sc) throws IOException {
        // Read the message to the buffer
        buffer.clear();
        sc.read(buffer);
        buffer.flip();

        while (buffer.hasRemaining()) { // loop until the buffer is empty
            byte b = buffer.get(); // get the next byte from the buffer
            System.out.println((char)b+":"+b);
            if (b == 10) { // check if the byte is the ASCII value for "ENTER"
                // Decode and print the message to stdout
                String message = sb.toString();
                System.out.println(message);
                if (message.charAt(0) == '/')
                    processCommand(message.substring(1), sc);
                sb.setLength(0);
                continue; // exit the loop
            }
            sb.append((char) b); // append the byte to the StringBuilder
        }


        // If no data, close the connection
/*
        if (buffer.limit() == 0) {
            return false;
        }

        // Decode and print the message to stdout
        String message = decoder.decode(buffer).toString();
        if (message.charAt(0) == '/')
            processCommand(message.substring(1), sc);
        else if(users.get(sc).getState().equals(User.INSIDE)) sendResponseRoom(sc, MESSAGE+users.get(sc).getNick()+message);
*/

        // System.out.print( message );

        return true;

    }
}
