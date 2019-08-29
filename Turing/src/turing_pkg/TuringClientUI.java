package turing_pkg;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.MouseInfo;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

/* Swing framework API's */
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

public class TuringClientUI {

	private static SocketChannel client_ch = null;
	private static SocketChannel notification_ch = null;
	private static DatagramSocket chatSocket = null;
	private static String userName;
	private static Map<String, Integer> documents;
	private static Registry turing_services;
	private static TuringRemoteService remoteOBJ;
	private static InetAddress chatAddress;
	private static MulticastReceiver mc_receiver;
	private static Thread notification_handler = null;
	private static byte CLIENT_STATUS;
	private static String session_name;
	private static int session_index;
	
	/* UI components */
	private static JFrame mainFrame;
	private static JFrame editorWindow;
	private static JPanel panel;
	private static JTextField statusBar;
	private static JTextArea chat_history;
	private static JTextField message_box;
	private static JList<String> file_explorer;
	private static DefaultListModel<String> list;
	private static JScrollPane explorer_scroll;
	private static JButton btnSend;
	private static JMenuBar menuBar;
	private static JMenu mnFile;
	private static JMenu mnAccount;
	private static JMenuItem mntmNew;
	private static JMenuItem mntmList;
	private static JMenuItem mntmRegister;
	private static JMenuItem mntmLogin;
	private static JMenuItem mntmLogout;
	private static JMenuItem mntmEdit;
	private static JMenuItem mntmShowSection;
	private static JMenuItem mntmShow;
	private static JMenuItem mntmShare;
	
