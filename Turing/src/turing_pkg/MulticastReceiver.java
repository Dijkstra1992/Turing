package turing_pkg;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class MulticastReceiver extends Thread {
	
	private MulticastSocket socket;
	private InetAddress	group;
	private byte[] buffer;
	
	public MulticastReceiver(InetAddress chatAddress) {
		this.group = chatAddress;
		this.buffer = new byte[256];
	}
	
	@Override
	public void run() {
	
		try {
			socket = new MulticastSocket(Config.CHAT_SERVICE_PORT);
			socket.joinGroup(group);
			System.out.println("MULTICAST RECEIVER READY");
			
			while (!isInterrupted()) {
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				socket.receive(packet);
				String message = new String(packet.getData(), 0, packet.getLength());
				System.out.println(message);
			}
			
		} catch (IOException e) { e.printStackTrace(); }
		
		
		
	}

}
