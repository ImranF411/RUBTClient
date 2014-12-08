package StudentWork;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.BitSet;

/**
 * Peer class, manages connection and communication with peers in swarm.
 * @author Imran
 * @author Joe Kennedy
 * 
 */
public class Peer implements Runnable{
	
	private Integer port;
	private String ip;
	private String peerID;
	private byte[] bitfield;
	private Socket socket;
	DataOutputStream dos;
	DataInputStream din;
	static boolean[] writeIndex;
	static boolean[] firstRequestForPiece;
	static private boolean quit = false;
	private Handshake peerShake;
	private boolean DLingFrom = true;
	private boolean choked = true;
	
	/**
	 * @param port port of peer to connect to
	 * @param ip ip of peer to connect to
	 * @param peerID ID of peer given by tracker
	 */
	Peer(int port, String ip, String peerID){
		this.port = port;
		this.ip = ip;
		this.peerID = peerID;
		try {
			this.socket = new Socket(ip,port);
			this.dos = new DataOutputStream(socket.getOutputStream());
			this.din = new DataInputStream(socket.getInputStream());
		} catch (IOException e) {
			GUI.updateOutput("Could not connect to peer "+peerID+". Closing connection.");
			GUI.updateOutput(e.toString());
			return;
		}
		this.peerShake = null;
	}
	
	/**
	 * @param pID peer ID of peer
	 * @param incoming socket of peer
	 * @param inShake handshake of peer.
	 * @param dos DataOutputStream of socket to peer
	 * @param din DataInputStream of socket to peer
	 */
	Peer(String pID, Socket incoming, Handshake inShake, DataOutputStream dos, DataInputStream din){
		this.peerID = pID;
		this.socket = incoming;
		this.port = socket.getPort();
		this.dos = dos;
		this.din = din;
		this.ip = socket.getInetAddress().toString();
		this.peerShake = inShake;
	}
	
	/**
	 * @return the peer's port
	 */
	int getPort(){
		return port;
	}
	
	/**
	 * @return the peer's ip address.
	 */
	String getIP(){
		return ip;
	}
	