	/****************************
	 * Launch the application.
	 ***************************/
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				documents = new HashMap<String, Integer>();
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
					initGUI();
					CLIENT_STATUS = Config.OFFLINE;
					mainFrame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**********************************************
	 * Initialize Graphic User Interface components
	 **********************************************/
	private static void initGUI() {		
		mainFrame = new JFrame("TURING");
		mainFrame.setResizable(false);
		mainFrame.setBounds(100, 100, 720, 640);
		mainFrame.setMinimumSize(new Dimension(720, 640));
		mainFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		mainFrame.addWindowListener(new WindowAdapter() {
			@Override
            public void windowClosing(WindowEvent e) {
				int selection;
				if (CLIENT_STATUS == Config.EDITING) { // If an editing session is still active
					selection = JOptionPane.showConfirmDialog(null, 
							"All changes to open files will be lost. Do you really want to exit ?",
							"Confirm close", 
							JOptionPane.YES_NO_OPTION,
							JOptionPane.QUESTION_MESSAGE);
					if ( selection == JOptionPane.YES_OPTION ) {
						try {
							endEditRequest(session_name, session_index);
							logoutRequest(); 
						} catch (IOException io_ex) { io_ex.printStackTrace(); }
						editorWindow.dispose();
						mainFrame.dispose();
						System.out.print("UI components released successfully");
						return;
					}
				}
				else if (CLIENT_STATUS == Config.ONLINE){
					selection = JOptionPane.showConfirmDialog(null, 
							"Do you really want to exit ?",
							"Confirm close", 
							JOptionPane.YES_NO_OPTION,
							JOptionPane.QUESTION_MESSAGE);
					if ( selection == JOptionPane.YES_OPTION ) {
						try {logoutRequest(); } catch (IOException io_ex) { io_ex.printStackTrace(); }
						mainFrame.dispose();
						System.out.print("UI components released successfully");
						return;
					}
				} else {
					mainFrame.dispose();
					return;
				}
			}
		});
		
		panel = new JPanel();
		panel.setForeground(Color.BLACK);
		mainFrame.getContentPane().add(panel, BorderLayout.CENTER);
		panel.setLayout(null);
		chat_history = new JTextArea();
		chat_history.setBorder(null);
		chat_history.setEnabled(false);
		chat_history.setToolTipText("");
		chat_history.setBounds(10, 11, 555, 470);
		chat_history.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
		chat_history.setEditable(false);
		chat_history.setTabSize(4);
		chat_history.setLineWrap(true);
		chat_history.setColumns(2);
		chat_history.setAutoscrolls(true);
		chat_history.setBackground(Color.LIGHT_GRAY);
		panel.add(chat_history);
		
		btnSend = new JButton("Send");
		btnSend.setFont(new Font("Microsoft JhengHei UI", Font.BOLD, 11));
		btnSend.setBounds(575, 500, 100, 30);
		panel.add(btnSend);
		btnSend.setEnabled(false);
		btnSend.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				if ( message_box.getText().isEmpty() ) { // do nothing 
					return; 
				} 
				String text = userName + ": ";
				text = text.concat(new String(message_box.getText()));
				byte[] buffer = new byte[Config.DATAGRAM_PKT_SIZE];
				try {
					buffer = text.getBytes(Config.DEFAULT_ENCODING);
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length, chatAddress, Config.CHAT_SERVICE_PORT);
					chatSocket.send(packet);
					message_box.setText(null);
				} catch (IOException e) {e.printStackTrace();}
			}
		});
		
		message_box = new JTextField();
		message_box.setBorder(null);
		message_box.setBounds(10, 500, 555, 60);
		message_box.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 11));
		message_box.setPreferredSize(new Dimension(320, 60));
		message_box.setEditable(true);
		message_box.setEnabled(false);
		message_box.setBackground(Color.LIGHT_GRAY);
		message_box.addKeyListener(new KeyListener() {
			public void keyPressed(KeyEvent arg0) {	
				if ( message_box.getText().isEmpty() ) { // do nothing 
					return; 
				} 
				if (arg0.getKeyCode() == KeyEvent.VK_ENTER) {
					String text = userName + ": ";
					text = text.concat(new String(message_box.getText()));
					byte[] buffer = new byte[Config.DATAGRAM_PKT_SIZE];
					try {
						buffer = text.getBytes(Config.DEFAULT_ENCODING);
						DatagramPacket packet = new DatagramPacket(buffer, buffer.length, chatAddress, Config.CHAT_SERVICE_PORT);
						chatSocket.send(packet);
						message_box.setText(null);
					} catch (IOException e) {e.printStackTrace();}
				}
			}

			public void keyReleased(KeyEvent arg0) {} // not used
			public void keyTyped(KeyEvent arg0) {} // not used
			
		});
		panel.add(message_box);
		
		statusBar = new JTextField();
		statusBar.setFont(new Font("Microsoft JhengHei UI", Font.BOLD, 11));
		statusBar.setBounds(0, 570, 714, 20);
		statusBar.setBackground(Color.LIGHT_GRAY);
		statusBar.setText("Offline");
		statusBar.setColumns(10);
		statusBar.setEditable(false);
		panel.add(statusBar);

		/* status bar */
		
		/* menu bar */
		menuBar = new JMenuBar();
		mainFrame.setJMenuBar(menuBar);
		
		mnFile = new JMenu("File");
		menuBar.add(mnFile);
		
		mntmNew = new JMenuItem("New");
		mntmNew.addActionListener(new ActionListener() { // NEW button listener
			public void actionPerformed(ActionEvent evt) {
				newdocActionPerformed();
			}
		});
		mntmNew.setEnabled(false);
		mntmNew.setIcon(new ImageIcon(TuringClientUI.class.getResource("/icons/newdoc_icon.png")));
		mnFile.add(mntmNew);
		
		mntmList = new JMenuItem("List");
		mntmList.addActionListener(new ActionListener() { // LIST button listener
			public void actionPerformed(ActionEvent evt) {
				listActionPerformed();
			}
		});
		mntmList.setEnabled(false);
		mntmList.setIcon(new ImageIcon(TuringClientUI.class.getResource("/icons/listdoc_icon.png")));
		mnFile.add(mntmList);
		
		mnAccount = new JMenu("Account");
		menuBar.add(mnAccount);
		
		mntmRegister = new JMenuItem("Register");
		mntmRegister.setIcon(new ImageIcon(TuringClientUI.class.getResource("/icons/register_icon.png")));
		mntmRegister.addActionListener(new ActionListener() { // REGISTER menu listener
			public void actionPerformed(ActionEvent evt) {
				registerActionPerformed();
			}
		});
		mnAccount.add(mntmRegister);
		
		mntmLogin = new JMenuItem("Login");
		mntmLogin.setIcon(new ImageIcon(TuringClientUI.class.getResource("/icons/login_icon.png")));
		mntmLogin.addActionListener(new ActionListener() { // LOGIN menu listener
			public void actionPerformed(ActionEvent evt) {
				loginActionPerformed();
			}
		});
		mnAccount.add(mntmLogin);
		
		mntmLogout = new JMenuItem("Logout");
		mntmLogout.setIcon(new ImageIcon(TuringClientUI.class.getResource("/icons/logout_icon.png")));
		mntmLogout.addActionListener(new ActionListener() { // LOGOUT menu listener
			public void actionPerformed(ActionEvent evt) {
				logoutActionPerformed();
			}
		});
		mnAccount.add(mntmLogout);
		mntmLogout.setEnabled(false);
		
	}
	
	/**************************
	 * event trigger functions
	 **************************/
	private static void registerActionPerformed() {
		JDialog regDialog = registerDialog();
		regDialog.setLocationRelativeTo(mainFrame);
		regDialog.setVisible(true);
	}

	private static void loginActionPerformed() {
		JDialog logDialog = loginDialog();
		logDialog.setLocationRelativeTo(mainFrame);
		logDialog.setVisible(true);
	}
	
	private static void logoutActionPerformed() {
		try {
			logoutRequest();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void newdocActionPerformed() {
		JDialog docDialog = newDocDialog();
		docDialog.setLocationRelativeTo(mainFrame);
		docDialog.setVisible(true);
	}

	private static void listActionPerformed() {
		ArrayList<String> rcv_files = null;
		try {
			rcv_files = listDocsRequest();
		} catch (IOException req_ex) {
			JOptionPane.showMessageDialog(mainFrame, "Error retrieving file list", "ERROR", JOptionPane.ERROR_MESSAGE);
		}
		if (rcv_files != null) {
			JDialog listDialog = fileExplorer();
			Iterator<String> f_iter = rcv_files.iterator();
			while (f_iter.hasNext()) {
				String current_file = (String) f_iter.next();
				int current_sect_n = Integer.parseInt((String) f_iter.next(), 10);
				list.addElement(current_file);
				if (!documents.containsKey(current_file)) { //adding file to client's local documents list if needed
					documents.put(current_file, current_sect_n);
				}
			}
			listDialog.setLocationRelativeTo(mainFrame);
			listDialog.setVisible(true);
		}
	}
	
	private static void shareActionPerformed(String filename) {
		JDialog shareDialog = shareDocDialog(filename);
		shareDialog.setLocationRelativeTo(mainFrame);
		shareDialog.setVisible(true);
	}
	
	/********************************** UI DIALOG BUILDERS ***********************************************/
	
	private static JDialog registerDialog() {
		
		JDialog dialog = new JDialog(mainFrame, "Register", true);
		JPanel contentPanel = new JPanel();
		dialog.setBounds(100, 100, 450, 300);
		dialog.getContentPane().setLayout(new BorderLayout());
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		dialog.getContentPane().add(contentPanel, BorderLayout.CENTER);
		contentPanel.setLayout(null);
		dialog.setResizable(false);
		
		JLabel lblUsername = new JLabel("Username: ");
		lblUsername.setBounds(85, 80, 75, 20);
		contentPanel.add(lblUsername);
		
		JTextField userfield = new JTextField();
		userfield.setBounds(170, 80, 110, 20);
		contentPanel.add(userfield);
		userfield.setColumns(10);
		
		JLabel lblPassword = new JLabel("Password: ");
		lblPassword.setBounds(85, 115, 75, 20);
		contentPanel.add(lblPassword);
		
		JPasswordField passfield = new JPasswordField();
		passfield.setColumns(10);
		passfield.setBounds(170, 115, 110, 20);
		contentPanel.add(passfield);
		
		JCheckBox chckbxShow = new JCheckBox("Show");
		chckbxShow.setBounds(301, 115, 65, 20);
		chckbxShow.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				if (chckbxShow.isSelected()) passfield.setEchoChar((char)0);
				else passfield.setEchoChar(new Character((char)0x25CF));
			}
		});
		contentPanel.add(chckbxShow);
		
		JButton btnRegister = new JButton("Register");
		btnRegister.setBounds(150, 175, 100, 30);
		contentPanel.add(btnRegister);
		
		JLabel lblWelcomeToTuring = new JLabel("Welcome in Turing!");
		lblWelcomeToTuring.setHorizontalAlignment(SwingConstants.CENTER);
		lblWelcomeToTuring.setFont(new Font("Microsoft JhengHei", Font.BOLD, 19));
		lblWelcomeToTuring.setBounds(85, 11, 265, 60);
		contentPanel.add(lblWelcomeToTuring);
		
		/* button listeners */
		btnRegister.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
			String username = new String(userfield.getText());
			char[] password = passfield.getPassword();
			
			if (!isValidUser(username, password)) {
				JOptionPane.showMessageDialog(
						mainFrame, 
						"Invalid credentials", 
						"Invalid username/password: password must be at least 5 characters long", 
						JOptionPane.ERROR_MESSAGE);
			}
			
			else {
				/* retrieving remote registration method */
				try {
					turing_services = LocateRegistry.getRegistry(Config.REMOTE_SERVICE_PORT);
					remoteOBJ = (TuringRemoteService) turing_services.lookup("registerOP");
					
					if ( remoteOBJ.registerOP(username, password) ) {
						JOptionPane.showMessageDialog(
								mainFrame, 
								"Successfully registered", 
								"Success!", 
								JOptionPane.INFORMATION_MESSAGE);
						dialog.dispose();
					}
					else {
						JOptionPane.showMessageDialog( 
								mainFrame, 
								"This username is not available",
								"Invalid username/password", 
								JOptionPane.INFORMATION_MESSAGE);
						userfield.setText("");
						passfield.setText("");
					}
				}catch (NotBoundException | HeadlessException | RemoteException ex) {
					JOptionPane.showMessageDialog(
							mainFrame, 
							"Unable to reach the server, try again later", 
							"COM_ERROR", 
							JOptionPane.ERROR_MESSAGE);
				}			
			}
		}
	});
		
		return dialog;
	}

	private static JDialog loginDialog() {
		
		JDialog dialog = new JDialog(mainFrame, "Login", true);
		JPanel contentPanel = new JPanel();
		dialog.setBounds(100, 100, 450, 300);
		dialog.getContentPane().setLayout(new BorderLayout());
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		dialog.getContentPane().add(contentPanel, BorderLayout.CENTER);
		contentPanel.setLayout(null);
		dialog.setResizable(false);
		
		JLabel lblUsername = new JLabel("Username: ");
		lblUsername.setBounds(85, 80, 75, 20);
		contentPanel.add(lblUsername);
		
		JTextField userfield = new JTextField();
		userfield.setBounds(170, 80, 110, 20);
		contentPanel.add(userfield);
		userfield.setColumns(10);
		
		JLabel lblPassword = new JLabel("Password: ");
		lblPassword.setBounds(85, 116, 75, 20);
		contentPanel.add(lblPassword);
		
		JPasswordField passfield = new JPasswordField();
		passfield.setColumns(10);
		passfield.setBounds(170, 116, 110, 20);
		contentPanel.add(passfield);
		
		JCheckBox chckbxShow = new JCheckBox("Show");
		chckbxShow.setBounds(300, 115, 65, 20);
		contentPanel.add(chckbxShow);
		
		JButton btnLogin = new JButton("Login");
		btnLogin.setBounds(150, 175, 100, 30);
		contentPanel.add(btnLogin);
		
		JLabel lblWelcomeInTuring = new JLabel("Welcome in Turing!");
		lblWelcomeInTuring.setHorizontalAlignment(SwingConstants.CENTER);
		lblWelcomeInTuring.setFont(new Font("Microsoft JhengHei", Font.BOLD, 19));
		lblWelcomeInTuring.setBounds(85, 11, 265, 58);
		contentPanel.add(lblWelcomeInTuring);
		
		/* button listeners */
		chckbxShow.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				if (chckbxShow.isSelected()) passfield.setEchoChar((char)0);
				else passfield.setEchoChar(new Character((char)0x25CF));
			}
		});
		
		btnLogin.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e){
			String username = new String(userfield.getText());
			char[] password = passfield.getPassword();
							
			if (!(isValidUser(username, password))) {
				JOptionPane.showMessageDialog(
						mainFrame, 
						"Please choose a valid Username and Password", 
						"Invalid username/password", 
						JOptionPane.ERROR_MESSAGE);
			}
		
			else {
				/* connecting to server and sending a login request */
				InetSocketAddress server_address = new InetSocketAddress(Config.SERVER_IP, Config.SERVER_PORT);
				try {
					/* Open comunication and data transfer socket channel */
					client_ch = SocketChannel.open(server_address);
					client_ch.configureBlocking(false);
					
					loginRequest(username, password);
					byte r;
					if ( (r=getResponse(client_ch)) == Config.SUCCESS ) {
						statusBar.setText(username + " - Online");
						mntmRegister.setEnabled(false);
						mntmLogin.setEnabled(false);
						mntmLogout.setEnabled(true);
						userName = new String(username);
						enableOnlineServices();
						JOptionPane.showMessageDialog(mainFrame, "Successfully logged in");
						dialog.dispose();
							
					}
					else {
						JOptionPane.showMessageDialog(mainFrame, Config.ERROR_LOG(r));
					}
				} catch (IOException log_ex) {
					JOptionPane.showMessageDialog(
							mainFrame, 
							"Unable to reach the server, try again later", 
							"CONNECTION_ERROR", 
							JOptionPane.ERROR_MESSAGE);
				} 
			}
		}
	});
		return dialog;
	}

	private static JDialog newDocDialog() {
		
		JDialog dialog = new JDialog(mainFrame, "New Document", true);
		dialog.setBounds(100, 100, 450, 300);
		dialog.getContentPane().setLayout(null);
		
		JLabel lblName = new JLabel("Title: ");
		lblName.setHorizontalAlignment(SwingConstants.RIGHT);
		lblName.setBounds(80, 90, 65, 20);
		dialog.getContentPane().add(lblName);
		
		JLabel lblsections = new JLabel("#Sections: ");
		lblsections.setHorizontalAlignment(SwingConstants.RIGHT);
		lblsections.setBounds(80, 125, 65, 20);
		dialog.getContentPane().add(lblsections);
		
		JTextField namefield = new JTextField();
		namefield.setBounds(155, 90, 135, 20);
		dialog.getContentPane().add(namefield);
		
		JTextField sectionfield = new JTextField();
		sectionfield.setBounds(155, 125, 135, 20);
		dialog.getContentPane().add(sectionfield);
		
		JButton btnCreate = new JButton("Create");
		btnCreate.setBounds(155, 175, 90, 30);
		dialog.getContentPane().add(btnCreate);
		
		/* button listeners */
		btnCreate.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
			String filename = new String(namefield.getText());
			int section_num = Integer.parseInt(sectionfield.getText());
			
			if (filename.contains("-")) {
				JOptionPane.showMessageDialog(mainFrame, "Illegal character ' - '");
			} else if (section_num < 1) {
				JOptionPane.showMessageDialog(mainFrame, "Please select a valid section number");
			} else {
				try {
					newDocRequest(filename, section_num);
				} catch (UnsupportedEncodingException e2) {
					e2.printStackTrace();
				}
				try {
					byte r;
					if ( (r=getResponse(client_ch)) == Config.SUCCESS ) {
						JOptionPane.showMessageDialog(mainFrame, "File successfully created");
						dialog.dispose();
					}
					else {
						JOptionPane.showMessageDialog(mainFrame, Config.ERROR_LOG(r));
					}
				} catch (HeadlessException | IOException req_ex) {
					req_ex.printStackTrace();
				}
			}			
		}
	});
		
		return dialog;
	}
	
	private static JDialog shareDocDialog(String filename) {
		JDialog dialog = new JDialog(mainFrame, "Share document", true);
		JPanel panel = new JPanel();
		panel.setLayout(new GridBagLayout());
		dialog.setSize(400, 150);
		dialog.setResizable(false);
		dialog.setLocationRelativeTo(file_explorer);
		
		JLabel dest_label = new JLabel("Destinatary: ");
		JTextField dest = new JTextField(16);
		JButton shareButton = new JButton("Share");
		
		GridBagConstraints layout_cs = new GridBagConstraints();	//Panel layout constraints
		layout_cs.fill = GridBagConstraints.HORIZONTAL;
		
		layout_cs.gridx = 0;
		layout_cs.gridy = 0;
		panel.add(dest_label, layout_cs);
		
		layout_cs.gridx = 1;
		layout_cs.gridy = 0;
		panel.add(dest, layout_cs);
		
		layout_cs.gridx = 2;
		layout_cs.gridy = 0;
		panel.add(shareButton, layout_cs);
		
		dialog.getContentPane().add(panel);
		
		shareButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String destinatary = new String(dest.getText());
				String file_name = new String(filename);
				try {
					shareRequest(file_name, destinatary);
					byte r;
					if ( (r=getResponse(client_ch)) == Config.SUCCESS ) {
						JOptionPane.showMessageDialog(mainFrame, file_name + " successfully shared with " + destinatary);
						dialog.dispose();
					}
					else {
						JOptionPane.showMessageDialog(mainFrame, Config.ERROR_LOG(r));
						dest.setText("");
					}
				} catch (IOException io_ex) {
					JOptionPane.showMessageDialog(mainFrame, io_ex.getMessage());
				}
			}
		});
				
		return dialog;
	}

	private static JDialog fileExplorer() {
		JDialog dialog = new JDialog(mainFrame, "My files");
		JPanel panel = new JPanel();
		JButton btnUpdate = new JButton("Refresh");
	
		btnUpdate.addActionListener(new ActionListener() { // refresh files list

			public void actionPerformed(ActionEvent arg0) {
				dialog.dispose();
				listActionPerformed();
			}
			
		});
		
		dialog.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				dialog.dispose();
			}
		});
		
		mntmEdit = new JMenuItem("Edit section");
		mntmEdit.setIcon(new ImageIcon(TuringClientUI.class.getResource("/icons/editdoc_icon.png")));
		mntmShowSection = new JMenuItem("Show section");
		mntmShowSection.setIcon(new ImageIcon(TuringClientUI.class.getResource("/icons/showsect_icon.png")));
		mntmShow = new JMenuItem("Show document");
		mntmShow.setIcon(new ImageIcon(TuringClientUI.class.getResource("/icons/showdoc_icon.png")));
		mntmShare = new JMenuItem("Share document");
		mntmShare.setIcon(new ImageIcon(TuringClientUI.class.getResource("/icons/sharedoc_icon.png")));
		
		dialog.setSize(320, 240);
		dialog.setResizable(true);
		dialog.setLocationRelativeTo(mainFrame);
		panel.setLayout(new BorderLayout());
		panel.setPreferredSize(new Dimension(480, 600));
		
		list = new DefaultListModel<>();
		file_explorer = new JList<String>(list);
		file_explorer.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		explorer_scroll = new JScrollPane(file_explorer);
		explorer_scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		explorer_scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		
		JPopupMenu selectionMenu = new JPopupMenu();
		selectionMenu.add(mntmEdit);
		selectionMenu.add(mntmShowSection);
		selectionMenu.add(mntmShow);
		selectionMenu.add(mntmShare);

		file_explorer.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent me) {
				if (SwingUtilities.isRightMouseButton(me) 
						&& !file_explorer.isSelectionEmpty()
						&& file_explorer.locationToIndex(me.getPoint()) == file_explorer.getSelectedIndex() ) {
					selectionMenu.show(file_explorer, me.getX(), me.getY());
				}
			}
		});
		
		/* adding popup menu item triggers */				
		mntmEdit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				String selected_file = new String(file_explorer.getSelectedValue());
				JPopupMenu popup = editSectionPopupMenu(selected_file, documents.get(selected_file));
				popup.setLocation(MouseInfo.getPointerInfo().getLocation());
				armPopup(popup);
				popup.setVisible(true);
				
			}
		});
		
		mntmShowSection.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				String selected_file = new String(file_explorer.getSelectedValue());
				JPopupMenu popup = showSectionPopupMenu(selected_file, documents.get(selected_file));
				popup.setLocation(MouseInfo.getPointerInfo().getLocation());
				armPopup(popup);
				popup.setVisible(true);
			}
		});
		
		mntmShow.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				String selected_file = new String(file_explorer.getSelectedValue());
				try {
					String text = downloadRequest(selected_file, -1, Config.SHOW_R);
					System.out.println("Received text: " + text);
					JDialog viewer = new JDialog();
					JTextArea textArea = new JTextArea();
					textArea.setEditable(false);
					viewer.setTitle(selected_file);
					viewer.getContentPane().add(textArea);
					textArea.setText(text);
					viewer.setLocationRelativeTo(file_explorer);
					viewer.setSize(480, 480);
					viewer.setVisible(true);
				}
				catch (IOException ex) {ex.printStackTrace();}
			}
		});
		
		mntmShare.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				shareActionPerformed(list.getElementAt(file_explorer.getSelectedIndex()));
			}
		});
				
		panel.add(explorer_scroll);
		panel.add(btnUpdate, BorderLayout.SOUTH);
		dialog.getContentPane().add(panel);
		
		return dialog;
	}
	
	private static JFrame fileEditor(String filename, String text, int section) {
		final JFrame frame = new JFrame(filename);
		final JMenuBar menu_bar = new JMenuBar();
		final JMenu mnFile = new JMenu("File");
		final JMenuItem mntmSave = new JMenuItem("Save");
		final JTextArea textArea = new JTextArea();
		final JScrollPane textScroll = new JScrollPane(textArea);
		
		mntmSave.setIcon(new ImageIcon(TuringClientUI.class.getResource("/icons/savedoc_icon.png")));
		
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
            public void windowClosing(WindowEvent e) {
				int confirm;
				confirm = JOptionPane.showConfirmDialog(frame, 
						"Do you really want to exit? Any unsaved action will be lost",
						"Exit edit mode",
						JOptionPane.YES_NO_OPTION,
						JOptionPane.WARNING_MESSAGE);
				if ( confirm == JOptionPane.YES_OPTION ) {
					try {
						endEditRequest(filename, section);
					} catch (UnsupportedEncodingException ue) {ue.printStackTrace();}
					frame.dispose();
					CLIENT_STATUS = Config.ONLINE;
				}
			}
		});		
		frame.setSize(new Dimension(800, 600));
		frame.setResizable(true);
		
		menu_bar.add(mnFile);
		mnFile.add(mntmSave);
		
		textArea.setEditable(true);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);		
		textArea.setText(text.trim());
				
		textScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		textScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		
		mntmSave.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				try {
					String text = new String(textArea.getText());
					updateSectionRequest(filename, text, section);
					byte r = getResponse(client_ch);
					if ( r != Config.SUCCESS) { // saving file failed
						JOptionPane.showMessageDialog(frame, "Error saving file: " + Config.ERROR_LOG(r));
					}
					else {
						JOptionPane.showMessageDialog(frame, "File saved successfully!");
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		
		frame.setJMenuBar(menu_bar);
		frame.getContentPane().add(textScroll);
		return frame;
	}

	private static JPopupMenu editSectionPopupMenu(String filename, int sections) {
		JPopupMenu popupMenu = new JPopupMenu();
		JPanel panel = new JPanel();
		JComboBox<Integer> s_lister = new JComboBox<Integer>();
		JButton btnEdit = new JButton("Edit");
		JButton btnCancel = new JButton("Cancel");
		
		for (int i=0; i<sections; i++) {
			s_lister.addItem(i);
		}
		
		// Edit button listener
		btnEdit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				
				if (CLIENT_STATUS == Config.EDITING) {
					JOptionPane.showMessageDialog(
							mainFrame,
							"Need to close current editing session first!");
					return;
				}
				
				int s = s_lister.getSelectedIndex(); 
				String text;
				try {
					text = downloadRequest(filename, s, Config.EDIT_R);
					if (text != null) {
						editorWindow = fileEditor(filename, text, s);
						editorWindow.setLocation(mainFrame.getX() + mainFrame.getWidth(), mainFrame.getY());
						editorWindow.setVisible(true);
						CLIENT_STATUS = Config.EDITING;
						String chat_address = getGroupAddress();
						chatAddress = InetAddress.getByName(chat_address);
						enableChat();
						session_name = new String(filename);
						session_index = s;
					} 
					popupMenu.setVisible(false);	
				} catch (UnsupportedEncodingException enc_e) {
					JOptionPane.showMessageDialog(mainFrame, enc_e.getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
				} catch (UnknownHostException host_e) {
					host_e.printStackTrace();
				}
			}
		});
	
		// Cancel button listener
		btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				popupMenu.setVisible(false);
			}
		});
		
		panel.setLayout(new FlowLayout());
		panel.add(s_lister);
		panel.add(btnEdit);
		panel.add(btnCancel);
		Object preventHide = s_lister.getClientProperty("doNotCancelPopup");
		popupMenu.putClientProperty("doNotCancelPopup", preventHide);
		popupMenu.add(panel);
		
		return popupMenu;
	}
	
	private static JPopupMenu showSectionPopupMenu(String filename, int sections) {
		JPopupMenu popupMenu = new JPopupMenu();
		JPanel panel = new JPanel();
		JComboBox<Integer> s_lister = new JComboBox<Integer>();
		JButton btnShow = new JButton("Show");
		JButton btnCancel = new JButton("Cancel");
		
		for (int i=0; i<sections; i++) {
			s_lister.addItem(i);
		}
		
		btnShow.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				byte s = (byte) s_lister.getSelectedIndex(); 
				String text;
				try {
					text = downloadRequest(filename, s, Config.SHOW_R);
					if (text == null) {
						JOptionPane.showMessageDialog(file_explorer, "Error retrieving selected file");
					} else {
						JDialog viewer = new JDialog();
						JTextArea textArea = new JTextArea();
						textArea.setEditable(false);
						viewer.setTitle(filename + ", section " + s);
						viewer.getContentPane().add(textArea);
						textArea.setText(text);
						viewer.setLocationRelativeTo(file_explorer);
						viewer.setSize(480, 480);
						viewer.setVisible(true);
						
					}
				} catch (UnsupportedEncodingException e) {
					JOptionPane.showMessageDialog(mainFrame, e.getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
				}
			}
		});
		
		btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				popupMenu.setVisible(false);
			}
		});
		
		panel.setLayout(new FlowLayout());
		panel.add(s_lister);
		panel.add(btnShow);
		panel.add(btnCancel);
		
		popupMenu.add(panel);
		
		return popupMenu;
	}
	
	/******************************************** REQUESTS *************************************************/
	
	/* sends a login request to the server */
	private static void loginRequest(String username, char[] password) throws UnsupportedEncodingException {
		
		ByteBuffer request = ByteBuffer.allocate(Config.BUF_SIZE);
		byte name_size = (byte) username.length();
		byte pass_size = (byte) password.length;
			
		request.put(Config.LOGIN_R);
		request.put(name_size);
		request.put(pass_size);
		request.put(username.getBytes(Config.DEFAULT_ENCODING));
		for (int i=0; i<password.length; i++) {
			request.putChar(password[i]);
		}
		request.flip();
		try {
			client_ch.write(request);
		} catch (IOException req_ex) {
			req_ex.printStackTrace();
		}
		
	}

	/* sends a logout request to the server */
	private static void logoutRequest() throws UnsupportedEncodingException {
		
		if (!serverOnline()) {
			disableOnlineService();
			mntmRegister.setEnabled(true);
			mntmLogin.setEnabled(true);
			mntmLogout.setEnabled(false);
			JOptionPane.showMessageDialog(mainFrame, "Server unreachable!");
		} else {
		
			ByteBuffer buffer = ByteBuffer.allocate(Config.BUF_SIZE);
			buffer.put(Config.LOGOUT_R);
			buffer.put((byte) userName.length());
			buffer.put(userName.getBytes(Config.DEFAULT_ENCODING));
			buffer.flip();
				
			try {
				client_ch.write(buffer);
				
				byte r;
				if ( (r=getResponse(client_ch)) != Config.SUCCESS) {
					JOptionPane.showMessageDialog(mainFrame, Config.ERROR_LOG(r));
				}
				else {
					disableOnlineService();
					mntmRegister.setEnabled(true);
					mntmLogin.setEnabled(true);
					mntmLogout.setEnabled(false);
					JOptionPane.showMessageDialog(mainFrame, "Logout succeeded");
				}
			} catch (IOException io_ex) {
				io_ex.printStackTrace();
			}
		}
		return;
	}
	
	/* sends a request to the server to create a new document */
	private static void newDocRequest(String filename, int sections) throws UnsupportedEncodingException {
		
		if (!serverOnline()) {
			JOptionPane.showMessageDialog(mainFrame, "Server currently offline, try again later");
			return;
		}
		
		ByteBuffer request = ByteBuffer.allocate(Config.BUF_SIZE);
		request.put(Config.NEW_R);
		request.put((byte) userName.length());
		request.put((byte) filename.length());
		request.putInt(sections);
		
		request.put(userName.getBytes(Config.DEFAULT_ENCODING));
		request.put(filename.getBytes(Config.DEFAULT_ENCODING));
		
		request.flip();
		try {
			client_ch.write(request);
		} catch (IOException req_ex) {
			JOptionPane.showMessageDialog(mainFrame, req_ex.getMessage());
			return;
		}
	}
	
	/* sends a request to the server to receive the list of proprietary and shared files */
	private static ArrayList<String> listDocsRequest() throws UnsupportedEncodingException {
		if (!serverOnline()) {
			JOptionPane.showMessageDialog(mainFrame, "Server currently offline, try again later");
			return null;
		}
	
		ByteBuffer buffer = ByteBuffer.allocate(Config.BUF_SIZE);
		byte name_size = (byte) userName.length();
		
		buffer.put(Config.LIST_R);
		buffer.put(name_size);
		buffer.put(userName.getBytes(Config.DEFAULT_ENCODING));
		
		buffer.flip();
		
		try {
			client_ch.write(buffer);
			byte r;
			if ( (r = getResponse(client_ch)) != Config.SUCCESS ) { // file list retrievement failed
				JOptionPane.showMessageDialog(mainFrame, Config.ERROR_LOG(r));
				return null;
			}
			else { // receiving file list
				buffer.clear();
				client_ch.read(buffer);
				buffer.flip();
				byte[] files_list = buffer.array();
				String files = new String(files_list, Config.DEFAULT_ENCODING);
				StringTokenizer s_tok = new StringTokenizer(files, "-");
				ArrayList<String> received_files = new ArrayList<String>();
				while (s_tok.hasMoreTokens()) {
					try { 
						String current_file = new String(s_tok.nextToken());
						String current_sect = new String(s_tok.nextToken());
						received_files.add(current_file);
						received_files.add(current_sect);
					} catch (NoSuchElementException end_ex) {} // last element is just a delimiter token	
				}
				return received_files;
			}
		} catch (IOException req_ex) {
			JOptionPane.showMessageDialog(mainFrame, req_ex.getMessage());
		}
		return null;
	}
	
	/* Sends a request to share file 'filename' with user 'destinatary' */
	private static void shareRequest(String filename, String destinatary) throws UnsupportedEncodingException {
		
		if (!serverOnline()) {
			JOptionPane.showMessageDialog(mainFrame, "Server currently offline, try again later");
		}
		
		ByteBuffer request = ByteBuffer.allocate(Config.BUF_SIZE);
		byte name_size = (byte) userName.length();
		byte fname_size = (byte) filename.length();
		byte destname_size = (byte) destinatary.length();
		
		request.put(Config.SHARE_R);
		request.put(name_size);
		request.put(fname_size);
		request.put(destname_size);
		
		request.put(userName.getBytes(Config.DEFAULT_ENCODING));
		request.put(filename.getBytes(Config.DEFAULT_ENCODING));
		request.put(destinatary.getBytes(Config.DEFAULT_ENCODING));
		
		request.flip();
		try {
			client_ch.write(request);
		} catch (IOException req_ex) {
			JOptionPane.showMessageDialog(mainFrame, req_ex.getMessage());
			return;
		}
	}
	
	/* Sends a request to download the selected file section and returns the downloaded file to the calling component */
	private static String downloadRequest(String filename, int section, byte mode) throws UnsupportedEncodingException {
		
		if (!serverOnline()) {
			JOptionPane.showMessageDialog(mainFrame, "Server currently offline, try again later");
			return null;
		}
		
		ByteBuffer request = ByteBuffer.allocate(Config.BUF_SIZE);
		byte name_size = (byte) userName.length();
		byte fname_size = (byte) filename.length();
		
		request.put(mode);
		request.put(name_size);
		request.put(fname_size);
		request.put(userName.getBytes(Config.DEFAULT_ENCODING));
		request.put(filename.getBytes(Config.DEFAULT_ENCODING));
		request.putInt(section);
		
		request.flip();
		
		try {
			client_ch.write(request);
			String file = downloadFile(); 
			return file;
		} catch (IOException req_ex) {
			JOptionPane.showMessageDialog(mainFrame, req_ex.getMessage());
			return null;
		}
		
	}

	/* Download a file from the server */
	private static String downloadFile() throws IOException {
		
		ByteBuffer buffer = ByteBuffer.allocate(Config.BUF_SIZE);
		ByteBuffer data;
		ByteArrayOutputStream byte_stream = new ByteArrayOutputStream();
		byte[] r_bytes;
		int received_bytes = 0;
		byte[] b_data;
		int text_size;
		byte[] text;
		String file;
		byte r_code;
		
		while (client_ch.read(buffer) <= 0) {
		}
		do { // reads incoming byte from channel, and puts them into a local bytestream array
			buffer.flip();
			r_bytes = buffer.array();
			byte_stream.write(r_bytes);
			buffer.clear();
		} while (client_ch.read(buffer) > 0);
		
		
		received_bytes = byte_stream.size();
		b_data = new byte[received_bytes];
		data = ByteBuffer.allocate(received_bytes);
		
		b_data = byte_stream.toByteArray();
		data.put(b_data);
		data.flip();
		byte_stream.close();
				
		r_code = data.get();
		if (r_code == Config.IN_EDIT) {
			int editor_name_s = data.getInt();
			byte[] editing_user_b = new byte[editor_name_s];
			data.get(editing_user_b, 0, editor_name_s);
			String editing_user = new String(editing_user_b, Config.DEFAULT_ENCODING);
			JOptionPane.showMessageDialog(mainFrame, "File currently in edit by user: " + editing_user);
			return null;
		} else {
			text_size = data.getInt();
			text = new byte[text_size];
			data.get(text, 0, text_size);
			file = new String(text, Config.DEFAULT_ENCODING);
			return file;
		}
	}
	
	/* Sends the new file version to the server */
	private static void updateSectionRequest(String filename, String text, int section) throws UnsupportedEncodingException { 
		
		int capacity = (text.getBytes(Config.DEFAULT_ENCODING).length) +		
					   userName.getBytes(Config.DEFAULT_ENCODING).length + 		
					   filename.getBytes(Config.DEFAULT_ENCODING).length + 		
					   2 * (Integer.SIZE) +											
					   1;														
		
		System.out.println("Total size needed: " + capacity + " bytes");
		ByteBuffer request = ByteBuffer.allocate(capacity);
		try {
			
			request.put(Config.SAVE_R); 											// request code
			request.put((byte) userName.getBytes(Config.DEFAULT_ENCODING).length);	// username length
			request.put((byte) filename.getBytes(Config.DEFAULT_ENCODING).length);	// filename length
			request.putInt(text.getBytes(Config.DEFAULT_ENCODING).length);			// text length
			request.putInt(section);												// section index
			request.put(userName.getBytes(Config.DEFAULT_ENCODING));				// username
			request.put(filename.getBytes(Config.DEFAULT_ENCODING));				// filename
			request.put(text.getBytes(Config.DEFAULT_ENCODING));					// text
			
			request.flip();
			client_ch.write(request);
		}
		catch (IOException io_ex) { io_ex.printStackTrace(); }
		
	}

	/* Sends a notification to the server to comunicate that given "section" of "filename" is now free */
	private static void endEditRequest(String filename, int section) throws UnsupportedEncodingException {
		ByteBuffer request = ByteBuffer.allocate(Config.BUF_SIZE);
		request.put(Config.END_EDIT_R);
		request.put((byte) userName.length());
		request.put((byte) filename.length());
		request.putInt(section);
		
		request.put(userName.getBytes(Config.DEFAULT_ENCODING));
		request.put(filename.getBytes(Config.DEFAULT_ENCODING));
		
		request.flip();
		
		try {
			client_ch.write(request);
			disableChat();
			CLIENT_STATUS = Config.ONLINE;
		} catch (IOException req_ex) {
			JOptionPane.showMessageDialog(mainFrame, req_ex.getMessage());
			return;
		}
	}
	
	/* Sends a request to the server to start the notification service for this session */
	private static void notifyServiceStartRequest(String username) {
		try {
			ByteBuffer request = ByteBuffer.allocate(Config.BUF_SIZE);
			byte name_size = (byte) username.length();
				
			request.put(Config.NOTIFY_SERV_R);
			request.put(name_size);
			request.put(username.getBytes(Config.DEFAULT_ENCODING));
			request.flip();
			try {
				notification_ch.write(request);
			} catch (IOException req_ex) {
				req_ex.printStackTrace();
			}
		} catch (IOException io_ex) { io_ex.printStackTrace(); }
	}
	
	/************************************ UTILITY FUNCTIONS ***********************************************/
	
	/* Checks server status */
	private static boolean serverOnline () {
		if ( client_ch.isConnected()) return true;
		return false;
	}
	
	/* Waits to receive a response code from the server for the current request */
	private static byte getResponse(SocketChannel channel) throws IOException {
		ByteBuffer response = ByteBuffer.allocate(1);		
		while(channel.read(response) <= 0) {
		}
		response.flip();
		byte r_code = response.get();
		return r_code;		
	}

	/* Waits to receive a multicast address from the server */
	private static String getGroupAddress() {
		String group_address = null;
		
		ByteBuffer buffer = ByteBuffer.allocate(32);
		try {
			while (client_ch.read(buffer) <= 0) {
			}
			buffer.flip();
			group_address = new String(buffer.array(), Config.DEFAULT_ENCODING);
		} catch (IOException io_ex) {io_ex.printStackTrace(); }
		
		
		return group_address;
	}
	
	/* Checks if the chosen <username, password> meets the requirements */
	private static boolean isValidUser(String username, char[] password) {

		if (username.length() > 16 || username.length() < 5) {
			return false;
		}
		
		if (password.length > 16 || password.length < 5) {
			return false;
		}
		
		return true;
	}
	
	/* Enables online functionalities */
	private static void enableOnlineServices() {
		
		CLIENT_STATUS = Config.ONLINE;
		InetSocketAddress server_address = new InetSocketAddress(Config.SERVER_IP, Config.SERVER_PORT);
		try {
			notification_ch = SocketChannel.open(server_address);
			notification_ch.configureBlocking(false);
			notifyServiceStartRequest(userName);
			byte r;
			if ( (r=getResponse(notification_ch)) == Config.SUCCESS) { // start notification listener thread
				notification_handler = new Thread(new NotificationHandler(notification_ch));
				notification_handler.start();
			} else {
				JOptionPane.showMessageDialog(mainFrame, Config.ERROR_LOG(r));
			}
		} catch (IOException io_ex) {io_ex.printStackTrace();}
		
		/* Enabling UI components */
		mntmNew.setEnabled(true);
		mntmList.setEnabled(true);
		btnSend.setEnabled(true);
		statusBar.setText("Online - " + userName);
		statusBar.setBackground(Color.GREEN);
		
	}

	/* Disables online functionalities */
	private static void disableOnlineService() {
		
		CLIENT_STATUS = Config.OFFLINE;
		mntmNew.setEnabled(false);
		mntmList.setEnabled(false);
		btnSend.setEnabled(false);
		statusBar.setText("Offline");
		statusBar.setBackground(Color.GRAY);
		notification_handler.interrupt();
		disableChat();
	}
	
	/* Enables chat service for current file work-group */
	private static void enableChat() {
				
		/* initialize UDP Socket for chat message exchange */
		try {
			chatSocket = new DatagramSocket();
			mc_receiver = new MulticastReceiver(chatAddress, chat_history);
			mc_receiver.start();

		} catch (SocketException e) {
			e.printStackTrace();
		}
		
		/* enable UI components */
		chat_history.setEnabled(true);
		message_box.setEnabled(true);
		btnSend.setEnabled(true);
		chat_history.setBackground(Color.WHITE);
		message_box.setBackground(Color.WHITE);
		
	}
	
	/* Disables chat service */
	private static void disableChat() {
		
		/* delete chat socket and msg_sender thread */
		if (mc_receiver != null) {
			mc_receiver.disableChat();
			try {
				mc_receiver.interrupt();
				mc_receiver.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		chat_history.setText("");
		message_box.setText("");
		chat_history.setEnabled(false);
		message_box.setEnabled(false);
		btnSend.setEnabled(false);
		chat_history.setBackground(Color.LIGHT_GRAY);
		message_box.setBackground(Color.LIGHT_GRAY);
	}

	/* Returns true if mouse points outside of the given region */
	private static boolean pointsOutOfRegion(Rectangle region) {
		int mouseX = MouseInfo.getPointerInfo().getLocation().x;
		int mouseY = MouseInfo.getPointerInfo().getLocation().y;
		if ( (mouseX >= region.getMinX() && mouseX <= region.getMaxX()) && 
			(mouseY >= region.getMinY() && mouseY <= region.getMaxY()) )
			return false;
		
		return true;
	}
	
	/* Manages _Popup visibility relative to user mouse actions */
	public static void armPopup(JPopupMenu _Popup) {
	    if(_Popup != null) {
	        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
	            @Override
	            public void eventDispatched(AWTEvent event) {

	                if(event instanceof MouseEvent) {
	                    MouseEvent m = (MouseEvent)event;
	                    if(m.getID() == MouseEvent.MOUSE_CLICKED && pointsOutOfRegion(_Popup.getVisibleRect())){
	                        _Popup.setVisible(false);
	                        Toolkit.getDefaultToolkit().removeAWTEventListener(this);
	                    }
	                }
	                if(event instanceof WindowEvent) {
	                    WindowEvent we = (WindowEvent)event;
	                    if(we.getID() == WindowEvent.WINDOW_DEACTIVATED || we.getID() == WindowEvent.WINDOW_STATE_CHANGED) {
	                        _Popup.setVisible(false);
	                        Toolkit.getDefaultToolkit().removeAWTEventListener(this);
	                    }
	                }
	            }

	        }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.WINDOW_EVENT_MASK);

	    }
	}
}
