/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.wifidirect;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.wifidirect.DeviceListFragment.DeviceActionListener;
import com.example.streamlocalfile.LocalFileStreamingServer;
//import com.example.streamlocalfile.Controlpath;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Scanner;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.streamlocalfile.LocalFileStreamingServer;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements ConnectionInfoListener {

    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    protected static final int PLAY_VIDEO_RESULT_CODE = 21;
    private final int MSG_IP = 0;
    private final int ACTIVE = 1;
    private final int MSG_PORT = 2;
    private final int MSG_BYE = 3;
    private View mContentView = null;
    private WifiP2pDevice device;
    private WifiP2pInfo info;
    private String myIP = null;
    private String currentplayingIP = null;
    private HashSet<String> offerIP = new HashSet<String>();
    private ProgressDialog progressDialog = null;
    private LocalFileStreamingServer mServer = null;
    private Controlpath controlpath = null;
    private int listener = 0 ;
    private String server_file_uri;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mContentView = inflater.inflate(R.layout.device_detail, null);
        mContentView.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
                        "Connecting to :" + device.deviceAddress, true, true
//                        new DialogInterface.OnCancelListener() {
//
//                            @Override
//                            public void onCancel(DialogInterface dialog) {
//                                ((DeviceActionListener) getActivity()).cancelDisconnect();
//                            }
//                        }
                );
                ((DeviceActionListener) getActivity()).connect(config);
            }
        });

        mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        ((DeviceActionListener) getActivity()).disconnect();
                        if (mServer != null) {
                            Log.d(WiFiDirectActivity.TAG, "HTTP Server stopped without being declared");
                            mServer.stop();
                        }
                        Log.d(WiFiDirectActivity.TAG, "HTTP Server Terminated");
                        controlpath.stop();
                        Log.d(WiFiDirectActivity.TAG, "Control Path Server Terminated");
                    }
                });

        mContentView.findViewById(R.id.btn_start_client).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // Allow user to pick an image from Gallery or other
                        // registered apps
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("video/*");
                        startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
                    }
                });
        mContentView.findViewById(R.id.stop_server).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        controlpath.stop();
                        mContentView.findViewById(R.id.stop_server).setVisibility(View.INVISIBLE);
                    }
                }
        );

        return mContentView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(WiFiDirectActivity.TAG,"requestCode is "+Integer.toString(requestCode)+";resultCode is" + Integer.toString(resultCode));
        switch (requestCode) {
            case CHOOSE_FILE_RESULT_CODE:
                if(resultCode== Activity.RESULT_OK) {
                    Log.d(WiFiDirectActivity.TAG, "File chosen with result code = " + CHOOSE_FILE_RESULT_CODE);
                    // User has picked an image.
                    Uri uri = data.getData();
                    server_file_uri = uri.toString();
                    String server_file_path = getRealPathFromURI(uri);
                    TextView statusText = (TextView) mContentView.findViewById(R.id.status_text);
                    //statusText.setText("Sending: " + server_file_path);
                    Log.d(WiFiDirectActivity.TAG, "Intent(DeviceDetailFragment)----------- " + server_file_path);

                    // Initiating and start LocalFileStreamingServer
                    mServer = new LocalFileStreamingServer(new File(server_file_path), myIP, controlpath);
                    //String deviceIp = info.groupOwnerAddress.getHostAddress();
                    //        Log.d(WiFiDirectActivity.TAG,"Here is the Httpserver addr: "+httpUri);
                    //        if(controlpath!=null) {
                    //            controlpath.sendPort(httpUri);
                    //            Log.d(WiFiDirectActivity.TAG,"sending url to client");
                    //        }
                    if (null != mServer && !mServer.isRunning())
                        mServer.start();
                    mContentView.findViewById(R.id.stop_server).setVisibility(View.VISIBLE);
                }
                break;
            case PLAY_VIDEO_RESULT_CODE:
                if(resultCode==Activity.RESULT_CANCELED) {
                    Log.d(WiFiDirectActivity.TAG, "Video play terminated with result code = " + Integer.toString(requestCode));
                    controlpath.sendGoodBye();
                }
                break;
            default:
                Log.d(WiFiDirectActivity.TAG, "unknown result code=" + resultCode);
        }
    }

    private String getRealPathFromURI(Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        CursorLoader loader = new CursorLoader(getActivity(), contentUri, proj, null, null, null);
        Cursor cursor = loader.loadInBackground();
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String result = cursor.getString(column_index);
        cursor.close();
        return result;
    }
    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        this.info = info;
        this.getView().setVisibility(View.VISIBLE);
        // The owner IP is now known.
        TextView view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(getResources().getString(R.string.group_owner_text)
                + ((info.isGroupOwner == true) ? getResources().getString(R.string.yes)
                : getResources().getString(R.string.no)));

        // InetAddress from WifiP2pInfo struct.
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText("Group Owner IP - " + info.groupOwnerAddress.getHostAddress());

        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.

        if (info.groupFormed  ) {
            //Log.d(WiFiDirectActivity.TAG,"start init");
            if(controlpath==null) {
                controlpath = new Controlpath(info.isGroupOwner, info.groupOwnerAddress.getHostAddress());
                controlpath.start();
            }
            if(!info.isGroupOwner) {
                /*
                progressDialog.setTitle("Waiting for owner preparation");
                progressDialog.setMessage("Loading...");
                progressDialog.setCancelable(true);
            final Handler timerhandler = new Handler();
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    controlpath.init();
                    timerhandler.postDelayed(this,500);
                }
            }
            */
            }else{

            }
        }
