package com.example.android.wifidirect;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Serializable;
import java.net.SocketTimeoutException;
import java.util.*;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;


public class ControlLayer implements Serializable{

    private static final int MASTER = 1;

    static public String goIP = null;
    static public String myIP = null;

    private Handler handler;

    private boolean isOwner;
    private boolean isRunning;
    private HashMap<String, IPStatus> peerIP;
    ServerSocket serverSocket;

    public ControlLayer(boolean isOwner, String goIP, Handler handler){
        this.isOwner = isOwner;
        this.goIP = goIP;
        this.peerIP = new HashMap<String, IPStatus>();
        this.handler = handler;
    }

    public void start() {

        new Thread() {
            public void run() {
                try {
                    init();
                    isRunning = true;

                    serverSocket = new ServerSocket(9000);
                    serverSocket.setSoTimeout(10000);

                    while (isRunning) {
                        try {
                            Socket clientSocket = serverSocket.accept();

                            Log.d(WiFiDirectActivity.TAG, "New connection accepted");
                            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(clientSocket.getInputStream()));
                            Request request = (Request) in.readObject();

                            if (request != null)
                                processRequest(request, clientSocket);
                            in.close();

                        } catch (SocketTimeoutException s) {
                            Log.d(WiFiDirectActivity.TAG, "Waiting for client...");

                            if(isOwner){
                                broadcastMessage("HEART");
                                for (Map.Entry<String, IPStatus> entry : peerIP.entrySet()) {
                                    if (entry.getKey() != myIP)
                                        entry.getValue().incHeartBeat();
                                }
                            }
                            printIPtoLog();

                        } catch (ClassNotFoundException e){
                            Log.e(WiFiDirectActivity.TAG, "Error: ClassNotFoundException encountered");
                        } catch (IOException e) {
                            Log.e(WiFiDirectActivity.TAG, "Error: IOException encountered");
                        }
                    }

                    serverSocket.close();

                } catch (IOException e) {
                    Log.e(WiFiDirectActivity.TAG, "Control Layer initiation failed");
                    Log.e(WiFiDirectActivity.TAG, e.getMessage());
                }
            }
        }.start();

    }

    public void stop(){
        isRunning = false;
    }

    public void init(){
        // if not owner, send message to owner to register IP
        if (!isOwner) {

            boolean success = false;
            while (!success){
                try {

                    // Update IP variables
                    Socket socket = new Socket(goIP, 9000);
                    myIP = socket.getLocalAddress().toString().substring(1);
                    socket.close();

                    Log.d(WiFiDirectActivity.TAG, "My IP = " + myIP);

                    // Register IP with Group Owner
                    sendMessage("HELLO", goIP);

                    success = true;

                } catch (IOException e){
                    Log.d(WiFiDirectActivity.TAG, "Init failed, try again");
                }
            }
        }
        else{
            myIP = goIP;
        }
        peerIP.put(myIP, new IPStatus());
    }

    // Send the message and returns a boolean indicating whether it was successful
    public void sendMessage(final String type, final String ip) {

        new Thread() {
            @Override
            public void run() {
                int trialCounter = 2;
                boolean success = false;
                while (!success && trialCounter != 0)
                {
                    try {

                        // Create socket
                        Socket socket = new Socket(ip, 9000);

                        // Construct the request object
                        Request request = new Request(type, peerIP);

                        // Write to the socket
                        ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                        out.writeObject(request);
                        out.flush();

                        // Close the socket and writer
                        out.close();
                        socket.close();

                        success = true;
                        Log.d(WiFiDirectActivity.TAG, "Message Sent: " + type);
                    } catch (IOException e) {
                        trialCounter--;
                        Log.e(WiFiDirectActivity.TAG, "Error in sendMessage: \t Retry Counter = " + trialCounter);
                        e.printStackTrace(System.err);
                    }
                }
            }
        }.start();
    }

    public void broadcastMessage(String type){

        // Broadcast the new IP to everyone except myself
        for(Iterator it = peerIP.keySet().iterator(); it.hasNext();){
            String client = it.next().toString();
            if (client.compareTo(myIP) != 0)
                sendMessage(type, client);
        }
    }

    private void processRequest(Request request, Socket clientSocket){

        Log.d(WiFiDirectActivity.TAG, "Processing request: " + request.type);

        // Process request and send response as needed
        if (request.type.compareTo("HELLO") == 0){

            // Store the received IP in the HashSet
            String ip = clientSocket.getRemoteSocketAddress().toString();
            ip = ip.substring(ip.indexOf('/')+1, ip.indexOf(':'));
            peerIP.put(ip, new IPStatus());
            Log.d(WiFiDirectActivity.TAG, "IP Registered: " + ip);

            // Broadcast the new IP to everyone except myself
            broadcastMessage("SYN");

            Log.d(WiFiDirectActivity.TAG, "IP Broadcasted!");

        } else if (request.type.compareTo("SYN") == 0) {
            // Only peers in a WiFiDirect group will receive SYN messages sent by the Group Owner

            peerIP = request.map;

        } else if(request.type.compareTo("START") == 0){

            // Only Group Owner in a WiFiDirect group will receive SLAVE messages

            String ip = clientSocket.getRemoteSocketAddress().toString();
            ip = ip.substring(ip.indexOf('/')+1, ip.indexOf(':'));
//            peerIP.put(ip, true);

            //
            if (peerIP.get(ip) != null)
                peerIP.get(ip).updateStatus(true);
            else
                peerIP.put(ip, new IPStatus(true));

            broadcastMessage("SYN");
            Log.d(WiFiDirectActivity.TAG, "A Hadoop instance has been started!");

        } else if(request.type.compareTo("MASTER") == 0){

            // Enable the button
            Message msg = handler.obtainMessage();
            msg.what = MASTER;
            handler.sendMessage(msg);

        } else if(request.type.compareTo("HEART") == 0){

            sendMessage("BEAT", goIP);

        } else if(request.type.compareTo("BEAT") == 0){

            // Only Group Owner will receive BEAT messages
            String ip = clientSocket.getRemoteSocketAddress().toString();
            ip = ip.substring(ip.indexOf('/')+1, ip.indexOf(':'));

            Log.d(WiFiDirectActivity.TAG, "HEARTBEAT received from: " + ip);
            peerIP.get(ip).decHeartBeat();

        }
    }

    private void printIPtoLog(){

        Log.d(WiFiDirectActivity.TAG, "My IP = " + myIP);
        Log.d(WiFiDirectActivity.TAG, "Group consists of: \n");
        for (Map.Entry<String, IPStatus> entry : peerIP.entrySet()) {
            String key = entry.getKey();
            IPStatus value = entry.getValue();
            Log.d(WiFiDirectActivity.TAG, "IP = " + key + "\t Status = " + value);
        }
    }







}


