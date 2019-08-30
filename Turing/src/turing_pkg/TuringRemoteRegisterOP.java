package turing_pkg;

import java.rmi.RemoteException;

public class TuringRemoteRegisterOP implements TuringRemoteService{
		
	public TuringRemoteRegisterOP () {
	}

	/* Creates a new user account and saves the credentials into the server's main database */
	public boolean registerOP(String username, char[] password) throws RemoteException {
		TuringServer.dbLock.lock();
		if ( TuringServer.usersDB.containsKey(username) ) {
			TuringServer.dbLock.unlock();
			return false;
		}
		else {
			User user = new User(username, password);
			TuringServer.usersDB.put(username, user);
			System.out.println("Registered new user: " + username);
			TuringServer.dbLock.unlock();
			return true;
		}	
	}

}   