//        else if (info.groupFormed) {
//            // The other device acts as the client. In this case, we enable the
//            // get file button.
//            //mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);
//            //((TextView) mContentView.findViewById(R.id.status_text)).setText(getResources()
//                    .getString(R.string.client_text));
//        }

        mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);
        //hide the connect button
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);
    }

    /**
     * Updates the UI with device data
     *
     * @param device the device to be displayed
     */
    public void showDetails(WifiP2pDevice device) {
        this.device = device;
        this.getView().setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(device.deviceAddress);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(device.toString());

    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    public void resetViews() {
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.status_text);
        view.setText(R.string.empty);
        mContentView.findViewById(R.id.btn_start_client).setVisibility(View.GONE);
        this.getView().setVisibility(View.GONE);
    }

    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */
    public class StreamingAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;
        private TextView statusText;
        private BufferedReader peerReader;

        /**
         * @param context
         * @param statusText
         */
        public StreamingAsyncTask(Context context, View statusText, BufferedReader peerReader) {
            this.context = context;
            this.statusText = (TextView) statusText;
            this.peerReader = peerReader;
        }

        @Override
        protected String doInBackground(Void... params) {

//                ServerSocket serverSocket = new ServerSocket(8988);
//                Log.d(WiFiDirectActivity.TAG, "Server: Socket opened");
//                Socket client = serverSocket.accept();
//                Log.d(WiFiDirectActivity.TAG, "Server: connection done");
//                final File f = new File(Environment.getExternalStorageDirectory() + "/"
//                        + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
//                        + ".jpg");
//
//                File dirs = new File(f.getParent());
//                if (!dirs.exists())
//                    dirs.mkdirs();
//                f.createNewFile();
//
//                Log.d(WiFiDirectActivity.TAG, "server: copying files " + f.toString());
            String url = null;

            try {


                //readLine will block until input is available
                url = peerReader.readLine();
                Log.d(WiFiDirectActivity.TAG, "HTTP Server IP Address: " + url);

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse(url), "video/*");
                startActivity(intent);


            }
            catch (IOException e)
            {
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
            }
            return url;
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
//        @Override
//        protected void onPostExecute(String result) {
//            if (result != null) {
//                statusText.setText("File copied - " + result);
//                Intent intent = new Intent();
//                intent.setAction(android.content.Intent.ACTION_VIEW);
//                intent.setDataAndType(Uri.parse("file://" + result), "image/*");
//                context.startActivity(intent);
//            }
//
//        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            statusText.setText("Opening a listening socket");
        }

    }

    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);

            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d(WiFiDirectActivity.TAG, e.toString());
            return false;
        }
        return true;
    }
    private Handler handle = new Handler(){
        @Override
        public void handleMessage(Message msg){
            switch(msg.what){
                case MSG_IP:
                    myIP = (String)msg.obj;
                    Log.d(WiFiDirectActivity.TAG,"UI thread get from " + myIP);
                    break;

                case ACTIVE:
                    if(currentplayingIP==null) {
                        final String uri = (String) msg.obj;
                        Pattern pattern = Pattern.compile("(http://|https://){1}((\\d{1,3}\\.){3}\\d{1,3})(:\\d*/)(.*)");
                        Matcher matcher = pattern.matcher(uri);
                        final String receivedIP;
                        final String receivedfilename;
                        if (matcher.find()) {
                            receivedIP = matcher.group(2);
                            Log.d(WiFiDirectActivity.TAG,receivedIP);
                            currentplayingIP = receivedIP;
                            receivedfilename = matcher.group(5);
                            Log.d(WiFiDirectActivity.TAG, receivedIP);
                            AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
                            dialog.setTitle(receivedfilename + " is sharing video");
                            CharSequence choice_list[] = {"Play", "Download"};
                            final ArrayList mSelectedItems = new ArrayList();
                            dialog.setMultiChoiceItems(choice_list, null,
                                    new DialogInterface.OnMultiChoiceClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                            if (isChecked) {
                                                // If the user checked the item, add it to the selected items
                                                mSelectedItems.add(which);
                                                Log.d(WiFiDirectActivity.TAG, "mSelectedItems.add: " + which);

                                            } else if (mSelectedItems.contains(which)) {
                                                // Else, if the item is already in the array, remove it
                                                mSelectedItems.remove(Integer.valueOf(which));
                                            }
                                        }
                                    });
                            dialog.setCancelable(false);
                            dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

//                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
//                            //intent.setDataAndType(Uri.parse(url), "video/*");
//                            startActivity(intent);
                                    if (mSelectedItems.contains(0)) {
                                        Log.d(WiFiDirectActivity.TAG, "Play video");
                                        Intent intent = new Intent(getActivity().getApplicationContext(), VideoViewActivity.class);
                                        intent.setDataAndType(Uri.parse(uri), "video/*");
                                        startActivityForResult(intent, PLAY_VIDEO_RESULT_CODE);
                                    }
                                    if (mSelectedItems.contains(1)) {
                                        // start download server
                                        Log.d(WiFiDirectActivity.TAG, "uri=" + uri);
                                        String server_ip = uri.substring(7, uri.indexOf(":", 7));
                                        int port_offset = Integer.parseInt(
                                                uri.substring(18, uri.indexOf(":", 18)));
                                        Log.d(WiFiDirectActivity.TAG, server_ip + "," + port_offset);
                                        controlpath.sendDonwloadRequest(server_ip, myIP, 9000 + port_offset);
                                        Log.d(WiFiDirectActivity.TAG, "Download data");
                                        FileServerAsyncTask task = new FileServerAsyncTask(
                                                getActivity(), mContentView.findViewById(R.id.status_text), 9000 + port_offset);
                                        task.execute();
                                    }
                                }
                            });
                            dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                }

                            });
                            dialog.show();

                        }
                        else{
                            Log.d(WiFiDirectActivity.TAG,"URI no match");
                        }
                    }
                    else{

                    }
                    break;
                case MSG_PORT:
                    //Check http server is running or not, preventing fake msg;
                    if(mServer!=null&&mServer.isRunning()){
                        ++listener;
                        ((TextView) mContentView.findViewById(R.id.status_text)).setText(Integer.toString(listener) + " peer is playing");
                    }
                    break;
                case MSG_BYE:
                    listener--;
                    if(listener==0)
                    {
                        ((TextView) mContentView.findViewById(R.id.status_text)).setText("");
                        ((TextView) mContentView.findViewById(R.id.stop_server)).setVisibility(View.GONE);
                        if(mServer!=null)
                            mServer.stop();
                    }
                    else
                        ((TextView) mContentView.findViewById(R.id.status_text)).setText(Integer.toString(listener) + " peer is playing");
                default:
                    Log.w(WiFiDirectActivity.TAG, "handleMessage: unexpected msg: " + msg.what);
            }
