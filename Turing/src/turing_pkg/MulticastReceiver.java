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
	private boolean ACTIVE;
	private JTextArea chat_box;
	
	public MulticastReceiver(InetAddress chatAddress, JTextArea chat_box) {
		this.group = chatAddress;
		this.buffer = new byte[256];
		this.ACTIVE = true;
		this.chat_box = chat_box;
	}

	@Override
	public void run() {
	
		try {
			socket = new MulticastSocket(Config.CHAT_SERVICE_PORT);
			socket.joinGroup(group);
			System.out.println("MULTICAST RECEIVER READY");
			
			while (ACTIVE) {
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				try { socket.receive(packet); } catch (SocketException sock_ex) { System.out.println("Chat socket closed"); }
				String message = new String(packet.getData(), 0, packet.getLength());
				if (message.isEmpty()) break;
				chat_box.append(message + "\n");
			}
		} catch (IOException e) { e.printStackTrace(); }
	}
	
	
	public void disableChat() {
		try {
			ACTIVE = false;
			socket.leaveGroup(group);
			socket.close();
			System.out.println("MULTICAST RECEIVER INTERRUPTED!!!");
		} catch (SocketException sock_ex) {
			System.out.println("Chat socket closed");
		} catch (IOException io_ex) {
			io_ex.printStackTrace();
		}
		
	}

}
