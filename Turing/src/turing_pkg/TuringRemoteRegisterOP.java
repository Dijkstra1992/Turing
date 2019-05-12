package turing_pkg;

import java.rmi.RemoteException;

public class TuringRemoteRegisterOP implements TuringRemoteService{
	
	public TuringRemoteRegisterOP () {
	}

	@Override
	synchronized public boolean registerOP(String username, char[] password) throws RemoteException {
		if ( TuringServer.usersDB.containsKey(username) ) {
			return false;
		}
		else {
			User user = new User(username, password);
			TuringServer.usersDB.put(username, user);
			System.out.println("Registered new user: " + username);
			
			return true;
		}	
	}

} 