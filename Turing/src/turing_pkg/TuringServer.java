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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/* my utility class */
import turing_pkg.Config;

public class TuringServer {
	
	public static Map<String, User> usersDB; 	 						// registered users (<username, User>)
	public static Lock dbLock;											// usersDB lock
	private static Map<String, SocketChannel> loggedUsers;				// online users (<username, client_socket_channel>)
	private static Map<String, String> editingSessions;					// list of currently open sections for editing (<file_section_name, username>)
	private static Map<String, ChatGroup> chatGroups;					// chat groups (<filename, chatgroup object>)
	private static Map<String, ArrayList<String>> pendingNotifications;	// <username, notification_messages_list>
	private static Map<SocketChannel, byte[]> pendingResponses;			// queue of pending responses to be send
	private static ServerSocketChannel server_ch = null;
	private static Selector ch_selector = null;
	
	/* main accept routine method */
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
		pendingResponses = new HashMap<SocketChannel, byte[]>();
		Set<SelectionKey> key_set;
		Iterator<SelectionKey> key_iterator;

		/* Clean up any existing files from previous executions */
		System.out.println("Removing previous execution files...");
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
					key_iterator.remove();

					if ( ready_key.isAcceptable() ) {

						SocketChannel client = server_ch.accept();
						client.configureBlocking(false);
						client.register(ch_selector, SelectionKey.OP_READ);
						
					}
					
					else if ( ready_key.isReadable() ) {
						
						SocketChannel client = (SocketChannel) ready_key.channel();
						try { 
							buffer.clear(); 
							readRequest(client, buffer);

						}
						catch (UnsupportedEncodingException encode_ex) {
							encode_ex.printStackTrace();
						}
						catch (IOException io_ex) {
							disconnectClient(client);
							client.close();
							ready_key.cancel();
						}
					}	
					
