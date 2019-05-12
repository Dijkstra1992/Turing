package turing_pkg;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/* my utility class */
import turing_pkg.Config;

public class TuringServer {
	
	/* Users database structure */ 
	public static Map<String, User> usersDB; 	 			// registered users
	private static Map<String, SocketChannel> loggedUsers;	// online users

	public static void main(String[] args) throws InterruptedException, IOException {
		
		/* Server socket initialization */
		System.out.println("Initializing server...");		
		ServerSocketChannel ssch = null;
		Selector selector = null;
		try {
			ssch = ServerSocketChannel.open();
			ssch.socket().bind(new InetSocketAddress(Config.SERVER_IP, Config.SERVER_PORT)); //modified "localhost" -> SERVER_IP
			ssch.configureBlocking(false);
			selector = Selector.open();
			ssch.register(selector, SelectionKey.OP_ACCEPT);
		} catch (Exception e) { e.printStackTrace(); }
		
		/* Data structures initialization */
		usersDB = new HashMap<String, User>();
		loggedUsers = new HashMap<String, SocketChannel>();
		
		/* exports remote registration service */
		TuringRemoteRegisterOP remote_service = new TuringRemoteRegisterOP();
		TuringRemoteService stub = (TuringRemoteService) UnicastRemoteObject.exportObject(remote_service, Config.REMOTE_SERVICE_PORT);
		LocateRegistry.createRegistry(Config.REMOTE_SERVICE_PORT);
		Registry registry = LocateRegistry.getRegistry(Config.REMOTE_SERVICE_PORT);
		registry.rebind("registerOP", stub);
		
		System.out.println("Server started on port " + Config.SERVER_PORT);
		
		Set<SelectionKey> key_set;
		Iterator<SelectionKey> key_iterator;

		while (true) { // server routine
					
			System.out.println("Waiting for incoming connections");
			try { 
				selector.select(5000);
			
				ByteBuffer buffer = ByteBuffer.allocate(Config.MAX_BUF_SIZE);
				key_set = selector.selectedKeys();
				key_iterator = key_set.iterator();
				
				while (key_iterator.hasNext()) {
					SelectionKey ready_key = key_iterator.next();
					
					if ( ready_key.isAcceptable() ) {
						// new connection request received on server channel 
						SocketChannel client = ssch.accept();
						client.configureBlocking(false);
						client.register(selector, SelectionKey.OP_READ);
						System.out.println("Client connected " + client.getRemoteAddress());
					}
					
					else if ( ready_key.isReadable() ) {
						// a client is sending a request on his channel
						SocketChannel client = (SocketChannel) ready_key.channel();
						buffer.clear();
						try { getRequest(client, buffer); }
						catch (IOException io_ex) {
							if (loggedUsers.containsValue(client)) {
								disconnectClient(client);
								System.out.println(client.getRemoteAddress() + " -> " + io_ex.getLocalizedMessage());
							}
							client.close();
							ready_key.cancel();

						}
					}									
					
					key_iterator.remove();
				}
			} catch (Exception select_ex) {
				select_ex.printStackTrace();
			}
		} 
	}

