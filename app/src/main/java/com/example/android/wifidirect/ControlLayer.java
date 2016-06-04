package com.example.android.wifidirect;

import java.net.SocketTimeoutException;
import java.util.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import android.util.Log;


public class ControlLayer{

    static public String goIP = null;
    static public String myIP = null;

    private boolean isOwner;
    private boolean isRunning;
    private HashMap<String, Boolean> peerIP = new HashMap<String, Boolean>();
    ServerSocket serverSocket;


    public ControlLayer(boolean isOwner, String goIP){
        this.isOwner = isOwner;
        this.goIP = goIP;
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

                            BufferedReader Reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                            String request = Reader.readLine();
                            if (request != null)
                                processRequest(request, clientSocket);
                        } catch (SocketTimeoutException s) {
                            Log.e(WiFiDirectActivity.TAG, "Waiting for client");
                            printIPtoLog();
                        } catch (IOException e) {
                            Log.e(WiFiDirectActivity.TAG, e.getMessage());
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

                    // Register IP with Group Owner
                    String message = "HELLO";
                    sendMessage(message, goIP);
                    Log.d(WiFiDirectActivity.TAG, "Socket opened: OwnerIP = " + goIP);

                    // Update IP variables
                    Socket socket = new Socket(goIP, 9000);
                    myIP = socket.getLocalAddress().toString().substring(1);
                    socket.close();
//                    peerIP.put(goIP, false);

                    success = true;

                } catch (IOException e){
                    Log.d(WiFiDirectActivity.TAG, "Init failed, try again");
                }
            }
        }
        else{
            myIP = goIP;
        }
        peerIP.put(myIP, false);
    }

    // Send the message and returns a boolean indicating whether it was successful
    public void sendMessage(final String message, final String ip) {

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
                        socket.setSoTimeout(5000);

                        // Write to the socket
                        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                        writer.write(message);
                        writer.flush();

                        // Close the socket and writer
                        writer.close();
                        socket.close();

                        success = true;
                        Log.d(WiFiDirectActivity.TAG, "Message Sent: " + message);
                    } catch (IOException e) {
                        Log.e(WiFiDirectActivity.TAG, e.getMessage());
                        trialCounter--;
                        Log.e(WiFiDirectActivity.TAG, "Retry Counter = " + trialCounter);
                    }
                }
            }
        }.start();
    }

    private void processRequest(String request, Socket clientSocket){

        // Process request and send response as needed
        if (request.contains("HELLO")){

            // Store the received IP in the HashSet
            String ip = clientSocket.getRemoteSocketAddress().toString();
            ip = ip.substring(ip.indexOf('/')+1, ip.indexOf(':'));
            peerIP.put(ip, false);
            Log.d(WiFiDirectActivity.TAG, "IP Registered: " + ip);

            // Construct the IP set string
            String ipset = "";
            for(Iterator it = peerIP.keySet().iterator(); it.hasNext();){
                ipset += it.next() + "/";
            }

            // Broadcast the new IP to everyone except myself
            for(Iterator it = peerIP.keySet().iterator(); it.hasNext();){
                String message = "SYN:" + ipset;
                String client = it.next().toString();
                if (client.compareTo(myIP) != 0)
                    sendMessage(message, client);
            }

            Log.d(WiFiDirectActivity.TAG, "IP Broadcasted: " + ipset);

        } else if (request.contains("SYN")) {
            // Only peers in a WiFiDirect group will receive SYN messages sent by the Group Owner

            // Parsing the received IPs
            String ipset = request.substring(request.indexOf(':')+1);
            Log.d(WiFiDirectActivity.TAG, "Synchronize IP set from owner " + ipset);
            String iparr[] = ipset.split("/");

            // Adding all the received IPs into the HashSet
            for(int i = 0; i < iparr.length; i++){
                if(iparr[i] != "")
                    peerIP.put(iparr[i], false);
            }
        } else if(request.contains("START")){
            String ip = clientSocket.getRemoteSocketAddress().toString();
            ip = ip.substring(ip.indexOf('/')+1, ip.indexOf(':'));
            peerIP.put(ip,true);

            Log.d(WiFiDirectActivity.TAG, "A Hadoop slave has been started!");
        }
    }

    private void printIPtoLog(){

        Log.d(WiFiDirectActivity.TAG, "My IP = " + myIP);
        Log.d(WiFiDirectActivity.TAG, "Group consists of: \n");
        for (Map.Entry<String, Boolean> entry : peerIP.entrySet()) {
            String key = entry.getKey();
            Boolean value = entry.getValue();
            Log.d(WiFiDirectActivity.TAG, "IP = " + key + "\t Status = " + value);
        }
    }

    private class request{
        String type;
        String value;
    }
}
