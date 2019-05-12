package turing_pkg;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface TuringRemoteService extends Remote {

	boolean registerOP (String username, char[] password) throws RemoteException;
	
}