	private static void getRequest(SocketChannel client, ByteBuffer buffer) throws IOException {
		
		buffer.clear();
		client.read(buffer);	
		//At this point if the remote host closed the connection than read returns -1,
		//an exception will be thrown and the socket channel will be closed. 

		/* reading request message header */
		buffer.flip();
		byte r_type = buffer.get();
		byte user_s;
		byte pass_s;
		byte[] username;
		byte[] file_name;
		byte[] dest_name;
		byte[] text;
		byte file_name_s;
		byte dest_name_s;
		byte section;
		byte text_length;
		String user;
		String file;
		String file_text;
		String dest;

		/* processing client requests */
		switch (r_type) {
			/* login request */
			case Config.LOGIN_R :
				user_s = buffer.get();
				pass_s = buffer.get(); //password length				
				username = new byte[user_s];
		 		buffer.get(username, 0, user_s);
				user = new String(username, Config.DEFAULT_ENCODING);				
				byte[] password = new byte[pass_s*2];
				buffer.get(password, 0, (pass_s*2));
				char[] pass = Config.toChars(password);
				loginUser(client, user, pass);
				break;
				
			/* logout request */
			case Config.LOGOUT_R :
				user_s = buffer.get();
				username = new byte[user_s];
				buffer.get(username, 0, user_s);
				user = new String(username, Config.DEFAULT_ENCODING);
				loggedUsers.remove(user);
				sendResponse(client, Config.SUCCESS);
				break;
				
			/* new document creation request */
			case Config.NEW_R :
				user_s = buffer.get();
				file_name_s = buffer.get(); //file name length
				byte sections	= buffer.get(); //number of sections
				username = new byte[user_s];
				buffer.get(username, 0, user_s);
				user = new String(username, Config.DEFAULT_ENCODING);
				file_name = new byte[file_name_s];
				buffer.get(file_name, 0, file_name_s);
				file = new String(file_name, Config.DEFAULT_ENCODING);	
				newDocCreate(client, user, file , sections);
				break;
				
			/* list user documents request */
			case Config.LIST_R :
				user_s = buffer.get();
				username = new byte[user_s];
				buffer.get(username, 0, user_s);
				user = new String(username, Config.DEFAULT_ENCODING);
				listDocs(client, user);
				break;
				
			/* share document request */
			case Config.SHARE_R	:
				user_s = buffer.get();
				file_name_s = buffer.get();
				dest_name_s = buffer.get(); 
				username = new byte[user_s];
				file_name = new byte[file_name_s];
				dest_name = new byte[dest_name_s];
				buffer.get(username, 0, user_s);
				buffer.get(file_name, 0, file_name_s);
				buffer.get(dest_name, 0, dest_name_s);
				user = new String(username, Config.DEFAULT_ENCODING);
				file = new String(file_name, Config.DEFAULT_ENCODING);
				dest = new String(dest_name, Config.DEFAULT_ENCODING);
				shareDoc(client, user, dest, file);
				break; 
			
			/* download file request */
			case Config.DOWNLOAD_R:
				user_s = buffer.get();
				file_name_s = buffer.get();
				username = new byte[user_s];
			    file_name = new byte[file_name_s];
			    buffer.get(username, 0, user_s);
			    buffer.get(file_name, 0, file_name_s);
			    section = buffer.get();
			    user = new String(username, Config.DEFAULT_ENCODING);
				file = new String(file_name, Config.DEFAULT_ENCODING);
			    sendFile(client, user, file, section);
				break;
				
			/* get new file version from client and save it to the server's local database */
			case Config.END_EDIT_R:
				user_s = buffer.get();
				file_name_s = buffer.get();
			    section = buffer.get();
			    text_length = buffer.get();
				username = new byte[user_s];
			    file_name = new byte[file_name_s];
			    text = new byte[text_length];
			    buffer.get(username, 0, user_s);
			    buffer.get(file_name, 0, file_name_s);
			    buffer.get(text, 0, text_length);
			    user = new String(username, Config.DEFAULT_ENCODING);
				file = new String(file_name, Config.DEFAULT_ENCODING);
				file_text = new String(text, Config.DEFAULT_ENCODING);
				updateFile(client, user, file, file_text, section);
				break;
			default	: 
				System.out.print("Unknown request!!!");
				break;
		}
		
	}
	
	private static void loginUser(SocketChannel client, String username, char[] password) {
		
		// checking if user is registered
		if ( !usersDB.containsKey(username) ) {
			System.out.println("UKNOWN USERNAME [" + username + "]");
			sendResponse(client, Config.UNKNOWN_USER);
			return;
		}
		
		// checking password validity
		User user = usersDB.get(username);
		char[] pass = user.getPass();
	
		for (int i=0; i<password.length; i++) {
			if (pass[i] != password[i]) {
				sendResponse(client, Config.INVALID_PASS);
				return;
			}
		}
		
		// check if user is already online
		if ( loggedUsers.containsKey(username)) {
			sendResponse(client, Config.ALREADY_ON);
			return;
		}
		
		// user login
		loggedUsers.put(username, client);
		sendResponse(client, Config.SUCCESS); 
	}

	private static void newDocCreate(SocketChannel client, String username, String filename, int sections) throws IOException {
		
		// check if user is already online
		if ( !loggedUsers.containsKey(username)) {
			sendResponse(client, Config.UNKNOWN_USER);
			return;
		}
		
		String pathName = new String(Config.FILE_PATH + username + "\\" + filename);
		System.out.println("Save location: " + pathName);
		Path savePath = Paths.get(pathName);
		try {
			Files.createDirectories(savePath);
			System.out.println("Directory " + pathName + " created");
			for (int i=0; i<sections; i++) {
				Path filePath = Paths.get(pathName + "\\" + filename + "_"+ Integer.toString(i) + ".txt");
				Files.createFile(filePath);
			}
		} catch (IOException io_ex) {
			io_ex.printStackTrace();
			sendResponse(client, Config.UNKNOWN_ERROR);
		}
		
		System.out.println("File " + pathName + " created");
		User user = usersDB.get(username);
		Document document = new Document(filename, sections, Config.CREATOR);
		user.addFile(document);
		sendResponse(client, Config.SUCCESS);
	}
	
