package turing_pkg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class User {
	
	private String username;
	private char[] password;
	private ArrayList<Document> documents;
	
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
	
	public Document getFile(String filename) {
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
} 