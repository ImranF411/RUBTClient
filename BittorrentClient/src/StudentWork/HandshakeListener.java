package StudentWork;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Class to listen for peers contacting us.
 * @author Imran
 * 
 */
public class HandshakeListener implements Runnable {
	boolean quit = false;
	ServerSocket ss;
	
	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public void run(){
		//Creates a serversocket to receive handshakes.
		Socket handListener;
		DataOutputStream dos;
		DataInputStream dis;
		
		try {
			ss = new ServerSocket(6881);
		} catch (IOException e2) {
			e2.printStackTrace();
			GUI.updateOutput("Cannot create ServerSocket. Ignoring incoming handshakes.");
			return;
		}
		
		//Loop reading from the ServerSocket until we quit.
		while(!quit){
			//Create Socket, DataInputStrean, DataOutputStream
			try{
				 handListener = ss.accept();
				 dis = new DataInputStream(handListener.getInputStream());
				 dos = new DataOutputStream(handListener.getOutputStream());
			} catch (IOException e1) {
				GUI.updateOutput("Closing socket handshake listener socket.");
				break;
			}
			
			//Take in a handshake.
			byte[] incomingShake = new byte[68];
			try {
				//Read the handshake
				dis.readFully(incomingShake);
				Handshake received = Handshake.parse(incomingShake);
				GUI.updateOutput("Peer is trying to contact us.");
				//Create peer from 
				String peerID = new String(received.peer_id,"UTF-8");
				
				/* if(!handListener.getInetAddress().toString().equals("128.6.171.130") && !handListener.getInetAddress().toString().equals("128.6.171.131")){
					GUI.updateOutput(peerID + " is not a valid peer to upload to.");
					//RUBTClient.peers.remove(this);
					try {
						handListener.close();
						dos.close();
						dis.close();
					} catch (IOException e) {
					}
					continue;
				} */
				
				if((RUBTClient.peerNames.contains(peerID))){
					GUI.updateOutput("Already connected to this peer. Closing this connection.");
					try {
						handListener.close();
						dos.close();
						dis.close();
					} catch (IOException e) {
					}
					continue;
				}
				
				Peer newPeer = null;
				if(received.info_hash.compareTo(RUBTClient.torInfo.info_hash) == 0){
					//Contacting peer has sent correct info hash and is not on contact list.
					newPeer = new Peer(peerID,handListener,received,dos,dis);
				}else{
					//Bad info_hash from peer or we already have peer on contact list.
					GUI.updateOutput("No good handshake. Ceasing contact with this peer.");
					try {
						handListener.close();
						dos.close();
						dis.close();
					} catch (IOException e) {
					}
					continue;
				}
				
				//Preparing peer before running thread.
				newPeer.setShake(received);
				newPeer.notDLingFrom();
				
				
				//Sending our handshake/bitfield to peer.
				Handshake shake = new Handshake(RUBTClient.torInfo.info_hash,"TwentyByteStringHere");
				shake.Send(dos);
				dos.write(RUBTClient.havefield);
				
				//Add peer to contact list.
				RUBTClient.peers.add(newPeer);
				RUBTClient.peerNames.add(peerID);
				
				
				newPeer.run();
				RUBTClient.update();
				
			} catch (IOException e) {
				GUI.updateOutput("Could not read handshake. Ceasing contact with this peer.");
				try {
					handListener.close();
					dos.close();
					dis.close();
				} catch (IOException e1) {
				}
			}
			
		}
		
		//Quit message set. Close ServerSocket.
		try {
			ss.close();
		} catch (IOException e) {
			// Program's ending anyways; do nothing.
		}
		
		return;
	}

	/**
	 * quits the HandshakeListener
	 */
	public void quit(){
		quit = true;
		try {
			ss.close();
		} catch (IOException e) {
		}
		return;
	}
	
}
