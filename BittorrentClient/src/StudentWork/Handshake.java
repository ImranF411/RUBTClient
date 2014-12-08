package StudentWork;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Handshake class; generates and parsed handshakes.
 * @author Imran
 * @author Joe Kennedy
 * 
 */
public class Handshake{
	final String protocol = "BitTorrent protocol";
	final int baseLength = 49;
	
	ByteBuffer info_hash;
	byte[] peer_id;
	
	/**
	 * @param info_hash infohash to include in handshake
	 * @param peer_id_string peerID to include in handshake
	 */
	Handshake(ByteBuffer info_hash, String peer_id_string ){
		this.info_hash = info_hash;
		this.peer_id = peer_id_string.getBytes();
	}
	
	/**
	 * @param incoming byte[] of received handshake to parse.
	 * @return Handshake object of received handshake.
	 */
	public static Handshake parse(byte[] incoming){
		int index = 0;
		int length = (int)incoming[index];
		index++;
		String protoIN = "";
		for(int i = 0; i < length; index++, i++)
			protoIN = protoIN+((char)incoming[index]);
		index = index+8;
		ByteBuffer ihash = ByteBuffer.allocate(20);
		for(int i = 0; i < 20; index++,i++){
			ihash.put(incoming[index]);
		}
		String peerID = "";
		for(int i = 0; i < 20; index++,i++){
			peerID = peerID+((char)incoming[index]);
		}
		
		//System.out.println(peerID);
		return new Handshake(ihash, peerID);
	}
	
	
	
	/**
	 * @return byte[] of handshake to send.
	 */
synchronized byte[] Send(DataOutputStream dos){
		
		ByteBuffer buffer = ByteBuffer.allocate(baseLength+protocol.length());
		byte[] reserved = new byte[8];
		
		buffer.put((byte) protocol.length());
		buffer.put(protocol.getBytes());
		buffer.put(reserved);
		info_hash.rewind();
		buffer.put(info_hash);
		buffer.put(peer_id);
		
		try {
			dos.write(buffer.array());
		} catch (IOException e) {
			// TODO Auto-generated catch block
//			e.printStackTrace();
		}
		
		//info_hash.rewind();
		
		return buffer.array();
	}
}
