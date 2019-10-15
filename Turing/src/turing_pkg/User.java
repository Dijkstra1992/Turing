package turing_pkg;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class User {
	
	private String username;
	private char[] password;
	private ArrayList<Document> documents;
	private SocketChannel notification_ch;
	private String currentSession = null;
	private int sessionIndex = -1;
	
	public User (String username, char[] password) {
		this.username = new String(username);
		this.password = Arrays.copyOf(password, password.length);
		this.documents = new ArrayList<Document>();	
		
	}
	
	public String getName() {
		return this.username;
	}
	 
	public char[] getPass() {
		return this.password;
	}
 
	public void addFile(Document file) {
		documents.add(file);
	}
	
	public Document getDocument(String filename) {
		Iterator<Document> it = documents.iterator();
		while (it.hasNext()) {
			Document current = it.next();
			if (current.getTitle().equals(filename)) {
				return current;
			}
		}
		return null;
	}
	
	public Iterator<Document> getFileIterator() {
		return this.documents.iterator();
	}
	
	public void addNotificationChannel(SocketChannel channel) {
		this.notification_ch = channel;
	}
	
	public SocketChannel getNotificationChannel() {
		return this.notification_ch;
	}
	
	public void setSessionStatus(String file_section_name, int index) {
		if (file_section_name == null) {
			currentSession = null;
		}
		else {
			currentSession = new String(file_section_name);
		}
		sessionIndex = index;
	}

	public boolean hasOpenSessions() {
		return (currentSession != null);
	}

	public String getOpenSessionName() {
		return currentSession;
	}

	public int getOpenSessionIndex() {
		return sessionIndex;
	}
} 