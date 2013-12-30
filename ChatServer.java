import java.awt.TrayIcon.MessageType;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;



public class ChatServer
{
	// A pre-allocated buffer for the received data
	static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

	// Decoder for incoming text -- assume UTF-8
	static private final Charset charset = Charset.forName("UTF8");
	static private final CharsetDecoder decoder = charset.newDecoder();
	
	public static Hashtable<SocketChannel, String> users = new Hashtable<SocketChannel, String>();
	public static Hashtable<SocketChannel, Integer> userState = new Hashtable<SocketChannel, Integer>();
	public static Hashtable<SocketChannel, String> userChannel = new Hashtable<SocketChannel, String>();
	
	public static final Integer INIT = 1;
	public static final Integer OUTSIDE = 2;
	public static final Integer INSIDE = 3;
	public static final String OK = "OK" + '\n';
	public static final String ERROR = "ERROR" + '\n';
	
	static public void main( String args[] ) throws Exception {
		// Parse port from command line
		int port = Integer.parseInt( args[0] );
		//int port = 10002;
		try {
			// Instead of creating a ServerSocket, create a ServerSocketChannel
			ServerSocketChannel ssc = ServerSocketChannel.open();

			// Set it to non-blocking, so we can use select
			ssc.configureBlocking( false );

			// Get the Socket connected to this channel, and bind it to the
			// listening port
			ServerSocket ss = ssc.socket();
			InetSocketAddress isa = new InetSocketAddress( port );
			ss.bind( isa );

			// Create a new Selector for selecting
			Selector selector = Selector.open();

			// Register the ServerSocketChannel, so we can listen for incoming
			// connections
			ssc.register( selector, SelectionKey.OP_ACCEPT );
			System.out.println( "Listening on port "+ port );

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
				Set keys = selector.selectedKeys();
				Iterator it = keys.iterator();
				while (it.hasNext()) {
					// Get a key representing one of bits of I/O activity
					SelectionKey key = (SelectionKey)it.next();

					// What kind of activity is it?
					if (key.isAcceptable()) {

						
						// It's an incoming connection.  Register this socket with
						// the Selector so we can listen for input on it
						Socket s = ss.accept();
						System.out.println( "Got connection from "+s );

						// Make sure to make it non-blocking, so we can use a selector
						// on it.
						SocketChannel sc = s.getChannel();
						sc.configureBlocking( false );

						// Register it with the selector, for reading
						sc.register( selector, SelectionKey.OP_READ );
						//users.put(sc, "asd");
						userState.put(sc, INIT);
					} else if (key.isReadable()) {

						SocketChannel sc = null;

						try {

							// It's incoming data on a connection -- process it
							sc = (SocketChannel)key.channel();
							boolean ok = processInput( sc );

							// If the connection is dead, remove it from the selector
							// and close it
							if (!ok) {
								key.cancel();

								Socket s = null;
								try {
									s = sc.socket();
									System.out.println( "Closing connection to "+s );
									s.close();
								} catch( IOException ie ) {
									System.err.println( "Error closing socket "+s+": "+ie );
								}
							}

						} catch( IOException ie ) {

							// On exception, remove this channel from the selector
							key.cancel();

							try {
								sc.close();
							} catch( IOException ie2 ) { System.out.println( ie2 ); }

							System.out.println( "Closed "+sc );
						}
					}
				}

				// We remove the selected keys, because we've dealt with them.
				keys.clear();
			}
		} catch( IOException ie ) {
			System.err.println( ie );
		}
	}


	// Just read the message from the socket and send it to stdout
	static private boolean processInput( SocketChannel sc ) throws IOException {
		// Read the message to the buffer

		Socket s = sc.socket();
		//buffer.clear();
		sc.read( buffer );
		//buffer.flip();
		buffer.rewind();
		// If no data, close the connection
		if (buffer.limit()==0) {
			return false;
		}

		// Decode and print the message to stdout
		String message = decoder.decode(buffer).toString();
		System.out.println(message);
		if (message.charAt(message.length()-1) == '\n') {
			buffer.clear();
			message = message.replaceAll("(\\r|\\n)", "");
			String[] parsed = message.split(" ");
			
			// NICK
			if (parsed[0].equalsIgnoreCase("/nick") && parsed.length == 2) {
				//System.out.println("WARNING: The command /nick is not warning people that user changes nick");
				if(users.containsValue(parsed[1])) {
					// Nick exists
					System.out.println("Nick wasn't changed");
					sendMessage(sc, ERROR);
				} else {
					//nick doesn't exists, changing users nick
					userChangedNick(sc, users.get(sc), parsed[1]);
					users.put(sc, parsed[1]);
					if(userState.get(sc) != INSIDE){
						userState.put(sc, OUTSIDE);
					}
					System.out.println("User changed nick");
					sendMessage(sc, OK);
					
				}
				return true;
			}
			// JOIN
			if (parsed[0].equalsIgnoreCase("/join") && parsed.length == 2) {
				if(userState.get(sc).equals(INSIDE)) {
					userLeave(sc, userChannel.get(sc));
				}
				
				userChannel.put(sc, parsed[1]);
				userState.put(sc, INSIDE);
				sendMessage(sc, OK);
				
				userJoin(sc, parsed[1]);
				
				return true;
			}
			// LEAVE
			if (parsed[0].equalsIgnoreCase("/leave")) {
				if(userState.get(sc) == INSIDE) {
					userLeave(sc, userChannel.get(sc));
					userState.put(sc, OUTSIDE);
					userChannel.remove(sc);
					sendMessage(sc, OK);
				} else {
					System.out.println("This user hasn't joined a channel");
					sendMessage(sc, ERROR);
				}
				return true;
			}
			// BYE
			if (parsed[0].equalsIgnoreCase("/bye")) {
				
				if(userState.get(sc) == INSIDE) {
					userLeave(sc, userChannel.get(sc));
				}
				userState.remove(sc);
				users.remove(sc);
				userChannel.remove(sc);
				String str_temp = "BYE"+'\n';
				sendMessage(sc, str_temp);
				return false;
			}
			// PRIV
			if (parsed[0].equalsIgnoreCase("/priv")) {
				
				Enumeration keys = users.keys();
				
				SocketChannel sc_temp = null;
				String nick = null;
				while (keys.hasMoreElements()){	
					Object key = keys.nextElement();
					sc_temp = (SocketChannel) key;
					if(users.get(key).equals(parsed[1])) {
						nick = users.get(key);
						break;
					}
				}
				
				if(nick == null) {
					sendMessage(sc, ERROR);
					return true;
				}
				String messageToSend = "PRIVATE " + users.get(sc);
				
				for(int i = 2; i< parsed.length; i++){
					messageToSend = messageToSend + " " + parsed[i];
				}
				
				
				messageToSend = messageToSend + '\n';
				System.out.println(messageToSend);
				sendMessage(sc_temp, messageToSend);
				sendMessage(sc, messageToSend);
				return true;
			}
			
			if(parsed[0].charAt(0) == '/') {
				if(parsed[0].length() == 1) {
					sendMessage(sc, ERROR);
					return true;
				} else {
					if(parsed[0].charAt(1) != '/'){
						sendMessage(sc, ERROR);
						return true;
					}
				}
				
			}
			// MESSAGE
			if(userState.get(sc) == INSIDE) {
				// User is inside a channel
				// This means that the user can send normal messages
				
				String messageToSend = "MESSAGE " + users.get(sc) + " " + message +'\n';
				
				if(parsed[0].length()>1){
					if(parsed[0].charAt(1) == '/') {
						System.out.println("String starts with //");
						
						String str_tempString = "";
						for (int i = 1; i < message.length(); i++) {
							str_tempString = str_tempString + message.charAt(i);
						}
						messageToSend = "MESSAGE " + users.get(sc) + " " + str_tempString +'\n';
					}
				} 
				
				// Normal message
				System.out.println("A normal message");
				
				Enumeration keys = userChannel.keys();
				
				while (keys.hasMoreElements()){	
					Object key = keys.nextElement();
					//System.out.println(key.toString());
					if(userChannel.get(sc).equals(userChannel.get(key))) {
						SocketChannel sc_temp = (SocketChannel) key;
						sendMessage(sc_temp, messageToSend);
					}
				}
				
				return true;
			} else {
				// User isn't in any channel
				sendMessage(sc, ERROR);
			}
		} else {
			// message doesn't contain \n
			
		}
		return true;
	}
	
	public static void sendMessage(SocketChannel sc_to_send, String messageToSend) throws IOException {
		
		buffer.clear();
		buffer.put(messageToSend.getBytes());
		buffer.flip();
		
		while(buffer.hasRemaining()) {
		    sc_to_send.write(buffer);
		}
	}
	
	public static void userJoin(SocketChannel sc_to_send, String channel) throws IOException {
		Enumeration keys = userChannel.keys();
		
		String tempMessage = "JOINED " + users.get(sc_to_send) + '\n';
		
		while (keys.hasMoreElements()){	
			
			Object key = keys.nextElement();
			SocketChannel sc_temp = (SocketChannel) key;
			if(userChannel.get(key).equals(channel) && sc_temp != sc_to_send) {
				sendMessage(sc_temp, tempMessage);
			}
		}
	}

	public static void userLeave(SocketChannel sc_to_send, String channel) throws IOException {
		Enumeration keys = userChannel.keys();
		
		String tempMessage = "LEFT " + users.get(sc_to_send) + '\n';
		
		while (keys.hasMoreElements()){	
			
			Object key = keys.nextElement();
			SocketChannel sc_temp = (SocketChannel) key;
			if(userChannel.get(key).equals(channel) && sc_temp != sc_to_send) {
				sendMessage(sc_temp, tempMessage);
			}
		}
	}
	
	public static void userChangedNick(SocketChannel sc_to_send, String old_nick, String new_nick) throws IOException {
		
		Enumeration keys = userChannel.keys();
		
		String tempMessage = "NEWNICK " + old_nick + " "+ new_nick +'\n';
		
		while (keys.hasMoreElements()){	
			
			Object key = keys.nextElement();
			SocketChannel sc_temp = (SocketChannel) key;
			if(userChannel.get(key).equals(userChannel.get(sc_to_send)) && sc_temp != sc_to_send) {
				sendMessage(sc_temp, tempMessage);
			}
		}
	}
	
}
