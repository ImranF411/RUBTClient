package StudentWork;

import GivenTools.*;

import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;



/**
 * Main class of the RUBTClient program.
 * @author Imran
 * @author Joe Kennedy
 *
 */
public class RUBTClient implements Runnable{
	
	File inFile;
	ByteBuffer inData;
	
	//static boolean choked = true;
		
	static byte[] havefield = null;
	
	final static int BLOCKLENGTH = 16384;
	
	static byte[] FILEDATA;
	static byte[][] recievedBytes;
	
	static TorrentInfo torInfo;
	static ArrayList<Peer> peers;
	static ArrayList<String> peerNames;
	
	static int bytesDLed = 0;
	static int bytesULed = 0;
	
	static int openPort = 6881;
	static HandshakeListener hl;
	static boolean quit = false;
	/**
	 * @param args The arguments given when running the program. The 1st argument specifies the torrent file to read. The 2nd specifies the file to save to.
	 */
	@SuppressWarnings("resource")
	public static void main(String[] args) {
		if(args.length<2){
			System.out.println("Please specify the torrent file and file name of download using the following format: \"torrentFile.torrent\" \"downloadedFileName.extension\".");
			return;
		}
		String torrentPath = args[0];
		String fileName = args[1];
		
		
		
		Path path = Paths.get(torrentPath);
		byte[] torrentByteArray;
		try {
			torrentByteArray = Files.readAllBytes(path);
		} catch (IOException e) {
			System.out.println("Could not read torrent file. Exiting program.");
			return;
		}
		
		//////////////////////////////////////////////////////Parses info from torrent file.
		torInfo = getTorrentInfo(torrentByteArray);
		
		FILEDATA = new byte[torInfo.file_length];
		recievedBytes = new byte[(torInfo.file_length/torInfo.piece_length)+1][torInfo.piece_length];
		
		////////////////////////////////////////////////////////Loads preexisting data.
		File logFile = new File("Log.txt");
		if(logFile.exists()){
			Serializer tempSer = new Serializer("",null,null,null,0,0);
			try {
				tempSer = tempSer.read();
				if(tempSer.fileName!=null && tempSer.fileName.equals(fileName)){
					FILEDATA = tempSer.FILEDATA;
					recievedBytes = tempSer.recievedBytes;
					havefield = tempSer.havefield;
					bytesDLed = tempSer.bytesDLed;
					bytesULed = tempSer.bytesULed;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		GUI.startGUI();
		
		////////////////////////////////////////////////////////////////////////////////////////
		//The commented block below does not work. Try to fix this.
		////////////////////////////////////////////////////////////////////////////////////////
		//Zeroes out last piece. Ensures complete download when resuming previous download.
/*		recievedBytes[torInfo.file_length/torInfo.piece_length] = new byte[torInfo.file_length%torInfo.piece_length];
		if(havefield!=null){
			BitSet tempSet = Peer.fromByteArray(havefield);
			for(int unset = tempSet.length()-1; ;unset--){
				if(tempSet.get(unset)){
					tempSet.flip(unset);
					break;
				}
			}
			havefield = Peer.toByteArray(tempSet);
			if(bytesDLed==torInfo.file_length)
				bytesDLed -= torInfo.file_length%torInfo.piece_length;
		}*/
		
		//////////////////////////////////////////////////////Generates HTTP GET request url.
		String requestURL = getURLString(torInfo);
		
		URL obj;
		try {
			obj = new URL(requestURL+"&event=started");
		} catch (MalformedURLException e) {
			GUI.updateOutput("Could not generate get request. Exiting program.");
			return;
		}
		
		///////////////////////////////////////////////////////Opens connection to tracker.
		HttpURLConnection con;
		try {
			 con = (HttpURLConnection) obj.openConnection();
		} catch (IOException e) {
			GUI.updateOutput("Could not open connection. Exiting.");
			return;
		}
		
		////////////////////////////////////////////////////////Sends GET request to tracker.
		GUI.updateOutput("Sending GET request to :" + obj.toString());
		try {
			con.setRequestMethod("GET");
		} catch (ProtocolException e) {
			GUI.updateOutput("Could not set request method. Exiting.");
			return;
		}
		
		//////////////////////////////////////////////////////////Receives response from tracker.
		int responseCode;
		try {
			responseCode= con.getResponseCode();
		} catch (IOException e) {
			GUI.updateOutput("Could not get response code. Exiting.");
			return;
		}
		GUI.updateOutput("Response Code :" + responseCode);
		
		BufferedReader in;
		try {
			in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		} catch (IOException e1) {
			GUI.updateOutput("Could not get message from tracker. Ending program");
			return;
		}
		String input;
		StringBuffer response = new StringBuffer();
		
		try {
			while((input = in.readLine()) != null){
				response.append(input);
			}
		} catch (IOException e1) {
			GUI.updateOutput("Could not get message from server. Ending program");
			return;
		}
		
		byte[] responseBytes = response.toString().getBytes();
		
		//////////////////////////////////////////Decodes data from tracker.
		HashMap responseDictionary=null;
		try {
			responseDictionary = (HashMap) Bencoder2.decode(responseBytes);
		} catch (BencodingException e) {
			GUI.updateOutput("Could not decode response from dictionaray. Exiting program.");
			return;
		}
		
		///////////////////////////////////////////Finds peer list from tracker
		Iterator iter = responseDictionary.keySet().iterator();
		Object key = null;
		Object val = null;
		
		while(iter.hasNext() && (key = iter.next()) != null){
			val = responseDictionary.get(key);
			if(val instanceof ArrayList)
				break;
		}
		ArrayList peerList = (ArrayList) val;
		
//		byte[] incomingShake1 = new byte[68];
//		byte[] incomingShake2 = new byte[68];

		
		/*try {
			Socket s1 = new Socket("128.6.171.131",24399);
			Socket s2 = new Socket("128.6.171.130",4237);
			DataInputStream dis1 = new DataInputStream(s1.getInputStream());
			DataOutputStream dos1 = new DataOutputStream(s1.getOutputStream());
			DataInputStream dis2 = new DataInputStream(s2.getInputStream());
			DataOutputStream dos2 = new DataOutputStream(s2.getOutputStream());
			Handshake shake1 = new Handshake(RUBTClient.torInfo.info_hash,"TwentyByteStringHere");
			Handshake shake2 = new Handshake(RUBTClient.torInfo.info_hash,"TwentyByteStringHere");
			shake1.Send(dos1);
			shake2.Send(dos2);
			GUI.updateOutput(s1.isConnected());
			GUI.updateOutput(s2.isClosed());
			dis1.readFully(incomingShake1);
			GUI.updateOutput(s2.isConnected());
			GUI.updateOutput(s2.isClosed());
			dis2.readFully(incomingShake2);

		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}*/
		
		///////////////////////////////////////////Creates list of peers.
		peers = new ArrayList<Peer>();
		peerNames = new ArrayList<String>();
		for(Iterator getPeerData = peerList.iterator(); getPeerData.hasNext(); ){
			if(quit){
				Peer.quit();
				break;
			}
			String seedInfoString = captureText(getPeerData.next());
			
			int portIndex=0;
			int ipIndex=0;
			int idIndex=0;
		
			//GUI.updateOutput(seedInfoString);
			String[] seedInfo = seedInfoString.split("\n");
			for(int i=0;i<seedInfo.length;i++){
				//GUI.updateOutput(i+" : "+seedInfo[i]);
				if(seedInfo[i].contains("String: ip"))
					ipIndex = i+1;
				if(seedInfo[i].contains("String: port"))
					portIndex = i+1;
				if(seedInfo[i].contains("String: peer id"))
					idIndex = i+1;
			}
			
			int portNum = Integer.parseInt((seedInfo[portIndex].split(":"))[1].trim());
			String ipAddress = (seedInfo[ipIndex].split(":"))[1].trim();
			String pID = (seedInfo[idIndex].split(":"))[1].trim();
			
			/* if(!ipAddress.equals("128.6.171.130") && !ipAddress.equals("128.6.171.131")){
				GUI.updateOutput(pID + " is not a valid peer to download from.");
				//RUBTClient.peers.remove(this);
				continue;
			} */
			
			//////////////////////////////////////////////Start peer threads
			Peer newPeer = new Peer(portNum,ipAddress,pID);
			if(!peerNames.contains(pID)){
				peers.add(newPeer);
				peerNames.add(pID);
				(new Thread(newPeer)).start();
			}
		}
		
/* 		for(Iterator<Peer> startPeers = peers.iterator(); startPeers.hasNext();){
			(new Thread(startPeers.next())).start();
		} */
		
		if(!quit){
			hl = new HandshakeListener();
			Thread hlThread = new Thread(hl);
			hlThread.start();
		}
		
		GUI.updateOutput("Click the \"Quit\" button to close the program.");
//		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
//		String line = "";
		
		boolean once = true;
		
		while(!quit){
			
			if(bytesDLed == torInfo.file_length && once){
				once = !once;
				GUI.updateOutput("File download complete. Seeding file.");
				try {
					writeFile(fileName,((bytesDLed - torInfo.file_length == 0)));
				} catch (IOException e) {
					hl.quit();
					Peer.quit();
					close();
					GUI.updateOutput("Couldn't write to file. Sucks, don't it? Exiting program.");
					return;
				}
			}
			
//			try {
//				line = br.readLine();
//			} catch (IOException e) {
//			}
//			
//			if(line.equals(""))
//				GUI.updateOutput("Downloaded "+bytesDLed+" of "+torInfo.file_length+" bytes.");
//			
//			if(line.equalsIgnoreCase("Quit")){
//				GUI.updateOutput("Quitting the program");
//				quit = true;
//			}
			
		}
		
		HandshakeListener.quit();
		Peer.quit();
		close();
		
		if(bytesDLed == torInfo.file_length){
			GUI.updateOutput("Successfully downloaded data. Writing to file now.");
			complete();
		}
		//writing to file.
		try {
			writeFile(fileName,((bytesDLed - torInfo.file_length == 0)));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("Couldn't write to file. Sucks, don't it? Exiting program.");
			return;
		}
		GUI.updateOutput("Successfully wrote file to "+fileName);
//		Thread.sleep(500);
//		GUI.close();
		GUI.updateOutput("It is now safe to close this window");
		return;
	}
	
	/**
	 * @param toFileName The name of the file to write to.
	 * @param complete Whether or not we have downloaded all bytes of the file.
	 * @throws IOException  If for some reason the file cannot be written, IOException is thrown.
	 */
	@SuppressWarnings("resource")
	static void writeFile(String toFileName, boolean complete) throws IOException{
		ByteBuffer writeBuffer = ByteBuffer.wrap(FILEDATA);
		//File file = new File(toFileName);
		
		//opens channel to allow ByteBuffer to write to channel.
		FileChannel channel = null;
		if(complete){
			channel = new FileOutputStream(toFileName).getChannel();
			File delFile = new File(toFileName+".incomplete");
			delFile.delete();
		}
		else{
			channel = new FileOutputStream(toFileName+".incomplete").getChannel();
			FileChannel channelFalse = new FileOutputStream(toFileName).getChannel();
			channelFalse.write(ByteBuffer.wrap(new byte[0]));
		}
		channel.write(writeBuffer);
		channel.close();
		
		Serializer newSer = new Serializer(toFileName, FILEDATA, recievedBytes, havefield, bytesDLed, bytesULed);
		newSer.write();
		
		return;
		
	}
	
	//This method makes a TorrentInfo class of the torrent file by analyzing the byte array of the torrent file.
	//String capture text taken from StackOverflow.
	/**
	 * @param torrentByteArray A byte array of the torrent file. Gets scanned by the TorrentInfo class.
	 * @return TorrentInfo class object made from the torrentByteArray passed int.
	 */
	public static TorrentInfo getTorrentInfo(byte[] torrentByteArray){
		
		TorrentInfo torInfo = null;
		try {
			torInfo= new TorrentInfo(torrentByteArray);
		} catch (BencodingException e) {
			GUI.updateOutput("Could not decode torrent file. Exiting program");
			System.exit(1);
		}
		return torInfo;
	}
	
	//This method takes in a byte[] and returns a URLencoded string.
	//Taken from here: http://www.java2s.com/Code/Android/Network/ConvertabytearraytoaURLencodedstring.htm
	/**
	 * @param in Byte array of the infohash to convert to a URL safe string.
	 * @return A URL safe string made from the bytes of the infohash
	 */
	public static String byteArrayToURLString(byte in[]) {
	    byte ch = 0x00;
	    int i = 0;
	    if (in == null || in.length <= 0)
	      return null;

	    String hexaChars[] = { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
	        "A", "B", "C", "D", "E", "F" };
	    StringBuffer ret = new StringBuffer(in.length * 2);

	    while (i < in.length) {
	      // First check to see if we need ASCII or HEX
	      if ((in[i] >= '0' && in[i] <= '9')
	          || (in[i] >= 'a' && in[i] <= 'z')
	          || (in[i] >= 'A' && in[i] <= 'Z') || in[i] == '$'
	          || in[i] == '-' || in[i] == '_' || in[i] == '.'
	          || in[i] == '!') {
	        ret.append((char) in[i]);
	        i++;
	      } else {
	        ret.append('%');
	        ch = (byte) (in[i] & 0xF0); // Strip off high nibble
	        ch = (byte) (ch >>> 4); // shift the bits down
	        ch = (byte) (ch & 0x0F); // must do this is high order bit is
	        // on!
	        ret.append(hexaChars[(int) ch]); // convert the nibble to a
	        // String Character
	        ch = (byte) (in[i] & 0x0F); // Strip off low nibble
	        ret.append(hexaChars[(int) ch]); // convert the nibble to a
	        // String Character
	        i++;
	      }
	    }

	    return new String(ret);
	  }
	
	//////////////////////////////////Sends close message to tracker.
	/**
	 * Sends stopped message to the tracker.
	 */
	public static void close(){
		URL obj;
		try {
			obj = new URL(stoppedURLString(torInfo));
		} catch (MalformedURLException e) {
			GUI.updateOutput("Could not generate stop request.");
			return;
		}
		HttpURLConnection con;
		try {
			 con = (HttpURLConnection) obj.openConnection();
		} catch (IOException e) {
			GUI.updateOutput("Could not open connection.");
			return;
		}
		
		////////////////////////////////////////////////////////Sends POST message to tracker.
		GUI.updateOutput("Sending stopped message to :" + obj.toString());
		try {
			con.setRequestMethod("POST");
		} catch (ProtocolException e) {
			GUI.updateOutput("Could not set request method. Exiting.");
			return;
		}
		
		return;
	}
	
	/**
	 * updates the tracker about our progress.
	 */
	public static void update(){
		URL obj;
		try {
			obj = new URL(getURLString(torInfo));
		} catch (MalformedURLException e) {
			GUI.updateOutput("Could not generate tracker URL.");
			return;
		}
		HttpURLConnection con;
		try {
			 con = (HttpURLConnection) obj.openConnection();
		} catch (IOException e) {
			GUI.updateOutput("Could not open connection.");
			return;
		}
		
		////////////////////////////////////////////////////////Sends POST message to tracker.
		GUI.updateOutput("Sending update message to :" + obj.toString());
		try {
			con.setRequestMethod("POST");
		} catch (ProtocolException e) {
			GUI.updateOutput("Could not set request method.");
			return;
		}
		
		return;
	}

	//////////////////////////////////Sends complete message to tracker.
	/**
	 * Sends complete message to the tracker. 
	 */
	public static void complete(){
		URL obj;
		try {
			obj = new URL(completedURLString(torInfo));
		} catch (MalformedURLException e) {
			GUI.updateOutput("Could not generate stop request.");
			return;
		}
		HttpURLConnection con;
		try {
			 con = (HttpURLConnection) obj.openConnection();
		} catch (IOException e) {
			GUI.updateOutput("Could not open connection.");
			return;
		}
		
		GUI.updateOutput("Sending completed message to :" + obj.toString());
		try {
			con.setRequestMethod("POST");
		} catch (ProtocolException e) {
			GUI.updateOutput("Could not set request method. Exiting.");
			return;
		}
		
		return;
	}
	
	//This method captures text to the console and returns it to a string.
	/**
	 * @param o Object with print statements to capture from console. Used to get peer data from ToolKit print statements.	
	 * @return A string of captured text intercepted from console.
	 */
	public static String captureText(Object o){
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos);
		PrintStream old = System.out;
		System.setOut(ps);
		ToolKit.print(o);
		System.out.flush();
		System.setOut(old);
		
		return baos.toString();
	}
	
	//This method generates the URL for the HTTP GET request.
	/**
	 * @param torInfo TorrentInfo class object to extract announce url and infohash from.
	 * @return A string of the get request
	 */
	public static String getURLString(TorrentInfo torInfo){
		StringBuilder getRequest = new StringBuilder(torInfo.announce_url.toString());
		getRequest.append("?info_hash=").append(byteArrayToURLString(torInfo.info_hash.array()))
					.append("&peer_id=TwentyByteStringHere")
					.append("&port="+openPort)
					.append("&uploaded="+bytesULed)
					.append("&downloaded="+bytesDLed)
					.append("&left=").append(torInfo.file_length);
		openPort++;
		return getRequest.toString();
	}
	
	/**
	 * @param torInfo TorrentInfo class object to extract announce url and infohash from.
	 * @return A string of the stopped message
	 */
	//Generates stopped message url string
	public static String stoppedURLString(TorrentInfo torInfo){
		StringBuilder getRequest = new StringBuilder(torInfo.announce_url.toString());
		getRequest.append("?info_hash=").append(byteArrayToURLString(torInfo.info_hash.array()))
					.append("&peer_id=TwentyByteStringHere")
					.append("&port=6881")
					.append("&uploaded="+bytesULed)
					.append("&downloaded="+bytesDLed)
					.append("&left=").append(torInfo.file_length)
					.append("&event=stopped");
		
		return getRequest.toString();
	}
	
	/**
	 * @param torInfo TorrentInfo class object to extract announce url and infohash from.
	 * @return A string of the completed message
	 */
	//Generates completed message url string
	public static String completedURLString(TorrentInfo torInfo){
		StringBuilder getRequest = new StringBuilder(torInfo.announce_url.toString());
		getRequest.append("?info_hash=").append(byteArrayToURLString(torInfo.info_hash.array()))
					.append("&peer_id=TwentyByteStringHere")
					.append("&port=6881")
					.append("&uploaded="+bytesULed)
					.append("&downloaded="+bytesDLed)
					.append("&left=").append(torInfo.file_length)
					.append("&event=completed");
		
		return getRequest.toString();
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}
	
	/**
	 * Changes the quit status of the RUBT client.
	 */
	public static void quit(){
		quit = true;
		return;
	}
	
}

/**
 * Class to write data of incomplete downloads to disk.
 * @author Imran
 * @author Joe Kennedy
 * 
 */
class Serializer implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	String fileName;
	byte[] FILEDATA;
	byte[][] recievedBytes;
	byte[] havefield;
	int bytesDLed;
	int bytesULed;
	
	/**
	 * @param name name of the file to save to
	 * @param filedata	byte[] of file to write
	 * @param bytes byte[][] of file data. Used for record keeping.
	 * @param have byte[] of our bitfield
	 * @param DLed number of bytes downloaded
	 * @param ULed number of bytes uploaded
	 */
	public Serializer(String name, byte[] filedata, byte[][] bytes, byte[] have, int DLed, int ULed){
		fileName = name;
		FILEDATA = filedata;
		recievedBytes = bytes;
		havefield = have;
		bytesDLed = DLed;
		bytesULed = ULed;
	}
	
	/**
	 * Writes progress so far to file to resume later.
	 */
	public void write(){
		File log = new File("log.txt");
		if(log.exists())
			log.delete();
		try {
			log.createNewFile();
			FileOutputStream fos = new FileOutputStream("log.txt",false);
			ObjectOutputStream out = new ObjectOutputStream(fos);
			out.writeObject(this);
			out.close();
			GUI.updateOutput("Wrote persisting data.");
		} catch (IOException e) {
		}
		return;
	}
	
	/**
	 * @return Serializer object containing data to use to resume.
	 * @throws IOException if file cannot be read, throws this exception.
	 */
	public Serializer read() throws IOException{
		Serializer temp = null;
		FileInputStream fis = null;
		ObjectInputStream in = null;
		
			try {
				fis = new FileInputStream("log.txt");
				in = new ObjectInputStream(fis);
				temp = (Serializer) in.readObject();
				in.close();
			} catch (ClassNotFoundException e) {
			}
			
		return temp;
	}

	
}