	private static void listDocs(SocketChannel client, String username) {
		if ( !loggedUsers.containsKey(username)) {
			sendResponse(client, Config.UNKNOWN_USER);
			return;
		}
		
		User user = usersDB.get(username);
		Iterator<Document> it = user.getFileIterator();
		if (!it.hasNext()) { //empty documents list
			sendResponse(client, Config.EMPTY_LIST);
			return;
		}
		ByteBuffer buffer = ByteBuffer.allocate(Config.MAX_BUF_SIZE);
		buffer.put(Config.SUCCESS);
		try {
			while (it.hasNext()) 
			{
				Document temp = it.next();
				byte[] data = new byte[temp.title.length()*2]; 
				data = temp.title.getBytes(Config.DEFAULT_ENCODING);
				buffer.put(data);
				buffer.put(" ".getBytes(Config.DEFAULT_ENCODING));
				System.out.println("Lenght: " + data.length  + ", Name: " + temp.title);
			}
			buffer.flip();
			client.write(buffer);
		} catch (IOException io_ex) {
			io_ex.printStackTrace();
			sendResponse(client, Config.UNKNOWN_ERROR);
		}
		
	}
	
	private static void shareDoc(SocketChannel client, String sender, String receiver, String file) {
		
		if (sender.equals(receiver)) {
			sendResponse(client, Config.INVALID_DEST);
			return;
		}
		
		User recv = usersDB.get(receiver);
		if ( recv == null) {
			sendResponse(client, Config.UNKNOWN_USER);
			return;
		}
		
		User send = usersDB.get(sender);
		Document document = send.getFile(file);
		if (document == null) {
			sendResponse(client, Config.NO_SUCH_FILE);
			return;
		}
		recv.addFile(document);
		sendResponse(client, Config.SUCCESS);
		
		if (loggedUsers.containsKey(receiver)) {
			//TODO: send notification to receiver
		}
		
		return;
	}

	private static void sendFile(SocketChannel client, String username, String filename, int section_number) {
		User user = usersDB.get(username);
		Document doc = user.getFile(filename);
		if (doc.num_sections < section_number) {
			sendResponse(client, Config.INVALID_SECT);
			return;
		}
		String sectionText = getFileSection(username, filename, section_number);
		if (sectionText.length() > 0) {
			byte[] text = new byte[sectionText.length()];
			try {
				text = sectionText.getBytes(Config.DEFAULT_ENCODING);
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			ByteBuffer send = ByteBuffer.allocate(Config.MAX_BUF_SIZE);
			send.put(Config.RECEIVING_BYTES);
			send.put(text);
			send.flip();
			try {
				client.write(send);
				System.out.println("File " + filename + " sent to user " + username + ", total bytes: " + text.length);
			} catch (IOException io_ex) {
				io_ex.printStackTrace();
			}
		}
		else {
			ByteBuffer send = ByteBuffer.allocate(1);
			send.put(Config.NO_BYTES);
			send.flip();
			try {
				client.write(send);
			} catch (IOException io_ex) {
				io_ex.printStackTrace();
			}
		}
		
		//TODO: update section to editing status
	}
	
	private static void updateFile(SocketChannel client, String username, String filename, String text, int section) {
		
		String pathName = new String(Config.FILE_PATH + username + "\\" + filename);
		System.out.println("Save location: " + pathName);
		try {
			Path filePath = Paths.get(pathName + "\\" + filename + "_"+ Integer.toString(section) + ".txt");
			Files.write(filePath, text.getBytes());	
		} catch (IOException io_ex) {
			io_ex.printStackTrace();
			sendResponse(client, Config.UNKNOWN_ERROR);
		}
		sendResponse(client, Config.SUCCESS);
	}
	
	private static String getFileSection(String username, String filename, int section_n) {
		String pathName = new String(Config.FILE_PATH + username + "\\" + filename);
		Path filePath = Paths.get(pathName + "\\" + filename + "_" + Integer.toString(section_n) + ".txt");
		String text = null;
		try {
			text = new String(Files.readAllBytes(filePath));
		} catch (IOException io_ex) {
			io_ex.printStackTrace();
		}
		return text;
	}
	
	private static void sendResponse(SocketChannel client, byte outcome) {
		
		ByteBuffer response = ByteBuffer.allocate(1);
		response.put(outcome);
		response.flip();
		
		try {
			client.write(response); 
		}
		catch (IOException io_ex) { io_ex.printStackTrace(); }
		
	}
	
	private static void disconnectClient(SocketChannel client) {
		
		Set<Entry<String, SocketChannel>> usersQueue = loggedUsers.entrySet();
		Iterator<Entry<String, SocketChannel>> it = usersQueue.iterator();
		while (it.hasNext()) {
			Entry<String, SocketChannel> current = (Entry<String, SocketChannel>) it.next();
			if (current.getValue().equals(client)) {
				loggedUsers.remove(current.getKey());
				return;
			}
		}
	}
}
