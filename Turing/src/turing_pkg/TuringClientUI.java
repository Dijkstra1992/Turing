package turing_pkg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.StringTokenizer;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
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
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class TuringClientUI {

	/* UI components */
	private static JFrame mainFrame;
	private static JPanel panel;
	private static JTextField statusBar;
	private static JTextArea chat_history;
	private static JTextField message_box;
	private static JList<String> file_explorer;
	private static DefaultListModel<String> list;
	private static JScrollPane exp_scroll;
	private static JButton btnSend;
	private static JMenuBar menuBar;
	private static JMenu mnFile;
	private static JMenu mnAccount;
	private static JMenu mnHelp;
	private static JMenuItem mntmNew;
	private static JMenuItem mntmList;
	private static JMenuItem mntmRegister;
	private static JMenuItem mntmLogin;
	private static JMenuItem mntmLogout;
	private static JMenuItem mntmUsage;
	private static JMenuItem mntmEdit;
	private static JMenuItem mntmShowSection;
	private static JMenuItem mntmShow;
	private static JMenuItem mntmShare;
	
	/* Global variables and constants */
	private static SocketChannel client_ch = null;
	private static String userName;
	private static Registry turing_services;
	private static TuringRemoteService remoteOBJ;
	
	
	/****************************
	 * Launch the application.
	 ***************************/
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			@SuppressWarnings("static-access")
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
					TuringClientUI window = new TuringClientUI();
					window.mainFrame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/******************************
	 * Create the application.
	 *****************************/
	public TuringClientUI() {
		initialize();
	}

	/*******************************************
	 * Initialize the contents of the frame
	 *******************************************/
	private void initialize() {
		mainFrame = new JFrame("TURING");
		mainFrame.setBounds(100, 100, 800, 600);
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainFrame.setResizable(false);
		
		panel = new JPanel();
		mainFrame.getContentPane().add(panel, BorderLayout.CENTER);
		panel.setLayout(null);
		
		/* chat panel */
		chat_history = new JTextArea();
		chat_history.setTabSize(4);
		chat_history.setLineWrap(true);
		chat_history.setColumns(2);
		chat_history.setBounds(165, 10, 600, 450);
		chat_history.setPreferredSize(new Dimension(320, 400));
		panel.add(chat_history);
		chat_history.setEditable(false);
		chat_history.setEnabled(false);
		chat_history.setBackground(Color.LIGHT_GRAY);
		
		message_box = new JTextField();
		message_box.setBounds(165, 470, 600, 50);
		message_box.setPreferredSize(new Dimension(320, 100));
		panel.add(message_box);
		message_box.setEnabled(false);
		message_box.setBackground(Color.LIGHT_GRAY);
		
		btnSend = new JButton("Send");
		btnSend.setBounds(10, 470, 130, 25);
		panel.add(btnSend);
		btnSend.setEnabled(false);

		/* status bar */
		statusBar = new JTextField();
		statusBar.setBackground(Color.LIGHT_GRAY);
		statusBar.setText("Guest - Offline");
		//165, 470, 600, 50
		statusBar.setBounds(0, 530, 800, 20);
		statusBar.setColumns(10);
		statusBar.setEditable(false);
		panel.add(statusBar);
		
		/* menu bar */
		menuBar = new JMenuBar();
		mainFrame.setJMenuBar(menuBar);
		
		mnFile = new JMenu("File");
		menuBar.add(mnFile);
		
		mntmNew = new JMenuItem("New");
		mntmNew.addActionListener(new ActionListener() { // NEW button listener
			public void actionPerformed(ActionEvent evt) {
				newdocActionPerformed(evt);
			}
		});
		mntmNew.setEnabled(false);
		mnFile.add(mntmNew);
		
		mntmList = new JMenuItem("List");
		mntmList.addActionListener(new ActionListener() { // LIST button listener
			public void actionPerformed(ActionEvent evt) {
				listActionPerformed(evt);
			}
		});
		mntmList.setEnabled(false);
		mnFile.add(mntmList);
		
		mnAccount = new JMenu("Account");
		menuBar.add(mnAccount);
		
		mntmRegister = new JMenuItem("Register");
		mntmRegister.addActionListener(new ActionListener() { // REGISTER menu listener
			public void actionPerformed(ActionEvent evt) {
				registerActionPerformed(evt);
			}
		});
		mnAccount.add(mntmRegister);
		
		mntmLogin = new JMenuItem("Login");
		mntmLogin.addActionListener(new ActionListener() { // LOGIN menu listener
			public void actionPerformed(ActionEvent evt) {
				loginActionPerformed(evt);
			}
		});
		mnAccount.add(mntmLogin);
		
		mntmLogout = new JMenuItem("Logout");
		mntmLogout.addActionListener(new ActionListener() { // LOGOUT menu listener
			public void actionPerformed(ActionEvent evt) {
				logoutActionPerformed(evt);
			}
		});
		mnAccount.add(mntmLogout);
		mntmLogout.setEnabled(false);
		
		mnHelp = new JMenu("Help");
		menuBar.add(mnHelp);
		
		mntmUsage = new JMenuItem("Usage");
		mnHelp.add(mntmUsage);
	}
	
	/************************
	 * event triggers 
	 ************************/
	private static void registerActionPerformed(ActionEvent evt) {
		JDialog regDialog = registerDialog();
		regDialog.setVisible(true);
	}

	private static void loginActionPerformed(ActionEvent evt) {
		JDialog logDialog = loginDialog();
		logDialog.setVisible(true);
	}
	
	private static void logoutActionPerformed(ActionEvent evt) {
		try {
			logoutRequest();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void newdocActionPerformed(ActionEvent evt) {
		JDialog docDialog = newDocDialog();
		docDialog.setVisible(true);
	}

	private static void listActionPerformed(ActionEvent evt) {
		JDialog listDialog = fileExplorer();
		if (listDialog != null) listDialog.setVisible(true);
	}
	
	private static void shareActionPerformed(ActionEvent evt, String arg) {
		JDialog shareDialog = shareDocDialog(arg);
		shareDialog.setVisible(true);
	}
	
	private static void editActionPerformed(ActionEvent evt, String arg) {
		JDialog editDialog = editSectionDialog(arg);
		editDialog.setVisible(true);		
	}
	/********************************** UI DIALOG BUILDERS ***********************************************/
	
	private static JDialog registerDialog() {
		
		JDialog dialog = new JDialog(mainFrame, "Register", true);
		JPanel panel = new JPanel();
		panel.setLayout(new GridBagLayout());
		dialog.setSize(400, 150);
		dialog.setResizable(false);
		dialog.setLocationRelativeTo(mainFrame);
	
		GridBagConstraints layout_cs = new GridBagConstraints();	//Panel layout constraints
		layout_cs.fill = GridBagConstraints.HORIZONTAL;
		
		/* panel components*/
		JLabel userLabel = new JLabel("Username : ");
		JLabel passLabel = new JLabel("Password : ");
		JTextField userField = new JTextField(16);
		JPasswordField passField = new JPasswordField(16);
		JButton regButton = new JButton("Register");
		
		/* adding components to dialog pane */
		layout_cs.gridx = 0;
		layout_cs.gridy = 0;
		panel.add(userLabel, layout_cs);
		
		layout_cs.gridx = 1;
		layout_cs.gridy = 0;
		panel.add(userField, layout_cs);
		
		layout_cs.gridx = 0;
		layout_cs.gridy = 1;
		panel.add(passLabel, layout_cs);
		
		layout_cs.gridx = 1;
		layout_cs.gridy = 1;
		panel.add(passField, layout_cs);
		
		layout_cs.gridx = 1;
		layout_cs.gridy = 3;
		panel.add(regButton, layout_cs);
		
		dialog.add(panel);
		
		/* button listener */
		regButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String username = new String(userField.getText());
				char[] password = passField.getPassword();
				
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
						}
						else {
							JOptionPane.showMessageDialog( 
									mainFrame, 
									"This username is not available",
									"Invalid username/password", 
									JOptionPane.INFORMATION_MESSAGE);
						}
						dialog.dispose();
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
		JPanel panel = new JPanel();
		panel.setLayout(new GridBagLayout());
		dialog.setSize(400, 150);
		dialog.setResizable(false);
		dialog.setLocationRelativeTo(mainFrame);
	
		GridBagConstraints layout_cs = new GridBagConstraints();	//Panel layout constraints
		layout_cs.fill = GridBagConstraints.HORIZONTAL;
		
		/* panel components*/
		JLabel userLabel = new JLabel("Username : ");
		JLabel passLabel = new JLabel("Password : ");
		JTextField userField = new JTextField(16);
		JPasswordField passField = new JPasswordField(16);
		JButton logButton = new JButton("Login");
		
		/* adding components to dialog pane */
		layout_cs.gridx = 0;
		layout_cs.gridy = 0;
		panel.add(userLabel, layout_cs);
		
		layout_cs.gridx = 1;
		layout_cs.gridy = 0;
		panel.add(userField, layout_cs);
		
		layout_cs.gridx = 0;
		layout_cs.gridy = 1;
		panel.add(passLabel, layout_cs);
		
		layout_cs.gridx = 1;
		layout_cs.gridy = 1;
		panel.add(passField, layout_cs);
		
		layout_cs.gridx = 1;
		layout_cs.gridy = 3;
		panel.add(logButton, layout_cs);
		
		dialog.add(panel);
		
		/* button listeners */
		logButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e){
				String username = new String(userField.getText());
				char[] password = passField.getPassword();
								
				if (!(isValidUser(username, password))) {
					JOptionPane.showMessageDialog(
							mainFrame, 
							"Please choose a valid Username and Password", 
							"Invalid username/password", 
							JOptionPane.ERROR_MESSAGE);
				}
			
				else {
					/* connecting to server and sending login request */
					InetSocketAddress server_address = new InetSocketAddress(Config.SERVER_IP, Config.SERVER_PORT);
					try {
						client_ch = SocketChannel.open(server_address);
						loginRequest(username, password);
						byte r;
						if ( (r=getResponse()) == Config.SUCCESS ) {
							statusBar.setText(username + " - Online");
							mntmRegister.setEnabled(false);
							mntmLogin.setEnabled(false);
							mntmLogout.setEnabled(true);
							userName = new String(username);
							enableOnlineService();
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
								"COM_ERROR", 
								JOptionPane.ERROR_MESSAGE);
					} 
				}
			}
		});

		return dialog;
	}

	private static JDialog newDocDialog() {
		
		JDialog dialog = new JDialog(mainFrame, "New document creation", true);
		JPanel panel = new JPanel();
		panel.setLayout(new GridBagLayout());
		dialog.setSize(400, 150);
		dialog.setResizable(false);
		dialog.setLocationRelativeTo(mainFrame);
		
		JLabel doc_name = new JLabel("Name: ");
		JLabel sections_num = new JLabel("Sections: ");
		JTextField name = new JTextField(16);
		JTextField sections = new JTextField(16);
		JButton createButton = new JButton("Create");
		JButton cancButton = new JButton("Cancel");
		
		GridBagConstraints layout_cs = new GridBagConstraints();	//Panel layout constraints
		layout_cs.fill = GridBagConstraints.HORIZONTAL;
		
		/* adding components to dialog pane */
		layout_cs.gridx = 0;
		layout_cs.gridy = 0;
		panel.add(doc_name, layout_cs);
		
		layout_cs.gridx = 1;
		layout_cs.gridy = 0;
		panel.add(name, layout_cs);
		
		layout_cs.gridx = 0;
		layout_cs.gridy = 1;
		panel.add(sections_num, layout_cs);
		
		layout_cs.gridx = 1;
		layout_cs.gridy = 1;
		panel.add(sections, layout_cs);
		
		layout_cs.gridx = 3;
		layout_cs.gridy = 0;
		panel.add(createButton, layout_cs);
		
		layout_cs.gridx = 3;
		layout_cs.gridy = 1;
		panel.add(cancButton, layout_cs);
		
		dialog.add(panel);
		
		/* button listeners */
		cancButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				name.setText("");
				sections.setText("");
			}
		});
		
		createButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String filename = new String(name.getText());
				int section_num = Integer.parseInt(sections.getText());
				
				try {
					newDocRequest(filename, section_num);
				} catch (UnsupportedEncodingException e2) {
					e2.printStackTrace();
				}
				try {
					byte r;
					if ( (r=getResponse()) == Config.SUCCESS ) {
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
		});
				
		return dialog;
	}
	
	private static JDialog shareDocDialog(String arg) {
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
		
		/* adding components to dialog pane */
		layout_cs.gridx = 0;
		layout_cs.gridy = 0;
		panel.add(dest_label, layout_cs);
		
		layout_cs.gridx = 1;
		layout_cs.gridy = 0;
		panel.add(dest, layout_cs);
		
		layout_cs.gridx = 2;
		layout_cs.gridy = 0;
		panel.add(shareButton, layout_cs);
		
		dialog.add(panel);
		
		shareButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String destinatary = new String(dest.getText());
				String filename = new String(arg);
				try {
					shareRequest(filename, destinatary);
					byte r;
					if ( (r=getResponse()) == Config.SUCCESS ) {
						JOptionPane.showMessageDialog(mainFrame, filename + " successfully shared with " + destinatary);
						dialog.dispose();
					}
					else {
						if (r==Config.INVALID_DEST) {
							JOptionPane.showMessageDialog(mainFrame, Config.ERROR_LOG(r));
							dest.setText("");
						}
					}
				} catch (IOException io_ex) {
					JOptionPane.showMessageDialog(mainFrame, io_ex.getMessage());
				}
			}
		});
				
		return dialog;
	}
	
	private static JDialog editSectionDialog(String filename) {
		JDialog dialog = new JDialog(mainFrame, "Edit section", true);
		JPanel panel = new JPanel();
		
		panel.setLayout(new GridBagLayout());
		dialog.setSize(400, 150);
		dialog.setResizable(false);
		dialog.setLocationRelativeTo(file_explorer);
		
		JLabel sect_label = new JLabel("Section #: ");
		JTextField sect = new JTextField(16);
		JButton editBtn = new JButton("Edit");
		
		GridBagConstraints layout_cs = new GridBagConstraints();	//Panel layout constraints
		layout_cs.fill = GridBagConstraints.HORIZONTAL;
		
		/* adding components to dialog pane */
		layout_cs.gridx = 0;
		layout_cs.gridy = 0;
		panel.add(sect_label, layout_cs);
		
		layout_cs.gridx = 1;
		layout_cs.gridy = 0;
		panel.add(sect, layout_cs);
		
		layout_cs.gridx = 2;
		layout_cs.gridy = 0;
		panel.add(editBtn, layout_cs);
		
		dialog.add(panel);
		
		editBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					String file_text = downloadFile(filename, Integer.parseInt(sect.getText()));
					if (file_text == null) {
						JOptionPane.showMessageDialog(mainFrame, "Error retrieving file");
					}
					else { // show text editor window
						JDialog editor = fileEditor(filename, file_text, (Integer.parseInt(sect.getText())));
						editor.setLocation(mainFrame.getX() + mainFrame.getWidth(), mainFrame.getY());
						editor.setVisible(true);
						dialog.dispose();
						enableChat();
					}
				} catch (UnsupportedEncodingException encode_ex) {
					encode_ex.printStackTrace();
				}
			}
		});
		
		return dialog;
	}
	
	private static JDialog fileExplorer() {
		JDialog dialog = new JDialog(mainFrame, "My files", true);
		JPanel panel = new JPanel();
		
		mntmEdit = new JMenuItem("Edit");
		mntmShowSection = new JMenuItem("Show section");
		mntmShow = new JMenuItem("Show document");
		mntmShare = new JMenuItem("Share");
		
		dialog.setSize(320, 240);
		dialog.setResizable(true);
		dialog.setLocationRelativeTo(mainFrame);
		panel.setLayout(new BorderLayout());
		panel.setPreferredSize(new Dimension(480, 600));
		
		list = new DefaultListModel<>();
		file_explorer = new JList<String>(list);
		file_explorer.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		exp_scroll = new JScrollPane(file_explorer);
		exp_scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		
		final JPopupMenu selectionMenu = new JPopupMenu();
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
		mntmShare.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				shareActionPerformed(evt, list.getElementAt(file_explorer.getSelectedIndex()));
			}
		});
		
		mntmEdit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				editActionPerformed(evt, list.getElementAt(file_explorer.getSelectedIndex()));
			}
		});
		
		panel.add(file_explorer);
		dialog.add(panel);
		boolean res = false;
		try {
			res = listDocsRequest();
		} catch (IOException req_ex) {
			JOptionPane.showMessageDialog(mainFrame, "Error retrieving file list", "ERROR", JOptionPane.ERROR_MESSAGE);
		}
		if (res) return dialog;
		return null;
	}

	private static JDialog fileEditor(String filename, String text, int section) {
		JDialog dialog = new JDialog();
		JMenuBar menu_bar = new JMenuBar();
		JMenu mnFile = new JMenu("File");
		JMenuItem mntmSave = new JMenuItem("Save");
		JTextArea textArea = new JTextArea();
		JScrollPane textScroll = new JScrollPane(textArea);
		
		dialog.setTitle(filename);
		dialog.setSize(new Dimension(800, 600));
		
		menu_bar.add(mnFile);
		mnFile.add(mntmSave);
		
		textArea.setBorder(panel.getBorder());
		textArea.setText(text);
		
		textScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		textScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		
		mntmSave.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				try {
					endEditRequest(filename, textArea.getText(), section);
					byte r;
					r = getResponse();
					if ( r != Config.SUCCESS) { // saving file failed
						JOptionPane.showMessageDialog(dialog, "Error saving file: " + Config.ERROR_LOG(r));
					}
					else {
						JOptionPane.showMessageDialog(dialog, "File saved successfully!");
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		
		dialog.setJMenuBar(menu_bar);
		dialog.add(textArea);
		return dialog;
	}
	
	/******************************************** REQUESTS *************************************************/
	
	/* Sends a login request to the server */
	private static void loginRequest(String username, char[] password) throws UnsupportedEncodingException {
		
		ByteBuffer request = ByteBuffer.allocate(Config.REQ_BUF_SIZE);
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

	/* Sends a logout request to the server */
	private static void logoutRequest() throws IOException {
		
		if (!serverOnline()) {
			disableOnlineService();
			mntmRegister.setEnabled(true);
			mntmLogin.setEnabled(true);
			mntmLogout.setEnabled(false);
			JOptionPane.showMessageDialog(mainFrame, "Server unreachable!");
			return;
		}
		ByteBuffer buffer = ByteBuffer.allocate(Config.REQ_BUF_SIZE);
		buffer.put(Config.LOGOUT_R);
		buffer.put((byte) userName.length());
		buffer.put(userName.getBytes(Config.DEFAULT_ENCODING));
		buffer.flip();
		client_ch.write(buffer);
		
		byte r;
		if ( (r=getResponse()) != Config.SUCCESS) {
			JOptionPane.showMessageDialog(mainFrame, Config.ERROR_LOG(r));
		}
		else {
			disableOnlineService();
			mntmRegister.setEnabled(true);
			mntmLogin.setEnabled(true);
			mntmLogout.setEnabled(false);
			JOptionPane.showMessageDialog(mainFrame, "Logout succeeded");
		}
		return;
	}
	
	/* Sends a new document creation request to the server */
	private static void newDocRequest(String filename, int sections) throws UnsupportedEncodingException {
		
		if (!serverOnline()) {
			JOptionPane.showMessageDialog(mainFrame, "Server currently offline, try again later");
			return;
		}
		
		ByteBuffer request = ByteBuffer.allocate(Config.REQ_BUF_SIZE);
		request.put(Config.NEW_R);
		request.put((byte) userName.length());
		request.put((byte) filename.length());
		request.put((byte) sections);
		
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
	
	/* Sends a request to the server to receive the list of proprietary and shared files */
	private static boolean listDocsRequest() throws UnsupportedEncodingException {
		
		if (!serverOnline()) {
			JOptionPane.showMessageDialog(mainFrame, "Server currently offline, try again later");
			return false;
		}
		
		ByteBuffer request = ByteBuffer.allocate(Config.REQ_BUF_SIZE);
		byte name_size = (byte) userName.length();
		
		request.put(Config.LIST_R);
		request.put(name_size);
		request.put(userName.getBytes(Config.DEFAULT_ENCODING));
		
		request.flip();
		
		try {
			client_ch.write(request);
			byte r;
			if ( (r = getResponse()) != Config.SUCCESS ) { // file list retrievement failed
				JOptionPane.showMessageDialog(mainFrame, Config.ERROR_LOG(r));
				return false;
			}
			else { // receiving file list
				ByteBuffer buffer = ByteBuffer.allocate(Config.MAX_BUF_SIZE);
				client_ch.read(buffer);
				buffer.flip();
				byte[] files_list = buffer.array();
				String files = new String(files_list, Config.DEFAULT_ENCODING);
				StringTokenizer s_tok = new StringTokenizer(files);
				while (s_tok.hasMoreTokens()) {
					list.addElement(s_tok.nextToken());
				}
			}
		} catch (IOException req_ex) {
			JOptionPane.showMessageDialog(mainFrame, req_ex.getMessage());
		}
		return true;
	}
	
	/* Sends a request to share a given file with a destinatary user */
	private static void shareRequest(String filename, String destinatary) throws UnsupportedEncodingException {
		
		if (!serverOnline()) {
			JOptionPane.showMessageDialog(mainFrame, "Server currently offline, try again later");
		}
		
		ByteBuffer request = ByteBuffer.allocate(Config.REQ_BUF_SIZE);
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
	
	/* Sends a request to download the selected file */
	private static String downloadFile(String filename, int section) throws UnsupportedEncodingException {
		
		if (!serverOnline()) {
			JOptionPane.showMessageDialog(mainFrame, "Server currently offline, try again later");
		}
		
		ByteBuffer request = ByteBuffer.allocate(Config.REQ_BUF_SIZE);
		byte name_size = (byte) userName.length();
		byte fname_size = (byte) filename.length();
		
		request.put(Config.DOWNLOAD_R);
		request.put(name_size);
		request.put(fname_size);
		
		request.put(userName.getBytes(Config.DEFAULT_ENCODING));
		request.put(filename.getBytes(Config.DEFAULT_ENCODING));
		request.put((byte) section); 
		
		request.flip();
		try { 	//send download request
			client_ch.write(request);
			System.out.println("Download request send" );
			byte r = getResponse();
			
			if ( r == Config.RECEIVING_BYTES ) {
				System.out.println("Received positive response, downloading file...");
				ByteBuffer receive = ByteBuffer.allocate(Config.MAX_BUF_SIZE);
				try {
					client_ch.read(receive);
					byte[] received_b = receive.array();
					String file = new String(received_b, Config.DEFAULT_ENCODING);
					System.out.println("File received successfully!");	
					return file;
				} catch (IOException io_ex) {
					JOptionPane.showMessageDialog(mainFrame, io_ex.getMessage());
				}
			}
			else if ( r == Config.NO_BYTES ) {
				return new String("Empty");
			}
			else {
				JOptionPane.showMessageDialog(mainFrame, Config.ERROR_LOG(r));
				return null;
			}

		} catch (IOException req_ex) {
			JOptionPane.showMessageDialog(mainFrame, req_ex.getMessage());
			return null;
		}
		return null;
		
	}
	
	/* Sends the new file version to the server */
	private static void endEditRequest(String filename, String text, int section) throws IOException { 
		ByteBuffer request = ByteBuffer.allocate(Config.MAX_BUF_SIZE);
		request.put(Config.END_EDIT_R);
		request.put((byte) userName.length());
		request.put((byte) filename.length());
		request.put((byte) section);
		request.put((byte) text.length());
		
		request.put(userName.getBytes(Config.DEFAULT_ENCODING));
		request.put(filename.getBytes(Config.DEFAULT_ENCODING));
		request.put(text.getBytes(Config.DEFAULT_ENCODING));
		
		request.flip();
		try {
			client_ch.write(request);
		} catch (IOException req_ex) {
			JOptionPane.showMessageDialog(mainFrame, req_ex.getMessage());
			return;
		}
		
	}
	/************************************ UTILITY FUNCTIONS ***********************************************/
	
	/* Checks server status (used at the beginning of each request function */
	private static boolean serverOnline () {
		if ( client_ch.isConnected()) return true;
		return false;
	}
	
	/* Waits to receive a response code from the server for the current request */
	private static byte getResponse() throws IOException {
		ByteBuffer response = ByteBuffer.allocate(1);		
		client_ch.read(response);	
		response.flip();
		byte code = response.get();
		return code;
	}

	/* Checks if the chosen <username, password> meet the requirements */
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
	private static void enableOnlineService() {

		mntmNew.setEnabled(true);
		mntmList.setEnabled(true);
		btnSend.setEnabled(true);
		statusBar.setText("Online - " + userName);
		statusBar.setBackground(Color.GREEN);
		
	}

	/* Disables online functionalities */
	private static void disableOnlineService() {
		
		mntmNew.setEnabled(false);
		mntmList.setEnabled(false);
		btnSend.setEnabled(false);
		statusBar.setText("Offline");
		statusBar.setBackground(Color.GRAY);
		disableChat();
	}
	
	/* Enables chat service for current file work-group */
	private static void enableChat() {
		chat_history.setEnabled(true);
		message_box.setEnabled(true);
		chat_history.setBackground(Color.WHITE);
		message_box.setBackground(Color.WHITE);
	}
	
	/* Disables chat service */
	private static void disableChat() {
		chat_history.setEnabled(false);
		message_box.setEnabled(false);
		chat_history.setBackground(Color.GRAY);
		message_box.setBackground(Color.GRAY);
	}

}
