package turing_pkg;

import java.util.LinkedList;

public class ChatGroup {

	int openSections;
	LinkedList<String> workingUsers;
	String groupAddress;
	
	public ChatGroup(String address) {
		this.openSections = 0;
		this.groupAddress = new String(address);
		this.workingUsers = new LinkedList<String>();
	}
	
	public void addUser(String username) {
		workingUsers.add(username);
		openSections++;
	}
	
	public void removeUser(String username) {
		workingUsers.remove(username);
		openSections--;
	}
	
	public int openSections() {
		return this.openSections;
	}
	
	public String getGroupAddress() {
		return this.groupAddress;
	}
}
