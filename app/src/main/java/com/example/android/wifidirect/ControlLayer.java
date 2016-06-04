package com.example.android.wifidirect;

import java.net.SocketTimeoutException;
import java.util.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import android.util.Log;


public class ControlLayer implements Runnable{

    private boolean isOwner;
    private boolean isRunning;
    private String goIP = null;
    private String myIP = null;
    private HashSet<String> peerIP = new HashSet<String>();
    ServerSocket serverSocket;

    public ControlLayer(boolean isOwner, String goIP){
        this.isOwner = isOwner;
        this.goIP = goIP;
    }

    @Override
    public void run() {

        // Process received message
        init();
        isRunning = true;

        // Accept incoming connection
        try {
            serverSocket = new ServerSocket(9000);
            serverSocket.setSoTimeout(10000);

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();

                    BufferedReader Reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    String request = Reader.readLine();

                    processRequest(request, clientSocket);
//                    new Thread(new Listenthread(socket,connectIP)).start();
                } catch (SocketTimeoutException s) {
                    Log.e(WiFiDirectActivity.TAG, "Waiting for client");
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
                    peerIP.add(goIP);

                    success = true;

                } catch (IOException e){
                    Log.d(WiFiDirectActivity.TAG, "Init failed, try again");
                }
            }
        }
        else{
            myIP = goIP;
        }
    }

    // Send the message and returns a boolean indicating whether it was successful
    private boolean sendMessage(String message, String ip) {

        int trialcounter = 2;
        boolean success = false;
        while(!success && trialcounter != 0){

            try{
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
            } catch (IOException e){
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
                trialcounter--;
            }
        }

        return success;
    }

    private void processRequest(String request, Socket clientSocket){

        // Process request and send response as needed
        if (request.contains("HELLO")){

            // Store the received IP in the HashSet
            String ip = clientSocket.getRemoteSocketAddress().toString();
            ip = ip.substring(ip.indexOf('/')+1, ip.indexOf(':'));
            peerIP.add(ip);
            Log.d(WiFiDirectActivity.TAG, "IP Registered: " + ip);

            // Construct the IP set string
            String ipset = "";
            for(Iterator it = peerIP.iterator(); it.hasNext();){
                ipset += it.next() + "/";
            }

            // Broadcast the new IP to everyone
            for(Iterator it = peerIP.iterator(); it.hasNext();){
                String message = "SYN:" + ipset;
                sendMessage(message, it.next().toString());
            }

            Log.d(WiFiDirectActivity.TAG, "IP Broadcasted: " + ipset);

        } else if (request.contains("SYN")) {

            // Parsing the received IPs
            String ipset = request.substring(request.indexOf(':')+1);
            Log.d(WiFiDirectActivity.TAG, "Synchronize IP set from owner " + ipset);
            String iparr[] = ipset.split("/");

            // Adding all the received IPs into the HashSet
            for(int i = 0; i < iparr.length; i++){
                if(myIP.compareTo(iparr[i]) != 0 && iparr[i] != "")
                    peerIP.add(iparr[i]);
            }
        }
    }
}
