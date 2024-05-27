import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.*;
import java.util.*;

public class server {
    private static DataOutputStream dataOutputStream = null;
    private static DataInputStream dataInputStream = null;
 
    public static void main(String[] args)
    {
        // Here we define Server Socket running on port 2222
        try (ServerSocket serverSocket
             = new ServerSocket(2222)) {
	    	System.out.println("");
	       	System.out.println("");
			System.out.println("~ Novastoma Test Server ~");
	       	System.out.println("=============================");
            System.out.println(
                "Server is Starting in Port 2222");

                System.out.println("FileServer is up at "
                + InetAddress.getLocalHost().getHostAddress()
                + " on port " + serverSocket.getLocalPort());
		System.out.println("=============================");

		Enumeration<NetworkInterface> n = NetworkInterface.getNetworkInterfaces();
                for (; n.hasMoreElements();)
                {
                        NetworkInterface e = n.nextElement();
                       // System.out.println("Interface: " + e.getName());
                        Enumeration<InetAddress> a = e.getInetAddresses();
                        for (; a.hasMoreElements();)
                        {
                                InetAddress addr = a.nextElement();
                                if(addr.getHostAddress().contains(":")){

                                }else{
                                    System.out.println("  " + addr.getHostAddress());
                                }
                                
                        }
                }
			System.out.println("=============================");
            // Accept the Client request using accept method
            Socket clientSocket = serverSocket.accept();
            System.out.println("Connected");
            dataInputStream = new DataInputStream(
                clientSocket.getInputStream());
            dataOutputStream = new DataOutputStream(
                clientSocket.getOutputStream());
            // Here we call receiveFile define new for that
            // file
            receiveFile("WasteDatadb");
 
	     dataInputStream.close();
	     dataOutputStream.close();
	     clientSocket.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
 
    // receive file function is start here
 
    private static void receiveFile(String fileName)
        throws Exception
    {
        int bytes = 0;
        FileOutputStream fileOutputStream
            = new FileOutputStream(fileName);
 
        long size
            = dataInputStream.readLong(); // read file size
        byte[] buffer = new byte[4 * 1024];
        while (size > 0
               && (bytes = dataInputStream.read(
                       buffer, 0,
                       (int)Math.min(buffer.length, size)))
                      != -1) {
            // Here we write the file using write method
            fileOutputStream.write(buffer, 0, bytes);
            size -= bytes; // read upto file size
        }
        // Here we received file
        System.out.println("FILE RECEIVED!");
	  fileOutputStream.close();
    }
}

