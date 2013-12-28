import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
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

    static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );
    //static private final CharsetDecoder decoder = charset.newDecoder();
    
    String hostname = "localhost";
    
    Socket clientSocket = new Socket( "localhost" , 10002);
    
    DataOutputStream outToServer =
	         new DataOutputStream(clientSocket.getOutputStream());
    
    PrintWriter out =
            new PrintWriter(clientSocket.getOutputStream(), true);
    
    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
    	
    	String[] parsed = message.split(" ");
    	String toAppend = "";
    	
    	if(parsed[0].equals("MESSAGE")){
    		toAppend = parsed[1] + ": ";
    		String tempString = "MESSAGE " + parsed[1];
    		for (int i = tempString.length(); i < message.length(); i++) {
    			toAppend = toAppend + message.charAt(i);
    		}
    		
    	}
    	
    	
        chatArea.append(toAppend);
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
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui



    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        // PREENCHER AQUI com código que envia a mensagem ao servidor

    	//outToServer.writeBytes(message + '\n');
    	//outToServer.writeBytes(message);
    	out.println(message);
    }

    
    // Método principal do objecto
    public void run() throws IOException {
        // PREENCHER AQUI
    	BufferedReader s_in = null;
    	s_in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    	String response;
    	while ((response = (String) s_in.readLine()) != null) 
        {
            printMessage(response + '\n');
    		//System.out.println( response );
        }
    	
    }
    

    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        //ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        ChatClient client = new ChatClient("asd",22);
        client.run();
    }

}