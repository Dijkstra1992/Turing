package turing_pkg;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

final class Config {
	
	/* configuration values */
	public static final int BUF_SIZE		 	 	= 4096;
	public static final int DATAGRAM_PKT_SIZE		= 512;
	public static final int SERVER_PORT 		 	= 3026;
	public static final int REMOTE_SERVICE_PORT	 	= 3027;
	public static final int CHAT_SERVICE_PORT	 	= 3028;
	public static final int NOTIFY_SERVICE_PORT 	= 3029;
	public static final String SERVER_IP 	     	= "127.0.0.1";  
	public static final String DEFAULT_ENCODING	 	= "UTF-8";
	public static final String FILE_PATH 		 	= "C:\\Users\\Pietro\\Desktop\\Turing\\TuringDB\\"; 
	
	/* request type codes */
	public static final byte LOGIN_R		= (byte) 10;
	public static final byte LOGOUT_R 		= (byte) 11;
	public static final byte NEW_R	 		= (byte) 12;
	public static final byte EDIT_R			= (byte) 13;
	public static final byte END_EDIT_R		= (byte) 14;
	public static final byte SHARE_R 		= (byte) 15;
	public static final byte LIST_R			= (byte) 16;
	public static final byte SHOW_R		 	= (byte) 17;
	public static final byte SAVE_R			= (byte) 18;
	public static final byte NOTIFY_SERV_R  = (byte) 19;	
	
		
	/* response & error codes */
	public static final byte SUCCESS 	 	= (byte) 50;			
	public static final byte INVALID_PASS	= (byte) 51;
	public static final byte ALREADY_ON 	= (byte) 52;
	public static final byte UNKNOWN_USER	= (byte) 53;
	public static final byte UNKNOWN_ERROR 	= (byte) 54;
	public static final byte NO_SUCH_FILE	= (byte) 55;
	public static final byte INVALID_DEST   = (byte) 56;
	public static final byte EMPTY_LIST		= (byte) 57;
	public static final byte COM_ERROR		= (byte) 58;
	public static final byte RECEIVING_BYTES= (byte) 59;
	public static final byte NO_BYTES 		= (byte) 60;
	public static final byte DUPLICATE_FILE = (byte) 61;
	
	/* file permissions */
	public static final byte CREATOR		= (byte) 100;
	public static final byte SHARED			= (byte) 101;
	
	/* file section status */
	public static final byte FREE_SECTION	= (byte) 120;
	public static final byte IN_EDIT		= (byte) 121;
	
	private Config () {}
	 
 	/* Converts a given ERROR_CODE into a printable message that describes the error */ 
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
			case DUPLICATE_FILE:
				log = "User has already this file";
				break;
			case INVALID_DEST:
				log = "Invalid destinatary";
				break;
			case EMPTY_LIST:
				log = "No documents yet";
				break;
			case COM_ERROR:
				log = "Connection error";
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
