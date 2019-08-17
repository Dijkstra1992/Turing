package turing_pkg;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

public class NotificationHandler implements Runnable {
	
	private static SocketChannel notification_ch = null;
	private static JFrame window;

	public NotificationHandler(SocketChannel channel, JFrame frame) {
		notification_ch = channel;
		window = frame;
	}
	
	@Override
	public void run() {
		ByteBuffer buffer = ByteBuffer.allocate(Config.BUF_SIZE);
		try {
			System.out.println("Notification Handler started : " + notification_ch.getLocalAddress());
		} catch (IOException e) {
			e.printStackTrace();
		}
		while(!Thread.currentThread().isInterrupted()) {
			try {
				while ((notification_ch.read(buffer)) <= 0) {
					Thread.sleep(5000);
					System.out.println("Listening for notifications...");
				}
				buffer.flip();
				byte[] message_b = buffer.array();
				String message = new String(message_b, Config.DEFAULT_ENCODING);
				System.out.println("NOTIFICATION -> " + message);
				JOptionPane.showMessageDialog(window, message);
				buffer.clear();
			} catch (UnsupportedEncodingException encode_ex) {
				encode_ex.printStackTrace();
			} catch (IOException io_ex) {
				io_ex.printStackTrace();
				Thread.currentThread().interrupt();
			} catch (InterruptedException interrupt_ex) {
				System.out.println("Notification handler stopped");
				return;
			}
		}
	}

}
