package serial;

//import gnu.io.CommPort;
//import gnu.io.CommPortIdentifier;
//import gnu.io.SerialPort;

import jssc.SerialPort;
import jssc.SerialPortException;
import jssc.SerialPortList;


import serial.channel.ChannelManager;
import serial.listeners.PollBothChannels;
import serial.listeners.PollCommandChannel;
import serial.listeners.PollDataChannel;
import serial.listeners.SerialReader;
import serial.packet.SerialPortPacket;

import java.util.List;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import asg.cliche.Command;
import asg.cliche.Param;
import asg.cliche.ShellFactory;

public class SerialComm {
	
	private BlockingQueue<Byte>[] mChannelBuffers = new LinkedBlockingQueue[MAX_CHANNEL_NUMBER + 1];
	
	//InputStream in = null;
	//OutputStream out = null;
	
	SerialPort serialPort = null;
	
	private SerialReader sr=null;
	private Thread srThread=null;
	
	public static final int PACKET_SIZE = 512;
    
    public static final int COMMAND_CHANNEL = 1;
    public static final int DATA_CHANNEL = 2;
    public static final int MAX_CHANNEL_NUMBER = 2;	
    
    private static final String APP_HOME = System.getProperty("user.dir");
    
    final static String LIST_CMD 	= "LIST /data/*";
    final static String STOR_CMD 	= "STOR /Users/mbrooks/Downloads/test10k.txt /data/test10k.txt";
    final static String RETR_CMD 	= "RETR /data/test10k.txt /Users/mbrooks/temp/test10k.txt";
    final static String MVFL_CMD 	= "RETR /data/test10k.txt /data/test10kb.txt";
    final static String GET_CMD		= "GET /Users/mbrooks/temp/test10k.txt";
    final static String DELE_CMD 	= "DELE /data/test10k.txt";
    final static String MLST_CMD    = "MLST /device/restart";
    final static String MKD_CMD 	= "MKD /data/test_folder";
    final static String RMD_CMD 	= "RMD /data/test_folder";
    final static String SFRT_CMD 	= "SFRT /data/test10k.txt";
    final static String SETTINGS_CMD = "RETR /device/firmware /Users/mbrooks/temp/settings.txt";
    
    
    private ChannelManager channelMgr = null;

	public SerialComm() {
		super();
		try {
			// Mac load jni library
			//System.load("/home/mbrooks/development/projects/SimpleSerialConnector/lib/libjSSC-2.8_x86_64.jnilib");		
			
			//cleanUpLockFiles();
			
			// Mac Bluetooth Files
			//connect("/dev/cu.CYBERGATE-SerialPortSer");
			//connect("/dev/cu.BLUSTOR-SerialPortServe");
			//connect("/dev/cu.CYBERGATE-SerialPortSer");
			//connect("/dev/cu.usbmodem14241");
			
			// Windows
			connect("COM16");
		} catch (Exception e) {
			e.printStackTrace();
			exit();
		}
	}

