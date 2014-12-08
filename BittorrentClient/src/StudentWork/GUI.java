package StudentWork;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;

import javax.swing.*;

/**
 * Graphical user interface class of the RUBTClient program.
 * @author Imran
 *
 */
public class GUI extends JFrame{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	static JTextArea output;
	static JProgressBar progressBar;
	
	/**
	 * Creates and adds elements for the GUI window.
	 */
	public GUI(){
		super("RUBT Client");
		setLayout(new GridLayout(0,1,5,5));
		
		progressBar = new JProgressBar(0,RUBTClient.torInfo.file_length);
		progressBar.setValue(RUBTClient.bytesDLed);
		progressBar.setStringPainted(true);
		
		output = new JTextArea(10,64);
		output.setEditable(false);
		
		JButton quitButton = new JButton("Quit");
		quitButton.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				RUBTClient.quit();	
			}
		});
		//JPanel panel = new JPanel();
		
		add(progressBar);
		add(new JScrollPane(output), BorderLayout.CENTER);
		add(quitButton);
		
	}
	
	/**
	 * @param s string to update text area with
	 */
	synchronized static public void updateOutput(String s){
		output.append(s+"\n"); return;
	}
	
	/**
	 * Updates the progress bar.
	 */
	synchronized static public void updateBar(){
		progressBar.setValue(RUBTClient.bytesDLed); return;
	}
	
	/**
	 * Starts the GUI.
	 */
	public static void startGUI(){
		GUI gui = new GUI();
		gui.pack();
		gui.setVisible(true);
		gui.setLocationRelativeTo(null);
		gui.setResizable(true);
		gui.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}
	
	//Taken from here:
	//http://stackoverflow.com/questions/4524786/how-to-close-a-frame-when-button-clicks
	/**
	 * Closes the GUI window.
	 */
	public static void close() {
	    WindowEvent winClosingEvent = new WindowEvent( this, WindowEvent.WINDOW_CLOSING );
	    Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent( winClosingEvent );
	}
	
}