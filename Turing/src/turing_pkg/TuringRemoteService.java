package turing_pkg;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface TuringRemoteService extends Remote {

	/* remote account registration method */
	boolean registerOP (String username, char[] password) throws RemoteException;
	
}