//            new StreamingAsyncTask(getActivity(), mContentView.findViewById(R.id.status_text), peerReader)
//                    .execute();
        }
    };

    class Sendthread extends Thread {

        private String IP = null;
        private int port;
        private String msg = null;

        public Sendthread(String IP,String port,String msg){
            this.IP = IP;
            this.port = Integer.parseInt(port);
            this.msg =msg;
        }
        public Sendthread(String IP, int port,String msg){
            this.IP = IP;
            this.port = port;
            this.msg = msg;
        }

        @Override
        public void run() {
            BufferedWriter Writer;
            BufferedReader Reader;
            try {
                Socket socket = null;
                socket = new Socket(IP, port);
                socket.setSoTimeout(5000);
                Log.d(WiFiDirectActivity.TAG, "creating new socket " + IP + "/ " + port);

                Reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                //peerScanner = new Scanner(peerIS);
                Writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                Writer.write(msg + '\n');  //Greeting formate : Hello:(ID addr)
                Writer.flush();
                String reply = Reader.readLine();
                Log.d(WiFiDirectActivity.TAG,reply);
                int response = controlpath.ProcessRequest(reply,Writer);
                Writer.close();
                Reader.close();
                socket.close();
                //if(response!=controlpath.OK)
                //    run();
            }catch(IOException e){
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
                try {
                    Thread.sleep(400);
                    run();
                }catch (InterruptedException error){
                    Log.e(WiFiDirectActivity.TAG, error.getMessage());
                }
            }
        }
    }


    public class Controlpath implements Runnable {
        //public static final String TAG = Controlpath.class.getName();
        private final static int HELLO = 0;
        private final static int PORT = 1;
        private final static int OK = 2;
        private final static int OTHER = 3;
        private boolean isOwner;
        private boolean isRunning = false;
        private String OwnerIP = null;
        private String myIP = null;
        private Thread thread = null;
        private HashSet<String> peerIP = new HashSet<String>();

        //private BufferedReader peerReader = null;
        //private BufferedWriter peerWriter = null;
        //private String[] IPArray = null;

        public Controlpath(boolean isOwner,String IP){
            this.isOwner = isOwner;
            OwnerIP = IP;
        }

        public void start(){
            thread = new Thread(this);
            thread.start();
            isRunning = true;
        }
        public boolean isRunning(){return isRunning;}

        public boolean init() throws IOException{
            BufferedReader Reader;
            BufferedWriter Writer;
            Socket socket =null;
            if (!isOwner) {
                try {
                    Log.d(WiFiDirectActivity.TAG, "enter init");
                    Thread.sleep(400);
                    socket = new Socket(OwnerIP, 9000);
                    Log.d(WiFiDirectActivity.TAG, "Opening control socket - ");
                    socket.setSoTimeout(5000);
                    //socket.bind(null);
                    //socket.connect((new InetSocketAddress(OwnerIP, 9000)), 5000);
                    Log.d(WiFiDirectActivity.TAG, "Connection control socket");
                    //peerIS = socket.getInputStream();
                    Reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    //peerScanner = new Scanner(peerIS);
                    myIP = socket.getLocalAddress().toString().substring(1);
                    Writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                    Writer.write("Hello:" + OwnerIP + '\n');  //Greeting formate : Hello:(ID addr)
                    Writer.flush();
                    String line = Reader.readLine();
                    Log.d(WiFiDirectActivity.TAG,line);
                    Log.d(WiFiDirectActivity.TAG, "get from local method: " + myIP);
                    Writer.close();
                    Reader.close();
                    socket.close();
                    Log.d(WiFiDirectActivity.TAG, "socket closes");
                    peerIP.add(OwnerIP);
                } catch (InterruptedException e) {
                    Log.e(WiFiDirectActivity.TAG, e.getMessage());
                }
            }
            else{
                myIP = OwnerIP;
            }
            handle.obtainMessage(MSG_IP,myIP).sendToTarget();
            return true;
        }
        @Override
        public void run()  {
            Log.d(WiFiDirectActivity.TAG, " controlling thread is running");
            try {
                this.init();
            }catch (IOException e){
                Log.d(WiFiDirectActivity.TAG,"Init failed, try again");
                run();
            }
            try{
                ServerSocket serverSocket = new ServerSocket(9000);
                while(isRunning){
                    Socket socket = serverSocket.accept();
                    String connectIP = socket.getRemoteSocketAddress().toString();
                    new Thread(new Listenthread(socket,connectIP)).start();
                }

            }catch (IOException e){
                Log.e(WiFiDirectActivity.TAG,e.getMessage());
                Log.d(WiFiDirectActivity.TAG,"accepting error");
            }
        }
        public int ProcessRequest(String msg,BufferedWriter out){
            if(msg.contains("Hello:")) {
                Log.d(WiFiDirectActivity.TAG,"IP notified");
                return HELLO;
            }
            else if(msg.contains("PORT:")) {
                //get portnum from message;
                String url = msg.substring(msg.indexOf("http://"));
                Log.d(WiFiDirectActivity.TAG, url);
                handle.obtainMessage(ACTIVE, url).sendToTarget();
                try {
                    out.write("PORT OK");
                    Log.d(WiFiDirectActivity.TAG, "Reply to server");
                    out.flush();
                } catch (IOException e) {
                    Log.e(WiFiDirectActivity.TAG, e.getMessage());
                }
                return OTHER;
            }
            else if(msg.contains("CUT:")){
                Log.d(WiFiDirectActivity.TAG,"Server wanna shut down server");
                //active cut thread , and notifies peer
                return OTHER;
            }
            else if(msg.contains("GOODBYE:")){
                String cancelIP = msg.substring(msg.indexOf(':')+1);
                try{
                    out.write("PORT OK");
                    Log.d(WiFiDirectActivity.TAG,cancelIP+"has played off");
                    out.flush();
                }catch (IOException e){
                    Log.e(WiFiDirectActivity.TAG,e.getMessage());
                }
                handle.sendEmptyMessage(MSG_BYE);
                return OTHER;
            }
            else if(msg.contains("PORT OK")){
                handle.obtainMessage(MSG_PORT,true).sendToTarget();
                Log.d(WiFiDirectActivity.TAG,"OK");
                return OK;
            }
            else if(msg.contains("SYN")){
                msg = msg.substring(msg.indexOf(':')+1);
                Log.d(WiFiDirectActivity.TAG,msg);
                String IPs[] = msg.split("/");
                for(int i = 0 ; i < IPs.length ;++i){
                    if(myIP.compareTo(IPs[i])==0)
                        continue;
                    else if(IPs[i]!=""){
                        Log.d(WiFiDirectActivity.TAG,"Here "+IPs[i]);
                        peerIP.add(IPs[i]);
                    }
                }
                try{
                    out.write("PORT OK");
                    Log.d(WiFiDirectActivity.TAG,"Reply to server");
                    out.flush();
                }catch (IOException e){
                    Log.e(WiFiDirectActivity.TAG,e.getMessage());
                }
            }
            else if(msg.contains("DOWNLOAD"))
            {
                // send data ####### ADD IN CONTROL PATH
                String peer_ip = msg.substring(msg.indexOf("192"), msg.indexOf(":",12));
                int peer_port = Integer.parseInt(msg.substring(msg.indexOf(":",12) + 1));

                Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
                serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
                serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, server_file_uri);
                // target address
                serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS, peer_ip);
                serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, peer_port);
                getActivity().startService(serviceIntent);
                try{
                    out.write("PORT OK");
                    Log.d(WiFiDirectActivity.TAG,"Reply to server");
                    out.flush();
                }catch (IOException e){
                    Log.e(WiFiDirectActivity.TAG,e.getMessage());
                }
            }
            return OTHER;
        }
        public void sendIP(){
            Log.d(WiFiDirectActivity.TAG,"Syn IP set");
            String IPset = "";
            for(Iterator it = peerIP.iterator();it.hasNext();){
                IPset += it.next().toString()+'/';
            }
            Log.d(WiFiDirectActivity.TAG,IPset);
            for(Iterator it = peerIP.iterator(); it.hasNext();){
                //Log.d(WiFiDirectActivity.TAG,"Send IPset to "+it.next().toString());
                Sendthread sendthread = new Sendthread(it.next().toString(),9000,"SYN:"+IPset);
                sendthread.start();
            }

        }
        public void sendPort(String httpuri){

            Log.d(WiFiDirectActivity.TAG, httpuri);
            int time = 0;
            for(Iterator it = peerIP.iterator();it.hasNext();)
            {
                Sendthread msendthread = new Sendthread(it.next().toString(), 9000,"PORT:"+httpuri);
                msendthread.start();
            }
        }
        public void sendDonwloadRequest(String server_ip, String client_ip, int client_dl_port)
        {
            Log.d(WiFiDirectActivity.TAG, "client" + client_ip + "downloadFile from server" + server_ip);
            Sendthread msendthread = new Sendthread(server_ip, 9000,"DOWNLOAD:"+client_ip+":"+client_dl_port);
            msendthread.start();
        }
        public void sendGoodBye(){
            if(currentplayingIP!=null){
                Sendthread msendthread = new Sendthread(currentplayingIP, 9000,"GOODBYE:"+myIP);
                Log.d(WiFiDirectActivity.TAG, "GOODBYE"+myIP);
                msendthread.start();
                currentplayingIP = null;
            }

        }
        public void stop() {
            isRunning = false;
            if (thread == null) {
                Log.e(WiFiDirectActivity.TAG , "Server was stopped without being started.");
                return;
            }
            Log.e(WiFiDirectActivity.TAG, "Stopping server.");
            thread.interrupt();
        }
        public class Listenthread implements Runnable{
            private Socket socket = null;
            public boolean isRunning = false;
            public String connectIP = null;

            public Listenthread(Socket socket,String connectIP){
                this.socket = socket;
                this.connectIP = connectIP;
            }


            @Override
            public void run(){
                String content = null;
                BufferedReader Reader;
                BufferedWriter Writer;
                try {
                    //peerIS = socket.getInputStream();
                    Reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    //peerScanner = new Scanner(peerIS);
                    Writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                    String IP = socket.getRemoteSocketAddress().toString();
                    IP = IP.substring(IP.indexOf('/')+1,IP.indexOf(':'));
                    Log.d(WiFiDirectActivity.TAG, IP);
                    peerIP.add(IP);
                    content = Reader.readLine();
                    Log.d(WiFiDirectActivity.TAG, "Here is the msg: " + content);
                    int response = ProcessRequest(content,Writer);
                    if(response == HELLO) {
                        Writer.write("Hello:" + IP + "\n");
                        Log.d(WiFiDirectActivity.TAG,"Hello:" + IP + "\n");
                        Writer.flush();
                    }
                    while(!socket.isClosed()) {
                        Writer.close();
                        Reader.close();
                        socket.close();
                        Log.d(WiFiDirectActivity.TAG,"socket closes");
                    }
                    if(response==HELLO&&peerIP.size()>1&&info.isGroupOwner)
                    {
                        Log.d(WiFiDirectActivity.TAG,"might be here");
                        sendIP();
                    }
                }catch (IOException e){
                    Log.e(WiFiDirectActivity.TAG,e.getMessage());
                }
            }
        }

    }

}
