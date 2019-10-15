package turing_pkg;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import javax.swing.*;


public class NotificationHandler implements Runnable {
	
	private static SocketChannel notification_ch = null;
	int size;
	byte data[] = null;
	
	public NotificationHandler(SocketChannel channel) {
		notification_ch = channel;
	}
	
	@Override
	public void run() {
		ByteBuffer buffer = ByteBuffer.allocate(Config.BUF_SIZE);
		
		while(!Thread.currentThread().isInterrupted()) {
			try {
				while (notification_ch.read(buffer) <= 0) {
					Thread.sleep(1000);
				}
				buffer.flip();
				size = buffer.getInt();
				data = new byte[size];
				buffer.get(data, 0, size);
				String message = new String(data, Config.DEFAULT_ENCODING);
				displayNotification(message);
				buffer.clear();
				
			} catch (UnsupportedEncodingException encode_ex) {
				System.out.println("Error in message encoding: " + encode_ex.getMessage());
			} catch (IOException io_ex) {
				System.out.println("Notification channel error: " + io_ex.getCause());
				Thread.currentThread().interrupt();
			} catch (InterruptedException interrupt_ex) {
				try {
					notification_ch.close();
					return;
				} catch (IOException e) {
					e.printStackTrace();
				}
				
			}
		}
	}
	
	private void displayNotification(String message) {
		
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			e.printStackTrace();
		}
		JFrame frame = new JFrame();
		frame.setSize(300,125);
		frame.setUndecorated(true);
		frame.setLayout(new GridBagLayout());
		
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.weightx = 1.0f;
		constraints.weighty = 1.0f;
		constraints.insets = new Insets(5, 5, 5, 5);
		constraints.fill = GridBagConstraints.BOTH;
		
		JLabel headingLabel = new JLabel("New invite received");
		headingLabel.setOpaque(false);
		frame.add(headingLabel, constraints);
		
		constraints.gridx++;
		constraints.weightx = 0f;
		constraints.weighty = 0f;
		constraints.fill = GridBagConstraints.NONE;
		constraints.anchor = GridBagConstraints.NORTH;
				
		@SuppressWarnings("serial")
		JButton cloesButton = new JButton(new AbstractAction("X") {
			@Override
			public void actionPerformed(ActionEvent evt) {
				frame.dispose();
			}
		});
		cloesButton.setMargin(new Insets(1, 4, 1, 4));
		cloesButton.setFocusable(false);
		frame.add(cloesButton, constraints);
		
		// Notification pop-up will be closed after 5 seconds
		Timer timer = new Timer(5000, new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				frame.dispose();
			}
		});
		timer.setRepeats(false);
		
		constraints.gridx = 0;
		constraints.gridy++;
		constraints.weightx = 1.0f;
		constraints.weighty = 1.0f;
		constraints.insets = new Insets(5, 5, 5, 5);
		constraints.fill = GridBagConstraints.BOTH;
		
		JLabel messageLabel = new JLabel(message);
		frame.add(messageLabel, constraints);
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		Insets toolHeight = Toolkit.getDefaultToolkit().getScreenInsets(frame.getGraphicsConfiguration());
		frame.setLocation(screenSize.width - frame.getWidth(), screenSize.height - toolHeight.bottom - frame.getHeight());
		
		frame.setVisible(true);
		timer.start();

		
	}

}
