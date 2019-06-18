package turing_pkg;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

final class Config {
	
	/* common values */
	public static final int BUF_SIZE		 	 = 1024;
	public static final String SERVER_IP 	     = "localhost";
	public static final int SERVER_PORT 		 = 2026;
	public static final int REMOTE_SERVICE_PORT	 = 2027;
	public static final String DEFAULT_ENCODING	 = "UTF-8";
													//server file storage path
	public static final String FILE_PATH 		 = "C:\\Users\\Pietro\\Desktop\\Progetto_Reti\\Code\\TuringDB\\"; 
	public static final String TEMP_FOLDER		 = "C:\\Users\\Pietro\\Desktop\\Progetto_Reti\\Code\\TuringDB\\TEMP";
	
	/* request types codification */
	public static final byte LOGIN_R		= (byte) 0;	// login request
	public static final byte LOGOUT_R 		= (byte) 1;	// logout request
	public static final byte NEW_R	 		= (byte) 2;	// create new document request
	public static final byte EDIT_R			= (byte) 3; // edit an existing document request
	public static final byte END_EDIT_R		= (byte) 4; // close editing section request
	public static final byte SHARE_R 		= (byte) 5;	// share document request
	public static final byte LIST_R			= (byte) 6; // lists all proprietary/shared files
	public static final byte SHOW_R		 	= (byte) 7; // download section request
	public static final byte SAVE_R			= (byte) 8; // save edited section request
	
		
	/* errors and messages codification */
	public static final byte SUCCESS 	 	= (byte) 0;
	public static final byte INVALID_PASS	= (byte) 1;
	public static final byte ALREADY_ON 	= (byte) 3;
	public static final byte UNKNOWN_USER	= (byte) 4;
	public static final byte UNKNOWN_ERROR 	= (byte) 5;
	public static final byte NO_SUCH_FILE	= (byte) 6;
	public static final byte INVALID_DEST   = (byte) 7;
	public static final byte EMPTY_LIST		= (byte) 8;
	public static final byte INVALID_SECT	= (byte) 9;
	public static final byte RECEIVING_BYTES= (byte) 10;
	public static final byte NO_BYTES 		= (byte) 11;
	
	/* file permissions codification  */
	public static final byte CREATOR		= (byte) 0;
	public static final byte SHARED			= (byte) 1;
	
	/* file section status */
	public static final byte FREE_SECTION	= (byte) 0;
	public static final byte IN_EDIT		= (byte) 1;
	
	/* CLIENT status codes */
	public static final byte OFFLINE		= (byte) 0;
	public static final byte ONLINE			= (byte) 1;
	public static final byte EDITING		= (byte) 2;
	
	private Config () {}
	 
 	/* Converts a given ERROR CODE into a printable message that describes the error */ 
	public static final String ERROR_LOG(byte ERROR_CODE) {  
		String log = new String(); 
		
		switch (ERROR_CODE) {
			case INVALID_PASS:
				log = "Invalid password";
				break;
			case ALREADY_ON: 
				log = "User already online";
				break;
			case UNKNOWN_USER:
				log = "Unknown user";
				break;
			case NO_SUCH_FILE:
				log = "File not found!";
				break;
			case INVALID_DEST:
				log = "Invalid destinatary";
				break;
			case EMPTY_LIST:
				log = "No documents yet";
				break;
			default: 
				log = "Unknown error occurred!";
				break;
		}
		return log;
	}
	
	/* char[] to byte[] conversion */
	public static final byte[] toBytes (char[] chars, String encoding) {
		
		CharBuffer c_buffer = CharBuffer.wrap(chars);
		ByteBuffer b_buffer = Charset.forName(encoding).encode(c_buffer);
		
		byte[] result = Arrays.copyOfRange(b_buffer.array(), b_buffer.position(), b_buffer.limit());
		// clear sensitive data
		Arrays.fill(c_buffer.array(), '0');
		Arrays.fill(b_buffer.array(), (byte) 0);
		
		return result;
	}
	 
	/* byte[] to char[] conversion */
	public static final char[] toChars (byte[] bytes) {
		
		char[] result = new char[bytes.length >> 1];
		
		for (int i=0; i<result.length; i++) {
			int pos = i << 1;
			char current = (char) (((bytes[pos]&0x00FF)<<8) + (bytes[pos+1]&0x00FF));
			result[i] = current;
		}
		
		return result;
	}

}
