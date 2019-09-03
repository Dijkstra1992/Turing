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
	public static final String FILE_PATH 		 	= "C:\\Users\\Pietro\\Desktop\\TuringProjectWS\\TuringDB\\"; 
//	public static final String FILE_PATH			= ""; // insert local DB path here
	
	/* request type codes */
	public static final byte LOGIN_R		= (byte) 0x000;
	public static final byte LOGOUT_R 		= (byte) 0x001;
	public static final byte NEW_R	 		= (byte) 0x002;
	public static final byte EDIT_R			= (byte) 0x003;
	public static final byte END_EDIT_R		= (byte) 0x004;
	public static final byte SHARE_R 		= (byte) 0x005;
	public static final byte LIST_R			= (byte) 0x006;
	public static final byte SHOW_R		 	= (byte) 0x007;
	public static final byte SAVE_R			= (byte) 0x008;
	public static final byte NOTIFY_SERV_R  = (byte) 0x009;	
	
		
	/* response & error codes */
	public static final byte SUCCESS 	 	= (byte) 0x100;			
	public static final byte INVALID_PASS	= (byte) 0x101;
	public static final byte ALREADY_ON 	= (byte) 0x102;
	public static final byte UNKNOWN_USER	= (byte) 0x103;
	public static final byte UNKNOWN_ERROR 	= (byte) 0x104;
	public static final byte NO_SUCH_FILE	= (byte) 0x105;
	public static final byte INVALID_DEST   = (byte) 0x106;
	public static final byte EMPTY_LIST		= (byte) 0x107;
	public static final byte COM_ERROR		= (byte) 0x108;
	public static final byte RECEIVING_BYTES= (byte) 0x109;
	public static final byte NO_BYTES 		= (byte) 0x110;
	public static final byte DUPLICATE_FILE = (byte) 0x111;
	
	/* file permissions */
	public static final byte CREATOR		= (byte) 0x200;
	public static final byte SHARED			= (byte) 0x201;
	
	/* file section status */
	public static final byte FREE_SECTION	= (byte) 0x250;
	public static final byte IN_EDIT		= (byte) 0x251;
	
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
