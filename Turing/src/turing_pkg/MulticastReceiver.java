package turing_pkg;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;

import javax.swing.JTextArea;

public class MulticastReceiver extends Thread {
	
	private MulticastSocket socket;
	private InetAddress	group;
	private byte[] buffer;
	private JTextArea chat_box;
	
	public MulticastReceiver(InetAddress chatAddress, JTextArea chat_box) {
		this.group = chatAddress;
		this.buffer = new byte[256];
		this.chat_box = chat_box;
	}
 
	@Override
	public void run() {
	
		try {
			socket = new MulticastSocket(Config.CHAT_SERVICE_PORT);
			socket.joinGroup(group);
			
			while (true) {
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				socket.receive(packet); 
				String message = new String(packet.getData(), 0, packet.getLength());
				if (message.isEmpty()) break;
				chat_box.append(message + "\n");
			}
		} catch (SocketException sock_ex) { 
			System.out.println("Chat service interrupted");
		} catch (IOException io_ex) { 
			io_ex.printStackTrace();
		}

	}
	
	
	public void disableChat() {
		try {
			socket.leaveGroup(group);
			socket.close();
		} catch (SocketException sock_ex) {
			System.out.println("Chat socket closed: " + sock_ex.getCause());
		} catch (IOException io_ex) {
			System.out.println("Error leaving chat group: " + io_ex.getCause());
		}
		
	}

}

