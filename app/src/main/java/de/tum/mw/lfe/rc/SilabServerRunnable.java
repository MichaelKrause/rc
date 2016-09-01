package de.tum.mw.lfe.rc;
/*
MIT License

        Copyright (c) 2015-2016 Michael Krause (krause@tum.de)

        Permission is hereby granted, free of charge, to any person obtaining a copy
        of this software and associated documentation files (the "Software"), to deal
        in the Software without restriction, including without limitation the rights
        to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
        copies of the Software, and to permit persons to whom the Software is
        furnished to do so, subject to the following conditions:

        The above copyright notice and this permission notice shall be included in all
        copies or substantial portions of the Software.

        THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
        IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
        FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
        AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
        LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
        OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
        SOFTWARE.
*/
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class SilabServerRunnable implements Runnable {
	
    	private CommunicationThread commThread;
    	private Thread thread;
    	private ServerSocket mServerSocket;
    	private Socket mClientSocket;
    	private Handler mGuiHandler;
    	
    	private List<byte[]> mToSend = new ArrayList<byte[]>();
    	public static final int PORT = 7007; // open this port
    	public static final int PACKETSIZE = 6;//bytes in mToSend packets
    	
    	public static final char NOT_CONNECTED = 0;
    	public static final char CONNECTED = 1;
    	public static final char UPDATE_MARKER_TEXT = 2;
        public static final char LOSS_OF_INFORMATION = 3;//discarded packages

    private static final String TAG = "RC.SilabRunnable";
    	
    	
    	
    	public SilabServerRunnable(Handler guiHandler){
    		mGuiHandler = guiHandler;
    	
            try {
            	if (mServerSocket == null) mServerSocket = new ServerSocket(PORT);
            } catch (Exception e) {
            	Log.e(TAG, "ServerThread failed on open port: " + e.getMessage());
            }
            
    	}
 
    	
    	public void send2Silab(SilabPacket packet){

    		/*
    		 packet PACKETSIZE =6
    		 4 int dikablisFrame
    		 1 byte task
    		 1 byte trial
    		 */
    	  	
    		
    		byte[] bytes = new byte[PACKETSIZE];
            ByteBuffer bytebuffer = ByteBuffer.wrap(bytes);
            //bytebuffer.order(ByteOrder.BIG_ENDIAN);
            bytebuffer.putInt(packet.dikablisFrame);
            bytebuffer.put(packet.task);
            bytebuffer.put(packet.trial);

    		synchronized(mToSend){//sync against send and clear
    			
    			//bytebuffer.putInt(mToSend.size());//how many packets are quequed befoe this packet
    			
    			//TODO_MAN comment this out
                if (mToSend.size() < 100) {//ugly workaround if silab connection fails prevent ehndless send queue
                    mToSend.add(bytes);//queque this packet
                }else {
                    mToSend.remove(0);//delete first and add last to end if we have more tahn 100 packtes
                    mToSend.add(99,bytes);
                    Message msg2 = mGuiHandler.obtainMessage();
                    msg2.what = LOSS_OF_INFORMATION;
                    mGuiHandler.sendMessage(msg2);

                }
    			//Log.i(TAG,"queued packets: " +Integer.toString(mToSend.size()));
    		}//sync	
    	}   	
    	
        public void run() {
            
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000);
                    mClientSocket = mServerSocket.accept();
                    commThread = new CommunicationThread(mClientSocket);
                    thread = new Thread(commThread);
                    thread.start();
                } catch (Exception e) {
                	if(!Thread.currentThread().isInterrupted()){//only log error if this was not an intentional interrupt
                		Log.e(TAG, "ServerThread failed on accept connection: " + e.getMessage());
                	}	
                }
            }//while
            
            closeSockets();
            
        }//run
        
        //helpers
        public void closeSockets(){
            try{
                if (mServerSocket != null) mServerSocket.close();
                if (mClientSocket != null) mClientSocket.close();
              } catch (Exception e) {
            	  Log.e(TAG, "ServerThread failed to close sockets: " + e.getMessage());
              }
        }        
        
    	public String ipStatus(){
    		String tempStr = "";
    	      if((mServerSocket != null) && (mServerSocket.isBound()) ){
    		       tempStr += getIpAddress() + ":"+PORT;
    		      }else{
    			       tempStr += "-----";	    	  
    		      }
    	      return tempStr;
    	}

		public String getIpAddress(){
			try {
				Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
				while(nis.hasMoreElements()){//all interfaces
					NetworkInterface ni = nis.nextElement();
					Enumeration<InetAddress> ips = ni.getInetAddresses();
					while(ips.hasMoreElements()){//all addresses
						InetAddress a = ips.nextElement();
						if (!a.isLoopbackAddress() /*not localhost*/ && a instanceof Inet4Address /*not ip6*/) {
							return a.getHostAddress().toString();
						}
					}//all addresses
				}//all interfaces
			} catch (Exception e) {
				Log.e(TAG, "getIpAddress() failed: " + e.getMessage());
			}
			return "---";
		}






	class CommunicationThread implements Runnable {

    		private Socket clientSocket;
    		private BufferedReader input;
    		//private BufferedWriter output;
    		private OutputStream output;

    		
    		public CommunicationThread(Socket clientSocket) {
    			this.clientSocket = clientSocket;

    			try {
    				this.input = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
    				//this.output = new BufferedWriter(new OutputStreamWriter(this.clientSocket.getOutputStream()));
    				this.output = this.clientSocket.getOutputStream();
    			} catch (Exception e) {
                	Log.e(TAG, "CommunicationThread failed on create streams: " + e.getMessage());
    			}
    		}

    		public void run() {
    			
    		    Message msg = mGuiHandler.obtainMessage();
    		    msg.what = CONNECTED;
    		    mGuiHandler.sendMessage(msg);

    			int read;
    			while ((!Thread.currentThread().isInterrupted()) && (!clientSocket.isClosed())) {
    				
    				SystemClock.sleep(10);//TODO adjust to your needs
    				
    				try {
    					if(input.ready()){
    						read = input.read();							
    					}else{
    						read =-1;
    					}
    					if (read != -1){							  
 
    				        switch(read)
    				        { 
    				        case 'A':
    				        	//changeToLayout(LAYOUT_1);
    				        	break;	
    				        case '1':
    				        	/*
    						      Message msg2 = mGuiHandler.obtainMessage();
      						      msg2.what = UPDATE_MARKER_TEXT;
      						      mGuiHandler.sendMessage(msg2);
      						      */
    				        	break;	
    				        	

    				        	
    				        	
    				        default:
    				        }
    					     
    					}//if
    					
    					
    											
    					//output

    					synchronized(mToSend){//sync against append and clear
    						if(mToSend.size() > 0){
    							Log.d(TAG, "Send");
    							output.write(mToSend.get(0),0,PACKETSIZE);//send first in queue
    							//TODO_MAN output.write(mToSend.get(0),0,mToSend.get(0).length);//send first in queue
    							output.flush();
    							mToSend.remove(0);//remove first from queue
    							
    						}
    					}//sync
    					
    					
    				} catch (Exception e) {
    	            	Log.e(TAG, "CommunicationThread failed while input/output: " + e.getMessage());
    	            	Thread.currentThread().interrupt();
    				}
    				
    			}//while
    			try{	
    				input.close();
    				output.close();
    			} catch (Exception e) {
                	Log.e(TAG, "CommunicationThread failed on closing streams: " + e.getMessage());
    			}
    		    Message msg2 = mGuiHandler.obtainMessage();
    		    msg2.what = NOT_CONNECTED;
    		    mGuiHandler.sendMessage(msg2);

    		}//run

    	}
    	
    	
    }
    
//-------------------------------------------------------------------

	
	