	private void exitApplication() {
		if (sr!=null) {
			sr.terminate();
		}
		try {
			if (serialPort !=null && serialPort.isOpened()) {
				serialPort.closePort();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Exiting application");
		System.exit(0);
	}
	
    @Command // Help
    public void examples() {
    	System.out.println("");
    	System.out.println("    LIST: Files and Directories");
    	System.out.println("    LIST /data/*");
    	System.out.println("");
    	System.out.println("    STOR: Copy files to card");
        System.out.println("    STOR " + APP_HOME + "/test_files/test10k.txt /data/test10k.txt");
        System.out.println("    STOR " + APP_HOME + "/test_files/test100k.txt /data/test100k.txt");
        System.out.println("    STOR " + APP_HOME + "/test_files/test500k.txt /data/test500k.txt");
        System.out.println("");
        System.out.println("    RETR: Download a file");
        System.out.println("    RETR /data/test10k.txt " + APP_HOME + "/test10k.txt");
        System.out.println("    GET " + APP_HOME + "/test10k.txt");
        System.out.println("");
        System.out.println("    DELE: Delete File");
        System.out.println("    DELE /data/test10k.txt");
        System.out.println("");
        System.out.println("    MKD: Make a directory");
        System.out.println("    MKD /data/test_folder");        
        System.out.println("");
        System.out.println("    RMD Delete Folder");
        System.out.println("    RMD /data/test_folder");
        System.out.println("");
        System.out.println("    SFRT Finalize after uploading file to card.  Already included in STOR");
        System.out.println("    SFRT /data/test10k.txt");
        System.out.println("");
        System.out.println("    Get the current version of the card");
        System.out.println("    RETR /device/firmware " + APP_HOME + "/settings.txt");
 
    }

    
    @Command(description="Exit the application", abbrev="")
    public void exit() {
    	exitApplication();
    }

    @Command(description="List the files and directories.\n\n" + LIST_CMD, abbrev="")
    public void LIST( 
    		@Param(name="Path", description="Full path to the folder you want to list the contents.") String str) {
    	try {
    		SerialPortPacket packet = new SerialPortPacket(sendCommand("LIST", str), COMMAND_CHANNEL);
    		serialPort.writeBytes(packet.getBytes());
			
			readBothChannels();
		} catch (Exception e) {
			e.printStackTrace();
		}
        //return "LIST sent";
    }

    @Command(description="Delete a file.  See RMD for folders. \n\n" + DELE_CMD, abbrev="")
    public void DELE(@Param(name="Path", description="Name of the file to delete including full path.") String str) {
    	try {
    		SerialPortPacket packet = new SerialPortPacket(sendCommand("DELE", str), COMMAND_CHANNEL);
    		serialPort.writeBytes(packet.getBytes());
			
			readBothChannels();
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    
    @Command(description="Special Files \n\n" + MLST_CMD, abbrev="")
    public void MLST(@Param(name="Path", description="Name of the file for special command.") String str) {
    	try {
    		SerialPortPacket packet = new SerialPortPacket(sendCommand("MLST", str), COMMAND_CHANNEL);
    		serialPort.writeBytes(packet.getBytes());
			
			readBothChannels();
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    
    
    // MKD /data/test_folder
    @Command(description="Create a folder.\n\n" + MKD_CMD, abbrev="")
    public void MKD(@Param(name="Path", description="Name of the folder including the full path.") String str) {
    	try {
    		SerialPortPacket packet = new SerialPortPacket(sendCommand("MKD", str), COMMAND_CHANNEL);
    		serialPort.writeBytes(packet.getBytes());
			
			readBothChannels();
		} catch (Exception e) {
			e.printStackTrace();
		}
    }

    @Command(description="Delete a directory.  See \"DELE\" for deleting a file.\n\n" + RMD_CMD, abbrev="")
    public void RMD(
    		@Param(name="Path", description="Name of the folder including the full path.") String str) {
    	try {
    		SerialPortPacket packet = new SerialPortPacket(sendCommand("RMD", str), COMMAND_CHANNEL);
    		serialPort.writeBytes(packet.getBytes());
			
			readBothChannels();
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    @Command(description="Rename a file.\n\n" + MVFL_CMD, abbrev="")
    public void MVFL(
    		@Param(name="Source", description="Remote name of the file to be changed.") String source, 
    		@Param(name="Target", description="New name for that file.") String target) {
    	try {
    		SerialPortPacket packet = new SerialPortPacket(sendCommand("RNFR", source), COMMAND_CHANNEL);
    		serialPort.writeBytes(packet.getBytes());
			
    		readCommandChannel();
    		
    		packet = new SerialPortPacket(sendCommand("RNTO", target), COMMAND_CHANNEL);
    		serialPort.writeBytes(packet.getBytes());
			
    		readCommandChannel();
    		
		} catch (Exception e) {
			e.printStackTrace();
		}    	
    }
    
    @Command(description="Download a file.\n\n" + RETR_CMD, abbrev="")
    public void RETR(
    		@Param(name="Source", description="Local name of the file to upload, including the full path.") String source, 
    		@Param(name="Target", description="Name of the file, including full path on the card.") String target) {
    	try {
    		SerialPortPacket packet = new SerialPortPacket(sendCommand("RETR", source), COMMAND_CHANNEL);
    		serialPort.writeBytes(packet.getBytes());
			
    		readCommandChannel();
		} catch (Exception e) {
			e.printStackTrace();
		}    	
    }

    @Command(description="Save the data channel sent from the card and write to a file.\n\n" + GET_CMD, abbrev="")
    public String GET( @Param(name="Path", description="Local name of the file to upload, including the full path.") String path) {
    	try {
    		channelMgr.getFile(path);
    		
    		readBothChannels();
		} catch (Exception e) {
			e.printStackTrace();
		}
    	return "File saved = " + path;
    }
    
    @Command(description="Upload a file to the card.\n\n" + STOR_CMD, abbrev="")
    public void STOR(
    		@Param(name="Source", description="Local name of the file to upload, including the full path.") String source, 
    		@Param(name="Target", description="Name of the file, including full path on the card.") String target) {
    	
    	double startTime = System.currentTimeMillis();
    	try {
    		SerialPortPacket packet = new SerialPortPacket(sendCommand("STOR", target), COMMAND_CHANNEL);
    		serialPort.writeBytes(packet.getBytes());
			
			// You have 200 miliseconds to make this call
			channelMgr.putFile(source);
			
			// Must send SRFT to tell card transfer complete
			SFRT(target);
			
			double endTime   = System.currentTimeMillis();
			
			System.out.println("STOR time in seconds = " + (endTime - startTime)/1000);
			
			readBothChannels();
		} catch (Exception e) {
			e.printStackTrace();
		}    	
    }
    
    // GKBluetoothCard : public Response finalize(String cardPath)
    // Line 101
    @Command(description="Finalize a file after executing a \"STOR\".\n\n" + SFRT_CMD, abbrev="")
    public void SFRT(
    		@Param(name="Path", description="Name of the folder including the full path.") String path) {
        String timestamp = new SimpleDateFormat("yyyyMMddhhmmss").format(new Date());
        try {
        	SerialPortPacket packet = new SerialPortPacket(sendCommand("SRFT", timestamp + " " + path), COMMAND_CHANNEL);
        	serialPort.writeBytes(packet.getBytes());
        	
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    
    @Command(description="Empty que memory for command channel", abbrev="")
    public String c() {
    	String data = new String();
		try {
			data = channelMgr.readCommand();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return data;
    }
    
    @Command(description="Empty que memory for data channel", abbrev="")
    public String d() {
    	String data = new String();
		try {
			data = channelMgr.readData();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return data;
    }    

	public static void main(String[] args) {
		SerialComm tw = new SerialComm();
		try {
			tw.process();
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (tw.serialPort != null ) {
				try {
					tw.serialPort.closePort();
				} catch (Exception e) {/* eat it */ }
			}
		}
	}
	
    private void readBothChannels() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        PollBothChannels poll = new PollBothChannels();
        poll.setChannelMgr(channelMgr);
        
        Future<String> future = executor.submit(poll);

        final int READ_TIMEOUT = 3;
        
        try {
            System.out.println(future.get(READ_TIMEOUT, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            future.cancel(true);
        } catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
        executor.shutdownNow();
    }	

    private void readCommandChannel() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        PollCommandChannel poll = new PollCommandChannel();
        poll.setChannelMgr(channelMgr);
        
        Future<String> future = executor.submit(poll);

        final int READ_TIMEOUT = 3;
        
        try {
            System.out.println(future.get(READ_TIMEOUT, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            future.cancel(true);
        } catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
        executor.shutdownNow();
    }	

    private void readDataChannel() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        PollDataChannel poll = new PollDataChannel();
        poll.setChannelMgr(channelMgr);
        
        Future<String> future = executor.submit(poll);

        final int READ_TIMEOUT = 3;
        
        try {
            System.out.println(future.get(READ_TIMEOUT, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            future.cancel(true);
        } catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
        executor.shutdownNow();
    }	

    
	private void process() throws Exception {	
		
		ShellFactory.createConsoleShell("xftp", "Enter 'examples', '?help <cmd>', or '?list' to list all commands and example commands", this).commandLoop();
		
		// LIST /*
		// System.out.println("Building command.");
		// SerialPortPacket packet = new SerialPortPacket(sendCommand("LIST", "/*"), COMMAND_CHANNEL);
		//  530 not logged
		
		// SerialPortPacket packet = new SerialPortPacket(sendCommand("LIST", "/data/*"), COMMAND_CHANNEL);
		// 530 not logged
		
		// SerialPortPacket packet = new SerialPortPacket(sendCommand("LIST", "/auth/*"), COMMAND_CHANNEL);
		// 530 not logged
		
		// SerialPortPacket packet = new SerialPortPacket(sendCommand("LIST", "/auth/signin/*"), COMMAND_CHANNEL);
		// 530 not logged
		
		//SerialPortPacket packet = new SerialPortPacket(sendCommand("LIST", "/device/*"), COMMAND_CHANNEL);
		//SerialPortPacket packet = new SerialPortPacket(sendCommand("RETR", "/device/settings/*"), COMMAND_CHANNEL);
		
		// System.out.println("Sending command.");
		// out.write(packet.getBytes());
		
		//String hex = "01 00 1E 4C 49 53 54 20 2F 61 70 70 73 2F 76 61 75 6C 74 2F 64 61 74 61 2F 2A 0D 0A 00 00";
		//out.write(hexStringToByteArray(hex.replaceAll("\\s","")));
		
	}
	
	private void cleanUpLockFiles() {
		File dir = new File("/var/lock");

		if (dir.list().length > 0) {
			System.out.println("Loc files exist, try to delete.");
			for(File file: dir.listFiles()) { 
				file.delete();
			}
		}
	}

	void connect(String portName) throws Exception {
		System.out.println("Connecting...");
		
		serialPort = new SerialPort(portName);
		
        if (serialPort.isOpened()) {
        	throw new Exception("Port already open");
        }
				
        //Open port
        serialPort.openPort();
                
        //We expose the settings. You can also use this line - serialPort.setParams(9600, 8, 1, 0);
        serialPort.setParams(SerialPort.BAUDRATE_115200, 
                             SerialPort.DATABITS_8,
                             SerialPort.STOPBITS_1,
                             SerialPort.PARITY_NONE);
        
        for (int i = 0; i <= MAX_CHANNEL_NUMBER; i++) {
            mChannelBuffers[i] = new LinkedBlockingQueue<>();
        }
        
        channelMgr = new ChannelManager();
        channelMgr.setBlockingQue(mChannelBuffers);
        channelMgr.setIOStreams(serialPort);
        
        sr = new SerialReader(serialPort);
        sr.setBlockingQue(mChannelBuffers);
        srThread = new Thread(sr);
        srThread.start();
        		
		System.out.println("Connecting finished");
	}
	
    private byte[] sendCommand(String method, String argument) throws IOException {
        String cmd = String.format("%s %s\r\n", method, argument);
        return cmd.getBytes(StandardCharsets.US_ASCII);
    }
    
    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    } 
    
    private static String bytesToHex(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for(byte b : in) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
           
}

