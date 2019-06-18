package turing_pkg;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.JPasswordField;

public class TestDialogClass extends JDialog {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private JTextField textField;
	private JPasswordField passwordField;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		try {
			TestDialogClass dialog = new TestDialogClass();
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			dialog.setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create the dialog.
	 */
	public TestDialogClass() {
		setBounds(100, 100, 450, 300);
		getContentPane().setLayout(null);
		
		JLabel lblNewLabel = new JLabel("Username :"); 
		lblNewLabel.setHorizontalAlignment(SwingConstants.TRAILING);
		lblNewLabel.setBounds(60, 70, 75, 20);
		getContentPane().add(lblNewLabel);
		
		textField = new JTextField();
		textField.setBounds(155, 70, 170, 20);
		getContentPane().add(textField);
		textField.setColumns(10);
		
		JLabel lblPassword = new JLabel("Password :");
		lblPassword.setHorizontalAlignment(SwingConstants.TRAILING);
		lblPassword.setBounds(60, 110, 75, 20);
		getContentPane().add(lblPassword);
		
		passwordField = new JPasswordField();
		passwordField.setBounds(155, 110, 170, 20);
		getContentPane().add(passwordField);
		
		JButton btnRegister = new JButton("Register");
		btnRegister.setBounds(161, 155, 89, 23);
		getContentPane().add(btnRegister);
		
		JButton btnLogin = new JButton("Login");
		btnLogin.setBounds(161, 190, 89, 23);
		getContentPane().add(btnLogin);
	}
}
