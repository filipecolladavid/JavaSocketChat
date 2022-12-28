import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;


public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui

    static private final ByteBuffer inputB = ByteBuffer.allocate(16384);
    static private final ByteBuffer outputB = ByteBuffer.allocate(16384);

    static private SocketChannel sc = null;

    // Decoder for incoming text -- assume UTF-8
    static private final Charset charset = Charset.forName("UTF8");
    static private final CharsetDecoder decoder = charset.newDecoder();

    static private final Set<String> AVAILABLECMD = Set.of("join", "nick", "leave", "bye", "priv");


    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }


    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocusInWindow();
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui

        Thread client = new Thread();
        client.run();

        InetSocketAddress isa = new InetSocketAddress(server, port);
        sc = SocketChannel.open(isa);

    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {

        // PREENCHER AQUI com código que envia a mensagem ao servidor


        if (message.charAt(0) == '/') {
            if (message.charAt(1) == '/') {
                message = "/" + message;
            } else {
                String[] parts = message.split(" ");
                String cmd = parts[0].substring(1);
                if(!AVAILABLECMD.contains(cmd)) message = "/"+message;
            }
        } message = message + "\n";

        System.out.println(message);
        outputB.clear();
        outputB.put(message.getBytes());
        outputB.flip();

        while(outputB.hasRemaining()) {
            sc.write(outputB);
        }

    }


    // Método principal do objecto
    public void run() throws IOException {
        // PREENCHER AQUI
        while (true) {
            inputB.clear();
            sc.read(inputB);
            inputB.flip();

            String message = decoder.decode(inputB).toString();
            String[] response = message.split(" ");

            if (response[0].equals("JOINED") && response.length == 2) {
                printMessage(response[1].trim() + " entrou na sala.\n");
            }
            else if (response[0].equals("LEFT") && response.length == 2) {
                printMessage(response[1].trim() + " saiu da sala.\n");
            }
            else if (response[0].equals("NEWNICK") && response.length == 3) {
                printMessage(response[1] + " mudou o nome para " + response[2]);
            }
            else if (response[0].equals("MESSAGE") && response.length > 2) {
                printMessage(response[1] + ": " + message.substring(response[0].length()+response[1].length()+2));
            }
            else if (response[0].equals("PRIVATE") && response.length > 2) {
                printMessage("Privada "+response[1] + ": " + message.substring(response[0].length()+response[1].length()+2));
            }
            else {
                printMessage(message);
            }
        }
    }


    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}