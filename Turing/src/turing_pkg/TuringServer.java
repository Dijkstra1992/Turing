package turing_pkg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/* my utility class */
import turing_pkg.Config;

public class TuringServer {
	
	public static Map<String, User> usersDB; 	 						// registered users (<username, User>)
	public static Lock dbLock;											// usersDB lock for mutual exclusion access
	private static Map<String, SocketChannel> loggedUsers;				// online users (<username, client_socket_channel>)
	private static Map<String, String> editingSessions;					// list of currently open sections for editing (<file_section_name, username>)
	private static Map<String, ChatGroup> chatGroups;					// for chat groups managing (<filename, chatgroup object>)
	private static Map<String, ArrayList<String>> pendingNotifications;	// <username, notification_messages_list>
	private static ServerSocketChannel server_ch = null;
	private static Selector ch_selector = null;
	
	
	public static void main(String[] args) throws InterruptedException, IOException {
		
		/* Server socket init */
		System.out.println("Initializing server...");
		try {
			server_ch = ServerSocketChannel.open();
			server_ch.socket().bind(new InetSocketAddress(Config.SERVER_IP, Config.SERVER_PORT));
			server_ch.configureBlocking(false);
			ch_selector = Selector.open();
			server_ch.register(ch_selector, SelectionKey.OP_ACCEPT);
		} catch (Exception e) { e.printStackTrace(); }
		
		/* Data structures initialization */
		dbLock = new ReentrantLock();
		usersDB = new HashMap<String, User>();
		loggedUsers = new HashMap<String, SocketChannel>(); 
		editingSessions = new HashMap<String, String>();
		chatGroups = new HashMap<String, ChatGroup>();
		pendingNotifications = new HashMap<String, ArrayList<String>>();
		Set<SelectionKey> key_set;
		Iterator<SelectionKey> key_iterator;

		/* Clean up any existing files from previous executions */
		System.out.println("Cleaning previous execution files...");
		cleanUpUtility(Paths.get(Config.FILE_PATH));
		System.out.println("Done!");
		
		/* Remote service export (RMI - new account registration service) */
		TuringRemoteRegisterOP remote_service = new TuringRemoteRegisterOP();
		TuringRemoteService stub = (TuringRemoteService) UnicastRemoteObject.exportObject(remote_service, Config.REMOTE_SERVICE_PORT);
		LocateRegistry.createRegistry(Config.REMOTE_SERVICE_PORT);
		Registry registry = LocateRegistry.getRegistry(Config.REMOTE_SERVICE_PORT);
		registry.rebind("registerOP", stub);
		
		System.out.println("Server started on port " + Config.SERVER_PORT);
		
		ByteBuffer buffer = ByteBuffer.allocate(Config.BUF_SIZE);
		while (true) { // server routine
			try { 
				ch_selector.select();
				key_set = ch_selector.selectedKeys();
				key_iterator = key_set.iterator();
				
				while (key_iterator.hasNext()) {
					SelectionKey ready_key = key_iterator.next();
					
					if ( ready_key.isAcceptable() ) {
						// new connection request received on server channel 
						SocketChannel client = server_ch.accept();
						client.configureBlocking(false);
						client.register(ch_selector, SelectionKey.OP_READ);
						
					}
					
					else if ( ready_key.isReadable() ) {
						// a client is sending a request on his channel
						SocketChannel client = (SocketChannel) ready_key.channel();
						try { 
							buffer.clear(); 
							readRequest(client, buffer); 
						}
						catch (UnsupportedEncodingException encode_ex) {
							encode_ex.printStackTrace();
						}
						catch (IOException io_ex) { // client closed connection
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

	/* Reds incoming request message from 'client' SocketChannel and calls the corresponding serving function */
	private static void readRequest(SocketChannel client, ByteBuffer request) throws Exception {

		ByteBuffer buffer = null;
		
		// reads all data received on the socket for the current client request message
		ByteArrayOutputStream byte_stream = new ByteArrayOutputStream();
		byte[] r_bytes;	// array cointaining all received bytes
		int total_read = 0;
		while ( client.read(request) > 0) {
			total_read += request.position();
			request.flip();
			r_bytes = request.array();
			byte_stream.write(r_bytes);
			request.clear();
		}
		if (total_read == 0) {
			sendResponse(client, Config.UNKNOWN_ERROR);
			throw new IOException();
		}
		byte[] request_bytes = byte_stream.toByteArray();
		byte_stream.close();
		buffer = ByteBuffer.allocate(request_bytes.length);
		buffer.put(request_bytes);
		buffer.flip();


		byte r_type, user_s, pass_s;
		byte[] username, file_name, dest_name, text_b;
		byte file_name_s, dest_name_s;
		int text_length, section;
		String user, file, dest;
		User currentUser;
		Document currentDoc;
		
		r_type = buffer.get(); // reads request type code
		/* processing client request */
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
				System.out.println("User " + user + " logged in");
				loginUser(client, user, pass);
				break;
				
			/* logout request */
			case Config.LOGOUT_R :
				user_s = buffer.get();
				username = new byte[user_s];
				buffer.get(username, 0, user_s);
				user = new String(username, Config.DEFAULT_ENCODING);
				loggedUsers.remove(user);
				System.out.println("User " + user + " logged out");
				sendResponse(client, Config.SUCCESS);
				break;
				
			/* new document creation request */
			case Config.NEW_R :
				user_s = buffer.get();
				file_name_s = buffer.get(); //file name length
				int sections	= buffer.getInt(); //number of sections
				username = new byte[user_s];
				buffer.get(username, 0, user_s);
				user = new String(username, Config.DEFAULT_ENCODING);
				file_name = new byte[file_name_s];
				buffer.get(file_name, 0, file_name_s);
				file = new String(file_name, Config.DEFAULT_ENCODING);
				System.out.println("User " + user + " created new document: " + file);
				newDocCreate(client, user, file , sections);
				break;
				
			/* list user documents request */
			case Config.LIST_R :
				user_s = buffer.get();
				username = new byte[user_s];
				buffer.get(username, 0, user_s);
				user = new String(username, Config.DEFAULT_ENCODING);
				System.out.println("User " + user + " requested documents list");
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
				System.out.println("User " + user + " shared file " + file + " with user " + dest);
				shareDoc(client, user, dest, file);
				break; 
				
			/* "show" and "show section" requests */
			case Config.SHOW_R:	
				user_s = buffer.get(); 
				file_name_s = buffer.get();
				username = new byte[user_s];
			    file_name = new byte[file_name_s];
			    buffer.get(username, 0, user_s);
			    buffer.get(file_name, 0, file_name_s);
			    section = buffer.getInt();
			    user = new String(username, Config.DEFAULT_ENCODING);
				file = new String(file_name, Config.DEFAULT_ENCODING);
				if (section == -1) {
					System.out.println("User " + user + " requested file " + file + " (VIEW MODE)");

				} else {
					System.out.println("User " + user + " requested file section " + section + " of file" + file + " (VIEW MODE)");
				}
				sendFile(client, user, file, section);
				break;
			
			/* edit section request */
			case Config.EDIT_R:
				user_s = buffer.get(); 
				file_name_s = buffer.get();
				username = new byte[user_s];
			    file_name = new byte[file_name_s];
			    buffer.get(username, 0, user_s);
			    buffer.get(file_name, 0, file_name_s);
			    section = buffer.getInt();
			    user = new String(username, Config.DEFAULT_ENCODING);
				file = new String(file_name, Config.DEFAULT_ENCODING);
				dbLock.lock();
				currentUser = usersDB.get(user);
				dbLock.unlock();
				currentDoc = currentUser.getDocument(file);
				System.out.println("User " + user + " requested file " + file + " (EDIT MODE)");
				if (currentDoc.getStatus(section) == Config.IN_EDIT) { // file section currently in edit by other user
					byte[] editor = (editingSessions.get(file + "_" + section)).getBytes(Config.DEFAULT_ENCODING);
					ByteBuffer response = ByteBuffer.allocate(Config.BUF_SIZE);
					response.put(Config.IN_EDIT);
					response.putInt(editor.length);
					response.put(editor);
					response.flip();
					try {
						client.write(response);
					} catch (IOException io_ex) {
						io_ex.printStackTrace();
					}
				} 
				else {
					sendFile(client, user, file, section);			// send requested file
					putUserInGroup(client, file, user);				// adds user to chat group 
					currentDoc.setStatus(Config.IN_EDIT, section);
					editingSessions.put(new String(file + "_" + section), user);
					currentUser.setSessionStatus(file, section);
				}
				break;
				
			/* client closed editing session */
			case Config.END_EDIT_R:
				user_s = buffer.get(); 
				file_name_s = buffer.get();
			    section = buffer.getInt();
				username = new byte[user_s];
			    file_name = new byte[file_name_s];
			    buffer.get(username, 0, user_s);
			    buffer.get(file_name, 0, file_name_s);
			    user = new String(username, Config.DEFAULT_ENCODING);
				file = new String(file_name, Config.DEFAULT_ENCODING);
				dbLock.lock();
				currentUser = usersDB.get(user); 
				dbLock.unlock();
				currentDoc = currentUser.getDocument(file);
				currentDoc.setStatus(Config.FREE_SECTION, section);
				System.out.println("User " + user + " terminated editing on file " + file + ", section " + section);
				removeUserFromGroup(user, file);	// removes user from chat groups. NOTE: deletes the group if no other users are working on it
				break;
				
			/* gets new file version from client */
			case Config.SAVE_R:
				user_s = buffer.get();
				file_name_s = buffer.get();
				text_length = buffer.getInt();
				section = buffer.getInt();
				username = new byte[user_s];
				file_name = new byte[file_name_s];
				text_b = new byte[text_length];
				buffer.get(username, 0, user_s);
				buffer.get(file_name, 0, file_name_s);
				buffer.get(text_b, 0, text_length);
				user = new String(username, Config.DEFAULT_ENCODING);
				file = new String(file_name, Config.DEFAULT_ENCODING);
				System.out.println("User " + user + " uploaded new version of file " + file + ", section " + section);
				updateFile(client, user, file, text_b, section);
				break;
				
			/* sets up a notification channel for the client */
			case Config.NOTIFY_SERV_R:
				user_s = buffer.get();
				username = new byte[user_s];
				buffer.get(username, 0, user_s);
				user = new String(username, Config.DEFAULT_ENCODING);
				dbLock.lock();
				User current_user = usersDB.get(user);
				dbLock.unlock();
				current_user.addNotificationChannel(client);
				System.out.println("Notification service started for user " + user);
				sendResponse(client, Config.SUCCESS);
				/* if user had offline notifications, send them now */
				if (pendingNotifications.containsKey(user)) {
					String message = new String("");
					Iterator<String> it = pendingNotifications.get(user).iterator();
					while (it.hasNext()) {
						message = message.concat(it.next() + "\n");
					}
					sendNotification(client, message);
					pendingNotifications.remove(user);
				}
				break;
				
			default	: 
				System.out.print("UNKNOWN REQUEST CODE\n");
				break;
		}
	}
	
	/* User login */
	private static void loginUser(SocketChannel client, String username, char[] password) {
		
		// checking if user is registered
		dbLock.lock();
		if ( !usersDB.containsKey(username) ) {
			dbLock.unlock();
			sendResponse(client, Config.UNKNOWN_USER);
			return;
		}
		
		// checking password validity
		User user = usersDB.get(username);
		dbLock.unlock();
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
	
	/* Creates a new file */
	private static void newDocCreate(SocketChannel client, String username, String filename, int sections) {
		
		// check if user is already online
		if ( !loggedUsers.containsKey(username)) {
			sendResponse(client, Config.UNKNOWN_USER);
			return;
		}
		
		String pathName = new String(Config.FILE_PATH + username + "\\" + filename);
		Path savePath = Paths.get(pathName);
		try {
			Files.createDirectories(savePath);
			for (int i=0; i<sections; i++) {
				Path filePath = Paths.get(pathName + "\\" + filename + "_"+ Integer.toString(i) + ".txt");
				Files.createFile(filePath);
			}
		} catch (IOException io_ex) {
			io_ex.printStackTrace();
			sendResponse(client, Config.UNKNOWN_ERROR);
		}
		
		dbLock.lock();
		User user = usersDB.get(username);
		dbLock.unlock();
		Document document = new Document(filename, username, sections, Config.CREATOR);
		user.addFile(document);
		sendResponse(client, Config.SUCCESS);
	}
	
	/* Sends a list of all proprietary and authorized documents for this user */
	private static void listDocs(SocketChannel client, String username) {
		if ( !loggedUsers.containsKey(username)) {
			sendResponse(client, Config.UNKNOWN_USER);
			return;
		}
		
		dbLock.lock();
		User user = usersDB.get(username);
		dbLock.unlock();
		Iterator<Document> it = user.getFileIterator();
		if (!it.hasNext()) { //empty documents list
			sendResponse(client, Config.EMPTY_LIST);
			return;
		}
		ByteBuffer buffer = ByteBuffer.allocate(Config.BUF_SIZE);
		buffer.put(Config.SUCCESS);
		try {
			while (it.hasNext()) 
			{
				Document temp = it.next();
				byte[] data_fn = new byte[temp.getTitle().length()*2];
				data_fn = temp.getTitle().getBytes(Config.DEFAULT_ENCODING);
				int s = temp.getSectionCount();
				byte[] sb = Integer.toString(s).getBytes();
				buffer.put("-".getBytes(Config.DEFAULT_ENCODING)); 
				buffer.put(data_fn);							   
				buffer.put("-".getBytes(Config.DEFAULT_ENCODING)); 
				buffer.put(sb);									   
				buffer.put("-".getBytes(Config.DEFAULT_ENCODING)); 
			}
			buffer.flip();
			client.write(buffer);
		} catch (IOException io_ex) {
			io_ex.printStackTrace();
			sendResponse(client, Config.UNKNOWN_ERROR);
		}		
	}
	
	/* Adds document 'file' to receivers documents list */
	private static void shareDoc(SocketChannel client, String sender, String receiver, String file) {
		
		if (sender.equals(receiver)) { 
			sendResponse(client, Config.INVALID_DEST);
			return;
		}
		
		dbLock.lock();
		User recvr = usersDB.get(receiver);
		dbLock.unlock();
		if ( recvr == null) { 
			sendResponse(client, Config.UNKNOWN_USER);
			return;
		}
		
		if ( recvr.getDocument(file) != null) {
			sendResponse(client, Config.INVALID_DEST);
			return;
		}
		
		dbLock.lock();
		User sendr = usersDB.get(sender);
		dbLock.unlock();
		Document source = sendr.getDocument(file);
		if (source == null) {
			sendResponse(client, Config.NO_SUCH_FILE);
			return;
		}
		recvr.addFile(source);
		sendResponse(client, Config.SUCCESS);
		
		/* Notify receiver */
		String message = new String(sender + " shared file '" + file + "' with you");
		
		if (!loggedUsers.containsKey(receiver)) { // add notification to pending notifications queue for this receiver
			if (pendingNotifications.containsKey(receiver)) { // notification list already exists for this user
				pendingNotifications.get(receiver).add(message);
			} else { // create new notification list
				ArrayList<String> notifications_list = new ArrayList<String>();
				notifications_list.add(message);
				pendingNotifications.put(receiver, notifications_list);
			}
		} else { // send notification directly to receiver if online
			
			SocketChannel notify_ch = recvr.getNotificationChannel();
			sendNotification(notify_ch, message);
		}
		
		return;
	}

	/* Sends file 'filename' to user 'username' */
	private static void sendFile(SocketChannel client, String username, String filename, int section_number) {
		dbLock.lock();
		User user = usersDB.get(username);
		dbLock.unlock();
		Document doc = user.getDocument(filename);
		String text = null;	
		
		if ( section_number == -1) {  // retrieves entire document from DB
			text = loadFile(doc, doc.getOwner(), filename);
		}
		else {  // retrieves requested section from DB
			text = loadFileSection(doc, doc.getOwner(), filename, section_number);
		}
		
		try {
			byte[] text_b = text.getBytes(Config.DEFAULT_ENCODING);
			int text_size = text_b.length;
			ByteBuffer send = ByteBuffer.allocate(text_size + Integer.SIZE);
			send.put(Config.SUCCESS);
			send.putInt(text_size);
			send.put(text_b);
			send.flip();
			try {
				client.write(send);
			} catch (IOException io_ex) {
				io_ex.printStackTrace();
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
	
	/* Save a new version of file 'filename' on disk*/
	private static void updateFile(SocketChannel client, String username, String filename, byte[] file_text, int section) {

		dbLock.lock();
		User user = usersDB.get(username);
		dbLock.unlock();
		Document document = user.getDocument(filename);
		String owner = new String(document.getOwner());
		
		String pathName = new String(Config.FILE_PATH + owner + "\\" + filename);
		try {
			Path filePath = Paths.get(pathName + "\\" + filename + "_" + Integer.toString(section) + ".txt");
			Files.write(filePath, file_text, StandardOpenOption.WRITE);
		} catch (IOException io_ex) {
			io_ex.printStackTrace();
			sendResponse(client, Config.UNKNOWN_ERROR);
			return;
		}
		sendResponse(client, Config.SUCCESS);
	}
	
	/* Loads file 'filename' from disk */
	private static String loadFile(Document doc, String username, String filename) {
		try {
			String pathName = new String(Config.FILE_PATH + username + "\\" + filename);
			Path dirPath = Paths.get(pathName);
			String text = new String();
			Stream<Path> paths = Files.list(dirPath);
			Iterator<Path> it = paths.iterator();
			int i=0;
			while (it.hasNext()) {
				Path currentPath = it.next();
				String currentSect = new String(Files.readAllBytes(currentPath));
				if (doc.getStatus(i) == Config.IN_EDIT) {
					String editor = new String(editingSessions.get(filename + "_" + i));
					text = text.concat("(section " + i + ", in edit by " + editor + ")\n").concat(currentSect + "\n");
				}
				else {
					text = text.concat("(section " + i + ")\n").concat(currentSect + "\n");
				}
				i++;
			}
			paths.close();
			return text;
		} catch (IOException io_ex) {
			io_ex.printStackTrace();
			return null;
		}
	}
	
	/* Loads section 'section_n' of file 'filename' from disk */
	private static String loadFileSection(Document doc, String username, String filename, int section_n) {
		String pathName = new String(Config.FILE_PATH + username + "\\" + filename);
		Path filePath = Paths.get(pathName + "\\" + filename + "_" + Integer.toString(section_n) + ".txt");
		String text = new String("");
		if (doc.getStatus(section_n) == Config.IN_EDIT) {
			String editor = new String(editingSessions.get(filename + "_" + section_n));
			text = text.concat("(In edit by " + editor + ")\n");
		}
		try {
			text = text.concat(new String(Files.readAllBytes(filePath)));
		} catch (IOException io_ex) {
			io_ex.printStackTrace();
		}
		return text;
	}
	
	/* Sends an operation response code to this 'client' */
	private static void sendResponse(SocketChannel client, byte outcome) {
		
		ByteBuffer response = ByteBuffer.allocate(1);
		response.put(outcome);
		response.flip();
		
		try {
			client.write(response); 
		}
		catch (IOException io_ex) { io_ex.printStackTrace(); }
		
	}
	
	/* Adds user 'username' to correspondig chat group */
	private static void putUserInGroup(SocketChannel client, String filename, String username) {
		String group_address;
		if (chatGroups.containsKey(filename)) {
			group_address = (chatGroups.get(filename)).getGroupAddress();
			(chatGroups.get(filename)).addUser(username);
			System.out.println("User " + username + " added to work group of document " + filename);
		} else {
			while ((group_address = getFreeChatGroupAddress()) == null ) {}
			ChatGroup group = new ChatGroup(group_address);
			group.addUser(username);
			chatGroups.put(filename, group);
			System.out.println("New work group created for document " + filename);
		}
		sendAddress(client, group_address); // send chat group address to the client
		/* notifies all users in this group that 'username' joined the work group*/
		try {
			DatagramSocket socket = new DatagramSocket();
			InetAddress address = InetAddress.getByName(group_address);
			String text = "'" + username + "' joined group\n";
			byte[] buffer = new byte[256];
			buffer = text.getBytes(Config.DEFAULT_ENCODING);
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, Config.CHAT_SERVICE_PORT);
			socket.send(packet);
			socket.close();
		} catch (IOException io_ex) { io_ex.printStackTrace(); }
	}
	
	/* Removes user 'username' from the chat group he was previously added to*/
	private static void removeUserFromGroup(String username, String filename) {
		ChatGroup group = chatGroups.get(filename);
		group.removeUser(username);
		if (group.openSections == 0) {
			chatGroups.remove(filename);
		}
		/* notify all users in this group (if exists any) that 'username' left the work group*/
		if (chatGroups.containsKey(filename)) {
			String group_address = chatGroups.get(filename).groupAddress;
			try {
				DatagramSocket socket = new DatagramSocket();
				InetAddress address = InetAddress.getByName(group_address);
				String text = "'" + username + "' left group\n";
				byte[] buffer = new byte[256];
				buffer = text.getBytes(Config.DEFAULT_ENCODING);
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, Config.CHAT_SERVICE_PORT);
				socket.send(packet);
				socket.close();
			} catch (IOException io_ex) { io_ex.printStackTrace(); }
		}
		
	}
	
	/* Generates a new random UDP Multicast address */
	private static String getFreeChatGroupAddress() {
		String rnd_generated = "239.255.";	// Class D address for UDP based Multicast service
		Random r = new Random();
		rnd_generated = rnd_generated.concat(r.nextInt(256) + "." + r.nextInt(256));
		try {
			InetAddress address = InetAddress.getByName(rnd_generated);
			if (address.isReachable(10)) {
				return null;
			}
		} catch (UnknownHostException e) {
			return null;
		} catch (IOException e) {
			return null;
		}
		
		return rnd_generated;
	}
	
	/* Sends an 'address' to 'client' as a String */
	private static void sendAddress(SocketChannel client, String address) {
		
		try {
			ByteBuffer buffer = ByteBuffer.allocate(16);
			buffer.put(address.getBytes(Config.DEFAULT_ENCODING));
			buffer.flip();
			client.write(buffer);

		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/* Sends a notification message to 'client' */
	private static void sendNotification(SocketChannel client, String message) {
		try {	
			ByteBuffer buffer = ByteBuffer.allocate(Config.BUF_SIZE);
			buffer.putInt(message.getBytes().length);
			buffer.put(message.getBytes(Config.DEFAULT_ENCODING));
			buffer.flip();
			client.write(buffer);
		} catch (IOException io_ex) {io_ex.printStackTrace();}
	}
	
	/* Recursively deletes all folders & files up to 'path' folder */
	private static void cleanUpUtility(Path path) throws IOException {
		
		if (Files.exists(path)) {
			if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
				try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
					for (Path entry : entries) {
						cleanUpUtility(entry);
					}
			    }
			}
			Files.delete(path);
		}
	}
}