	/**
	 * @return the peer's ID
	 */
	String getPeerID(){
		return peerID;
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 * Takes care of connecting, handshaking, and communicating with peer.
	 */
	@Override
	public void run() {
		
		GUI.updateOutput("Starting peer "+peerID);
		
		Handshake shake = new Handshake(RUBTClient.torInfo.info_hash,"TwentyByteStringHere");
		
		byte[] incomingShake = new byte[68];
		Message msg = new Message();
		
		//Sends handshake to peer
		if(peerShake == null){
			if(quit){
				try {
					dos.close();
					din.close();
					socket.close();
					//RUBTClient.peers.remove(this);
					return;
				} catch (IOException e1) {
				}
				return;
			}
			GUI.updateOutput("Trying to send handshake to peer "+peerID+".");
			shake.Send(dos);
			try {
					GUI.updateOutput("Trying to read handshake from peer"+peerID+".");
					din.readFully(incomingShake);
			} catch (IOException e) {
				GUI.updateOutput("Could not recieve handshake from "+peerID+". Disconnecting.");
				try {
					dos.close();
					din.close();
					socket.close();
					return;
				} catch (IOException e1) {
				}
				return;
			}
				
			//Receives and checks handshake
				Handshake gotten = Handshake.parse(incomingShake);
				GUI.updateOutput("Reading handshake from peer "+peerID+".");
//				for(int i = 0; i<68; i++)
//					System.out.print(incomingShake[i]);
//				GUI.updateOutput();
				if(gotten == null){
					GUI.updateOutput("Could not read handhsake from peer "+peerID+". Disconnecting.");
					try {
						dos.close();
						din.close();
						socket.close();
						//RUBTClient.peers.remove(this);
						return;
					} catch (IOException e1) {
					}
					return;
				}
				peerShake = gotten;
				GUI.updateOutput("Recieved handshake from "+peerID+".");
				//if(Arrays.equals(gotten.info_hash.array(), shake.info_hash.array())){
//				GUI.updateOutput(Arrays.toString(peerShake.info_hash.array()));
//				GUI.updateOutput(Arrays.toString(shake.info_hash.array()));
				if(gotten.info_hash.compareTo(shake.info_hash) != 0){
					GUI.updateOutput("Handshake from peer "+peerID+" has bad info hash. Disconnecting.");
					try {
						dos.close();
						din.close();
						socket.close();
						//RUBTClient.peers.remove(this);
						return;
					} catch (IOException e1) {
					}
					return;
				}
		}
		//Get the bitfield. It only appears once/peer.
		
		
		if(DLingFrom){
			//We contacted peer.
			try {
//				GUI.updateOutput("Socket is connected for peer "+peerID+": "+socket.isConnected());
				msg.receive(din, peerID,socket);
			} catch (IOException e) {
				GUI.updateOutput("Could not recieve a good bitfield from "+peerID+". Try closing the program and running again.");
//				GUI.updateOutput("Socket for peer "+peerID+"is connected: "+socket.isConnected());
//				GUI.updateOutput("Socket for peer "+peerID+"is closed: "+socket.isClosed());
				GUI.updateOutput(e.toString());
				try {
					dos.close();
					din.close();
					socket.close();
					//RUBTClient.peers.remove(this);
					return;
				} catch (IOException e1) {
				}
				return;
			}
			if (msg.id == Message.BITFIELD){
				////////////Peer is sending us a bitfield. May or may not be complete. If incomplete, expect a have message from peer.
				bitfield = msg.bfield;
				if(RUBTClient.havefield == null)
					RUBTClient.havefield = new byte[bitfield.length];
			}
		}else{
			//peer contacted us.
			bitfield = new byte[RUBTClient.havefield.length];
		}
		
		if(bitfield == null){
			GUI.updateOutput("Bad bitfield from "+peerID+".");
			//GUI.updateOutput(Arrays.toString(bitfield));
			return;
		}
		
		BitSet bitfieldSet = reverseBitSet(fromByteArray(bitfield));
		BitSet haveSet = fromByteArray(RUBTClient.havefield);
		if(writeIndex == null)
			writeIndex = new boolean[(RUBTClient.torInfo.file_length/RUBTClient.torInfo.piece_length)+1];
		if(firstRequestForPiece == null){
			firstRequestForPiece = new boolean[bitfieldSet.length()];
			for(int setTrue = 0; setTrue < firstRequestForPiece.length; setTrue++)
				firstRequestForPiece[setTrue] = true;
		}
		/////////////////////////////////////Loop here until download complete. /////////////////////////////////////
		while(RUBTClient.bytesDLed < RUBTClient.torInfo.file_length){
			if(quit){
				try {
					dos.close();
					din.close();
					socket.close();
					//RUBTClient.peers.remove(this);
					return;
				} catch (IOException e1) {
				}
				return;
			}
			int remainingBytes = RUBTClient.torInfo.file_length - RUBTClient.bytesDLed;
			msg = new Message();
			if(choked){
				//Choked. Send interest.
				Message sendInterest = new Message(1,Message.INTERESTED);
				try {
			//		GUI.updateOutput("We are presently choked by peer "+peerID+". Sending interest message.");
					dos.write(sendInterest.sendInter());
				} catch (IOException e) {
			//		GUI.updateOutput("Could not send interest message to peer.");
				}
			}else{
				int pieceIndex;
				for( pieceIndex=0; pieceIndex<bitfieldSet.length(); pieceIndex++){
					if(!haveSet.get(pieceIndex))			//Check what we don't have
						if(bitfieldSet.get(pieceIndex))		//Check what peer says they have
							if(!writeIndex[pieceIndex])		//Checking to see if we're already writing a piece of an index.
								break;						//Found a piece we don't have that peer does. Request this piece.
				}
				
				int offset = 0;
				if(firstRequestForPiece[pieceIndex]	)
					offset = 0;
				else
					offset = Message.BLOCKLENGTH;
				
				Message sendRequest = new Message(13,Message.REQUEST);
				sendRequest.pieceIndex = pieceIndex;
				sendRequest.pieceOffset = offset;
				
				try {
			//		GUI.updateOutput("Requesting block from piece number "+pieceIndex);
					if(remainingBytes < Message.BLOCKLENGTH)
						dos.write(sendRequest.sendReq(remainingBytes));
					else
						dos.write(sendRequest.sendReq(Message.BLOCKLENGTH));
				} catch (IOException e) {
			//		GUI.updateOutput("Could not send request message to peer.");
				}
			}
			
			/*try {
				msg.receive(din);
			} catch (IOException e) {
				GUI.updateOutput("Could not recieve a good message.");
			}*/
				
			try {
				msg.receive(din, peerID, socket);
			} catch (IOException e1) {
				//GUI.updateOutput("Could not recieve message from "+peerID+".");
				continue;
			}

			
			if (msg.id == Message.HAVE){
				////////////Peer is sending us a have message. Adjust the bitfield as necessary.
				GUI.updateOutput("Recieved \"Have\" message. Peer has index "+msg.have);
				bitfield[msg.have] = 1;
			}
			
			if (msg.id == Message.CHOKE){
				////////////Peer is not sharing with us. Send interest message.
				if(!choked)
					GUI.updateOutput(peerID+" has choked us. Sending interest messages.");
				choked = true;
			}
			
			if (msg.id == Message.UNCHOKE){
				////////////Peer is sharing with us. Send request messages.
				if(choked)
					GUI.updateOutput(peerID+" has unchoked us. Sending request messages.");
				choked = false;
			}
			
			if(msg.id == Message.INTERESTED){
				/////////////Peer would like to DL from us.//////////////////
				GUI.updateOutput("Peer is interested in downloading from us.");
				Message unchokeMSG = new Message(1,Message.UNCHOKE);
				GUI.updateOutput("Unchoking peer"+peerID+".");
				try{
					dos.write(unchokeMSG.sendChokeStatus());
				}catch(IOException e){
					GUI.updateOutput("Could not unchoke peer.");
				}
			}
			
			if(msg.id == Message.NOT_INTERESTED){
				////////////Code this later. Peer would not like to DL from us.///////////////
				GUI.updateOutput("Peer is not interested in downloading from us.");
				Message chokeMSG = new Message(1,Message.CHOKE);
				GUI.updateOutput("choking peer"+peerID+".");
				try{
					dos.write(chokeMSG.sendChokeStatus());
				}catch(IOException e){
					GUI.updateOutput("Could not choke peer.");
				}
			}
			
			if(msg.id == Message.REQUEST){
				////////////Peer is requesting a piece of data.//////////////
				GUI.updateOutput("Peer is requesting data from us.");
				byte[] piece = new byte[msg.bLen];
				System.arraycopy(RUBTClient.FILEDATA[msg.pieceIndex], msg.pieceOffset, piece, 0, msg.bLen);
				Message sendPiece = new Message(9+msg.bLen,Message.PIECE);
				sendPiece.pieceIndex = msg.pieceIndex;
				sendPiece.pieceOffset = msg.pieceOffset;
				GUI.updateOutput("Sending block to peer"+peerID+".");
				try {
					dos.write(sendPiece.sendPiece(piece));
				} catch (IOException e) {
					GUI.updateOutput("Failed.");
				}
			}
			
			if(msg.id == Message.PIECE){
				////////////Peer is sending us data. 	Conveniently, our block size for this torrent is presently 1/2 the piece size. I found this out in debugger.
			//	GUI.updateOutput("Peer "+peerID+" is sending data to us from block "+msg.pieceIndex);
				
				writeIndex[msg.pieceIndex] = true;
				
				int copyPos=0;
				if(firstRequestForPiece[msg.pieceIndex] && (msg.pieceIndex == ((RUBTClient.torInfo.file_length/RUBTClient.torInfo.piece_length))))
					RUBTClient.recievedBytes[msg.pieceIndex] = new byte[remainingBytes];
				if(!firstRequestForPiece[msg.pieceIndex])
					copyPos=Message.BLOCKLENGTH;
				
				System.arraycopy(msg.block, 0, RUBTClient.recievedBytes[msg.pieceIndex], copyPos, msg.block.length);
								
				int offsetIndex = 0;
				if(msg.pieceOffset!=0){
					offsetIndex = msg.pieceOffset;
				}

				firstRequestForPiece[msg.pieceIndex] = !firstRequestForPiece[msg.pieceIndex];
				RUBTClient.bytesDLed += msg.block.length;
				GUI.updateBar();
			
				//Because each block is 1/2 a piece and we have 2 blocks = 1 piece, we check hash before adding to file data.
				if(offsetIndex!=0){
					byte[] piece = RUBTClient.recievedBytes[msg.pieceIndex].clone();
					MessageDigest md = null;
					try {
						md = MessageDigest.getInstance("SHA-1");
					} catch (NoSuchAlgorithmException e) {
					}
					byte[] hashOfReceived = md.digest(piece);
					byte[] checkAgainst = RUBTClient.torInfo.piece_hashes[msg.pieceIndex].array();
					boolean hashFail = false;
					for(int findDiff = 0; findDiff<hashOfReceived.length; findDiff++){
						int checkTest = (int) hashOfReceived[findDiff];
						int checkControl = (int) checkAgainst[findDiff];
						if(checkTest != checkControl)
							hashFail = true;
					}
					if(hashFail){
						//Bad data. Throw it out.
						GUI.updateOutput("Failed hash for index "+msg.pieceIndex+" from "+peerID+". Throwing it out.");
						RUBTClient.bytesDLed -= piece.length;
						GUI.updateBar();
					}else{
						//Good data. Keep it.
					//	GUI.updateOutput("Good piece. Keeping it.");
						for(int copyIndex=0; copyIndex<piece.length; copyIndex++){
							RUBTClient.FILEDATA[msg.pieceIndex*RUBTClient.torInfo.piece_length+copyIndex] = piece[copyIndex];
						}
						haveSet.flip(msg.pieceIndex);
						RUBTClient.havefield = toByteArray(haveSet);
					//	GUI.updateOutput("Have "+RUBTClient.bytesDLed+" bytes out of "+RUBTClient.torInfo.file_length+" bytes.");
					//	GUI.updateOutput(RUBTClient.torInfo.file_length - RUBTClient.bytesDLed+" bytes remain.");
					}
					//Freeing up space in memory.
					piece = null;
					md = null;
					hashOfReceived = null;
				}
				writeIndex[msg.pieceIndex] = false;
			}
		
		}
		return;
	}
	
	//Taken from http://www.java2s.com/Code/Java/Language-Basics/ConvertingBetweenaBitSetandaByteArray.htm
	/**
	 * @param bytes bitfield to convert to BitSet
	 * @return BitSet of bitfield.
	 */
	static BitSet fromByteArray(byte[] bytes){
		BitSet bits = new BitSet();
		for(int i = 0; i < bytes.length * 8; i++){
			if((bytes[bytes.length - i/8 -1] & (1 << (i%8))) > 0){
				bits.set(i);
			}
		}
		return bits;
	}
	
	/**
	 * @param BitSet to be reversed. BitSet from bitfield needs to be reversed.
	 * @return reversed BitSet.
	 */
	static BitSet reverseBitSet(BitSet bits){
		BitSet rev = new BitSet();
		for(int i = 0; i < bits.length(); i++){
			if(bits.get(bits.length()-1-i))
				rev.set(i);
		}
		return rev;
	}
	
	//Taken from https://stackoverflow.com/questions/6197411/converting-from-bitset-to-byte-array
	/**
	 * @param bits BitSet to convert to bitfield byte[]
	 * @return byte[] of bitfield from BitSet
	 */
	public static byte[] toByteArray(BitSet bits) {
		    byte[] bytes = new byte[bits.length()/8+1];
		    for (int i=0; i<bits.length(); i++) {
		        if (bits.get(i)) {
		            bytes[bytes.length-i/8-1] |= 1<<(i%8);
		        }
		    }
		    return bytes;
		}
	
	/**
	 * 
	 */
	public static void quit(){
		quit = true;
		return;
	}
	
	/**
	 * @return boolean about whether the peers should be shut down or not.
	 */
	public static boolean quitStatus(){
		return quit;
	}
	
	/**
	 * @param handshake the peer sent us. Used when peer contacts us.
	 */
	public void setShake(Handshake handshake){
		peerShake = handshake;
		return;
	}
	
	/**
	 * Sets the boolean flag to specify if we contacted the peer or if the peer contacted us.
	 */
	public void notDLingFrom(){
		DLingFrom = false;
		return;
	}
	
}