					else if ( ready_key.isWritable() ) {

						SocketChannel client = (SocketChannel) ready_key.channel();
						byte[] response_b = (byte[]) ready_key.attachment();
						ByteBuffer responseBuffer = ByteBuffer.allocate(response_b.length);
						responseBuffer.put(response_b);
						responseBuffer.flip();
						client.write(responseBuffer);
						ready_key.interestOps(SelectionKey.OP_READ);

					}
				}
			} catch (Exception select_ex) {
				select_ex.printStackTrace();
			}
		} 
	}

	/* Reads incoming request message from 'client' SocketChannel and calls the corresponding request processing function */
	private static void readRequest(SocketChannel client, ByteBuffer request) throws Exception {

		byte[] response;
		
		ByteBuffer buffer = null; 
		ByteArrayOutputStream byte_stream = new ByteArrayOutputStream();
		byte[] r_bytes;	// array cointaining all received bytes
		int total_read = 0;
		// reads all data received on the socket for the current client request message
		while ( client.read(request) > 0) { // reads up to Config.BUF_SIZE bytes and saves them into 'request' buffer
			total_read += request.position();
			request.flip();
			r_bytes = request.array();
			byte_stream.write(r_bytes); // temp cumulative buffer
			request.clear();
		}
		if (total_read == 0) {
			response = new byte[1];
			response[0] = Config.UNKNOWN_ERROR; 
			pendingResponses.put(client, response);
			throw new IOException();
		}
		byte[] request_bytes = byte_stream.toByteArray();
		byte_stream.close();
		buffer = ByteBuffer.allocate(request_bytes.length);	// transfer entire message into 'buffer' for further data extraction

		buffer.put(request_bytes);
		buffer.flip();

		byte r_type, user_s, pass_s;
		byte[] username, file_name, dest_name, text_b;
		byte file_name_s, dest_name_s;
		int text_length, section;
		String user, file, dest;
		User currentUser;
		Document currentDoc;
		
		// Start decoding request message and extracting data
		r_type = buffer.get(); // reads request type code
		switch (r_type) {// decoding client request 
		
			/* login request */
			case Config.LOGIN_R :
				user_s = buffer.get();
				pass_s = buffer.get(); 			
				username = new byte[user_s];
		 		buffer.get(username, 0, user_s);
				user = new String(username, Config.DEFAULT_ENCODING);				
				byte[] password = new byte[pass_s*2];
				buffer.get(password, 0, (pass_s*2));
				char[] pass = Config.toChars(password);
				if (loginUser(client, user, pass)) {
					System.out.println("User " + user + " logged in");
				}
				break;
				
			/* logout request */
			case Config.LOGOUT_R :
				user_s = buffer.get();
				username = new byte[user_s];
				buffer.get(username, 0, user_s);
				user = new String(username, Config.DEFAULT_ENCODING);
				loggedUsers.remove(user);
				System.out.println("User " + user + " logged out");
				response = new byte[1];
				response[0] = Config.SUCCESS; 
				client.keyFor(ch_selector).attach(response);
				client.keyFor(ch_selector).interestOps(SelectionKey.OP_WRITE);
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
				newDocCreate(client, user, file , sections);
				System.out.println("User " + user + " created new document: " + file);
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
				if (shareDoc(client, user, dest, file)) {
					System.out.println("User " + user + " shared file " + file + " with user " + dest);
				}
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
				appendFileToChannel(client, user, file, section); // prepare to send file on channel
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
				if (currentDoc.getStatus(section) == Config.IN_EDIT) { // file section currently in edit by other user
					
					byte[] editor_name = (editingSessions.get(file + "_" + section)).getBytes(Config.DEFAULT_ENCODING);
					ByteBuffer responseBuffer = ByteBuffer.allocate(Config.BUF_SIZE);
					responseBuffer.put(Config.IN_EDIT);
					responseBuffer.putInt(editor_name.length);
					responseBuffer.put(editor_name);
					responseBuffer.flip();
					response = new byte[responseBuffer.limit()];
					responseBuffer.get(response, 0, responseBuffer.limit());
					client.keyFor(ch_selector).attach(response);
					client.keyFor(ch_selector).interestOps(SelectionKey.OP_WRITE);
				} 
				else { 
					appendFileToChannel(client, user, file, section); // prepare to send file on channel
					currentDoc.setStatus(Config.IN_EDIT, section);
					editingSessions.put(new String(file + "_" + section), user);
					System.out.println("User " + user + " requested file section " + section + " of file " + file + " (EDIT MODE)");
				}
				break;
				
			case Config.CHAT_ADDRESS_R:
				user_s = buffer.get(); 
				file_name_s = buffer.get();
				username = new byte[user_s];
			    file_name = new byte[file_name_s];
			    buffer.get(username, 0, user_s);
			    buffer.get(file_name, 0, file_name_s);
			    user = new String(username, Config.DEFAULT_ENCODING);
			    file = new String(file_name, Config.DEFAULT_ENCODING);
			    putUserInGroup(client, file, user);
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
				removeUserFromGroup(user, file);
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
				byte[] r_code = new byte[1];
				r_code[0] = Config.SUCCESS;
				/* if user had offline notifications, send them now */
				if (pendingNotifications.containsKey(user)) { // if there are pending notifications, add them to the response message
					String message = new String("");
					Iterator<String> it = pendingNotifications.get(user).iterator();
					while (it.hasNext()) {
						message = message.concat(it.next() + "\n");
					}
					pendingNotifications.remove(user);
					byte[] notifications = new byte[message.length()];
					notifications = message.getBytes(Config.DEFAULT_ENCODING);
					response = new byte[1 + notifications.length];
					System.arraycopy(r_code, 0, response, 0, 1);
					System.arraycopy(notifications, 0, response, 1, notifications.length);
				}
				else {
					response = new byte[1];
					response[0] = Config.SUCCESS;
				}
				client.keyFor(ch_selector).attach(response);
				client.keyFor(ch_selector).interestOps(SelectionKey.OP_WRITE);
				break;
				
			default	: 
				System.out.print("UNKNOWN REQUEST CODE\n");
				response = new byte[1];
				response[0] = Config.UNKNOWN_ERROR;
				client.keyFor(ch_selector).attach(response);
				client.keyFor(ch_selector).interestOps(SelectionKey.OP_WRITE);
				break;
		}
	}
	
	/* User login */
	private static boolean loginUser(SocketChannel client, String username, char[] password) {
		
		byte[] response = new byte[1];
		client.keyFor(ch_selector).interestOps(SelectionKey.OP_WRITE);
		
		// checking if user is registered
		dbLock.lock();
		if ( !usersDB.containsKey(username) ) {
			dbLock.unlock();
			response[0] = Config.UNKNOWN_USER;
			client.keyFor(ch_selector).attach(response);
			return false;
		}
		
		// checking password 
		User user = usersDB.get(username);
		dbLock.unlock();
		char[] pass = user.getPass();

		for (int i=0; i<password.length; i++) {
			if (pass[i] != password[i]) {
				response[0] = Config.INVALID_PASS; 
				client.keyFor(ch_selector).attach(response);
				return false;
			}
		}
		
		// check if user is already online
		if ( loggedUsers.containsKey(username)) {
			response[0] = Config.ALREADY_ON;
			client.keyFor(ch_selector).attach(response);
			return false;
		}
		
		// user login
		loggedUsers.put(username, client);
		response[0] = Config.SUCCESS; 
		client.keyFor(ch_selector).attach(response);
		return true;
		
	}
	
	/* Creates a new document */
	private static void newDocCreate(SocketChannel client, String username, String filename, int sections) {
		
		byte[] response = null;
		
		dbLock.lock();
		User user = usersDB.get(username);
		dbLock.unlock();
		
		if (user.getDocument(filename) != null) {
			response = new byte[1];
			response[0] = Config.DUPLICATE_FILE;
			client.keyFor(ch_selector).attach(response);
			client.keyFor(ch_selector).interestOps(SelectionKey.OP_WRITE);
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
			response = new byte[1];
			response[0] = Config.UNKNOWN_ERROR;
			client.keyFor(ch_selector).attach(response);	
			client.keyFor(ch_selector).interestOps(SelectionKey.OP_WRITE);
		}
		
		
		Document document = new Document(filename, username, sections, Config.CREATOR);
		user.addFile(document);
		response = new byte[1];
		response[0] = Config.SUCCESS;
		client.keyFor(ch_selector).attach(response);
		client.keyFor(ch_selector).interestOps(SelectionKey.OP_WRITE);
	}
	
	/* Sends a list of all proprietary and shared documents with this user */
	private static void listDocs(SocketChannel client, String username) {
		
		byte[] response_b = new byte[1];
		int total = 0;
		int current = 0;
		
		if ( !loggedUsers.containsKey(username)) {
			response_b[0] = Config.UNKNOWN_USER;
			client.keyFor(ch_selector).attach(response_b);
			client.keyFor(ch_selector).interestOps(SelectionKey.OP_WRITE);
			return;
		}
		
		dbLock.lock();
		User user = usersDB.get(username);
		dbLock.unlock();
		Iterator<Document> it = user.getFileIterator();
		if (!it.hasNext()) { //empty documents list
			response_b[0] = Config.EMPTY_LIST;
			client.keyFor(ch_selector).attach(response_b);
			client.keyFor(ch_selector).interestOps(SelectionKey.OP_WRITE);
			return;
		}
		ByteBuffer buffer = ByteBuffer.allocate(Config.BUF_SIZE);
		buffer.put(Config.SUCCESS);
		try {
			while (it.hasNext()) {
				Document temp = it.next();
				byte[] filename_b = new byte[temp.getTitle().length()*2];
				int sections = temp.getSectionCount();
				filename_b = temp.getTitle().getBytes(Config.DEFAULT_ENCODING);
				byte[] sections_b = Integer.toString(sections).getBytes();
				buffer.put("-".getBytes(Config.DEFAULT_ENCODING)); 
				buffer.put(filename_b);							   
				buffer.put("-".getBytes(Config.DEFAULT_ENCODING)); 
				buffer.put(sections_b);									   
				buffer.put("-".getBytes(Config.DEFAULT_ENCODING)); 
				buffer.flip();				
				current = buffer.limit();
				total += current;
				response_b = Arrays.copyOf(response_b, total);
				buffer.get(response_b, total-current, current);
				buffer.clear();
			}
			client.keyFor(ch_selector).attach(response_b);
			client.keyFor(ch_selector).interestOps(SelectionKey.OP_WRITE);
		} catch (IOException io_ex) {
			io_ex.printStackTrace();
			response_b[0] = Config.UNKNOWN_ERROR;
			client.keyFor(ch_selector).attach(response_b);
			client.keyFor(ch_selector).interestOps(SelectionKey.OP_WRITE);
		}		
	}
	
	/* Adds document 'file' to 'receiver' documents list */
	private static boolean shareDoc(SocketChannel client, String sender, String receiver, String file) {
		
		byte[] response = null;
		client.keyFor(ch_selector).interestOps(SelectionKey.OP_WRITE);
		
		if (sender.equals(receiver)) { 
			response = new byte[1];
			response[0] = Config.INVALID_DEST;
			client.keyFor(ch_selector).attach(response);
			return false;
		}
		
		dbLock.lock();
		User recvr = usersDB.get(receiver);
		dbLock.unlock();
		if ( recvr == null) { 
			response = new byte[1];
			response[0] = Config.UNKNOWN_USER;
			client.keyFor(ch_selector).attach(response);
			return false;
		}
		
		if ( recvr.getDocument(file) != null) {
			response = new byte[1];
			response[0] = Config.DUPLICATE_FILE;
			client.keyFor(ch_selector).attach(response);
			return false;
		}
		
		dbLock.lock();
		User sendr = usersDB.get(sender);
		dbLock.unlock();
		Document source = sendr.getDocument(file);
		if (source == null) {
			response = new byte[1];
			response[0] = Config.NO_SUCH_FILE;
			client.keyFor(ch_selector).attach(response);
			return false;
		}
		if ( (source.getOwner().compareTo(sender)) != 0) {
			response = new byte[1];
			response[0] = Config.INVALID_PERM;
			client.keyFor(ch_selector).attach(response);
			return false;
		}
		recvr.addFile(source);
		response = new byte[1];
		response[0] = Config.SUCCESS;
		client.keyFor(ch_selector).attach(response);
		
		/* Notify receiver */
		String message = new String(sender + " shared file '" + file + "' with you");
		
		if (!loggedUsers.containsKey(receiver)) { // add notification to pending notifications queue for this receiver
			if (pendingNotifications.containsKey(receiver)) { // notification list already exists for this user
				pendingNotifications.get(receiver).add(message);
			} else { // create new notifications list
				ArrayList<String> notifications_list = new ArrayList<String>();
				notifications_list.add(message);
				pendingNotifications.put(receiver, notifications_list);
			}
		} else { // send notifications directly to receiver if online
			
			SocketChannel notify_ch = recvr.getNotificationChannel();
			sendNotification(notify_ch, message);
		}
		
		return true;
	}

	/* Sends file 'filename' to user 'username' */
	private static void appendFileToChannel(SocketChannel client, String username, String filename, int section_number) {
		
		byte[] response = null;
		String text = null;	
		
		dbLock.lock();
		User user = usersDB.get(username);
		dbLock.unlock();
		Document doc = user.getDocument(filename);
		
		if ( section_number == -1) {  // retrieves entire document from DB
			text = loadFile(doc, doc.getOwner(), filename);
		}
		else {  // retrieves requested section from DB
			text = loadFileSection(doc, doc.getOwner(), filename, section_number);
		}

		try {
			byte[] text_b = text.getBytes(Config.DEFAULT_ENCODING);
			int text_size = text_b.length;
			ByteBuffer responseBuffer = ByteBuffer.allocate(text_size + 5);
			responseBuffer.put(Config.SUCCESS);
			responseBuffer.putInt(text_size);
			responseBuffer.put(text_b);
			responseBuffer.flip();
			response = responseBuffer.array();
			client.keyFor(ch_selector).attach(response);
			client.keyFor(ch_selector).interestOps(SelectionKey.OP_WRITE);

		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
	
	/* Save a new version of file 'filename' on disk*/
	private static void updateFile(SocketChannel client, String username, String filename, byte[] file_text, int section) {
		
		byte[] response = new byte[1];

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
			response[0] = Config.UNKNOWN_ERROR;
			client.keyFor(ch_selector).attach(response);
			client.keyFor(ch_selector).interestOps(SelectionKey.OP_WRITE);
			return;
		}
		response[0] = Config.SUCCESS;
		client.keyFor(ch_selector).attach(response);
		client.keyFor(ch_selector).interestOps(SelectionKey.OP_WRITE);
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
	
	/* Adds user 'username' to correspondig chat group */
	private static void putUserInGroup(SocketChannel client, String filename, String username) {
		String group_address;
		if (chatGroups.containsKey(filename)) { // add user to existing work group and notify all 
			group_address = (chatGroups.get(filename)).getGroupAddress();
			(chatGroups.get(filename)).addUser(username);
			try {
				DatagramSocket socket = new DatagramSocket();
				InetAddress address = InetAddress.getByName(group_address);
				String text = "\t(" + username + " joined group)";
				byte[] buffer = new byte[256];
				buffer = text.getBytes(Config.DEFAULT_ENCODING);
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, Config.CHAT_SERVICE_PORT);
				socket.send(packet);
				socket.close();
			} catch (IOException io_ex) { io_ex.printStackTrace(); }
		} else { // create new work group for this file
			while ((group_address = getFreeChatGroupAddress()) == null ) {
				// wait until a valid multicast address is generated
			}
			ChatGroup group = new ChatGroup(group_address);
			group.addUser(username);
			chatGroups.put(filename, group);
			System.out.println("New work group created for document " + filename);
		}
		
		try {
			byte[] address_b = group_address.getBytes(Config.DEFAULT_ENCODING);
			client.keyFor(ch_selector).attach(address_b);
			client.keyFor(ch_selector).interestOps(SelectionKey.OP_WRITE);
			System.out.println("User " + username + " added to work group of document " + filename);
		} catch (UnsupportedEncodingException encode_ex) {
			encode_ex.printStackTrace();
		}
		
	}
	
	/* Removes user 'username' from the chat group. NOTE: deletes the group if no other users are working in it*/
	private static void removeUserFromGroup(String username, String filename) {
		ChatGroup group = chatGroups.get(filename);
		group.removeUser(username);
		if (group.openSections == 0) { // none of the current file sections are in edit mode => delete work-group
			chatGroups.remove(filename);
			System.out.println("Group empty --> closed!");
		}

		else {
			String group_address = chatGroups.get(filename).groupAddress;
			try {
				DatagramSocket socket = new DatagramSocket();
				InetAddress address = InetAddress.getByName(group_address);
				String text = "\t(" + username + " left group)";
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
		rnd_generated = rnd_generated.concat(r.nextInt(256) + "." + r.nextInt(255));
		try {
			InetAddress address = InetAddress.getByName(rnd_generated);
			if (address.isReachable(10)) {//address reachable means it is already bind 
				return null;
			}
		} catch (UnknownHostException e) {
			return null;
		} catch (IOException e) {
			return null;
		}
		
		System.out.println("Group address generated: " + rnd_generated);
		return rnd_generated;
	}

	/* Sends a notification message to 'client' */
	private static void sendNotification(SocketChannel client, String message) {
		try {	
			ByteBuffer buffer = ByteBuffer.allocate(Config.BUF_SIZE);
			buffer.putInt(message.getBytes().length);
			buffer.put(message.getBytes(Config.DEFAULT_ENCODING));
			buffer.flip();
			byte[] notifications = new byte[buffer.limit()];
			notifications = buffer.array();
			client.keyFor(ch_selector).attach(notifications);
			client.keyFor(ch_selector).interestOps(SelectionKey.OP_WRITE);
			System.out.println("Added notification message <" + message + "> to channel " 
					+ client.getRemoteAddress() + ", " + notifications.length + " bytes" );
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
	
	/* Client disconnection */
	private static void disconnectClient(SocketChannel client) {
		Set<Entry<String, SocketChannel>> usersQueue = loggedUsers.entrySet();
		Iterator<Entry<String, SocketChannel>> it = usersQueue.iterator();
		while (it.hasNext()) {
			Entry<String, SocketChannel> current = (Entry<String, SocketChannel>) it.next();
			if (current.getValue().equals(client)) {
				String username = new String(current.getKey());
				loggedUsers.remove(username);
				try {
					User current_user = usersDB.get(username);
					if (current_user.hasOpenSessions()) { // if client closed connection while in editing-mode, then clean all session info
						String session_name = new String(current_user.getOpenSessionName());
						int session_index = current_user.getOpenSessionIndex();
						current_user.getDocument(session_name).setStatus(Config.FREE_SECTION, session_index); // release document section
						current_user.setSessionStatus(null, -1);		// clean user session status
						editingSessions.remove(session_name);			// remove user from active editing sessions list
						ChatGroup group = chatGroups.get(session_name); 
						group.removeUser(username);						// remove user from chat group 
					}
					if (current_user.getNotificationChannel() != null) {// close user notification channel 
						current_user.getNotificationChannel().close();
					}
				} catch (IOException io_ex) { io_ex.printStackTrace(); }
				return;
			}
		}
	}
}
