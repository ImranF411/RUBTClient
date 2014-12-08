package StudentWork;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * Message class; handles creation and parsing messages.
 * @author Imran
 * @author Joe Kennedy
 */
public class Message{
	final static byte KEEP_ALIVE = -1;
	final static byte CHOKE = 0;
	final static byte UNCHOKE = 1;
	final static byte INTERESTED =2;
	final static byte NOT_INTERESTED = 3;
	final static byte HAVE = 4;
	final static byte BITFIELD = 5;
	final static byte REQUEST = 6;
	final static byte PIECE = 7;
	final static byte CANCEL = 8;
	final static int BLOCKLENGTH = 16384; //NOT THE PIECE SIZE SPECIFIED BY THE TORRENT. We will be downloading pieces in blocks of this length. 
	
	int length;
	byte id;
	byte[] bfield;
	int have;
	
	//If piece or request
	int pieceIndex;
	int pieceOffset;
	byte[] block;	//piece only
	int bLen;		//request only
	
	//Default constructor. Makes empty message for received messages.
	/**
	 * Creates empty Message object. Used for receiving messages.
	 */
	Message (){}
	
	//Initialized constructor. Makes a message to send.
	/**
	 * @param msg_len length of message
	 * @param msg_id int specifying message type.
	 */
	Message (int msg_len, byte msg_id){
		length = msg_len;
		id = msg_id;
	}
	
	//For choke/unchoke
	/**
	 * @return byte[] of message that chokes/unchokes peer
	 */
	byte[] sendChokeStatus(){
		ByteBuffer ret = ByteBuffer.allocate(5);
		ret.putInt(length);
		ret.put(id);
		return ret.array();
	}
	
	/**
	 * @return byte[] of interest message
	 */
	byte[] sendInter(){
		ByteBuffer ret = ByteBuffer.allocate(5);
		ret.putInt(length);
		ret.put(id);
		return ret.array();
	}
	
	/**
	 * @param bLen length of block wanted by peer
	 * @return byte[] of request message
	 */
	byte[] sendReq(int bLen){
		ByteBuffer ret = ByteBuffer.allocate(17);
		ret.putInt(length);
		ret.put(id);
		ret.putInt(pieceIndex);
		ret.putInt(pieceOffset);
		ret.putInt(bLen);
		return ret.array();
	}
	
	/**
	 * @param piece byte[] of data to send
	 * @return byte[] of piece message to send
	 */
	byte[] sendPiece(byte[] piece){
		ByteBuffer ret = ByteBuffer.allocate(13+piece.length);
		ret.putInt(length);
		ret.put(id);
		ret.putInt(pieceIndex);
		ret.putInt(pieceOffset);
		ret.put(piece);
		return ret.array();
	}
	
	/**
	 * @param din input stream to read message from
	 * @return Message recieved
	 * @throws IOException If message is bad or not received well, throw IOException
	 */
	Message receive(DataInputStream din, String peerID, Socket socket) throws IOException{
		
		length = din.readInt();
		
		if(length < 0 || length > 131081){
			throw new IOException("Recieved bad massage length.");
		}
		
		if (length == 0){
			//Keep alive code
		}
		
		id = din.readByte();
		
		if(id >= 0 && id < 9){
		//	System.out.println("Recieved message of type " + id);
		}else{
			//Bad
			throw new IOException("Recieved bad message ID.");
		}
		
		//Received have message
		if(id == HAVE){
			have = din.readInt();
			bfield = null;
			return this;
		}
		
		//Received bitfield
		if (id == BITFIELD){
			bfield = new byte [length-1];
			din.readFully(bfield);
			have = -1;
			return this;
		}
		
		if(id == REQUEST){
			pieceIndex = din.readInt();
			pieceOffset = din.readInt();
			bLen = din.readInt();
			return this;
		}
		
		//Received piece
		if (id == PIECE){
			pieceIndex = din.readInt();
			pieceOffset = din.readInt();
			block = new byte[length-9];
			din.readFully(block);
			return this;
		}
		
		return null;
	}
	
}
