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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Scanner;
import java.util.WeakHashMap;
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
    private final int SYN = 3;
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
                    public void onClick(View v){
                        controlpath.stop();
                        mContentView.findViewById(R.id.stop_server).setVisibility(View.INVISIBLE);
                    }
                }
        );

        return mContentView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case CHOOSE_FILE_RESULT_CODE:
                Log.d(WiFiDirectActivity.TAG, "File chosen with result code = " + CHOOSE_FILE_RESULT_CODE );
                // User has picked an image.
                Uri uri = data.getData();
                TextView statusText = (TextView) mContentView.findViewById(R.id.status_text);
                statusText.setText("Sending: " + uri);
                Log.d(WiFiDirectActivity.TAG, "Intent(DeviceDetailFragment)----------- " + uri);

                // Initiating and start LocalFileStreamingServer
                mServer = new LocalFileStreamingServer(new File(getRealPathFromURI(uri)), myIP, controlpath);
                //String deviceIp = info.groupOwnerAddress.getHostAddress();
                //        Log.d(WiFiDirectActivity.TAG,"Here is the Httpserver addr: "+httpUri);
                //        if(controlpath!=null) {
                //            controlpath.sendPort(httpUri);
                //            Log.d(WiFiDirectActivity.TAG,"sending url to client");
                //        }
                if (null != mServer && !mServer.isRunning())
                    mServer.start();
                mContentView.findViewById(R.id.stop_server).setVisibility(View.VISIBLE);
                //        Log.d(WiFiDirectActivity.TAG, "Local File Streaming Server Initiated at" + httpUri);


                //        Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
                //        serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
                //        serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, uri.toString());
                //        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
                //                info.groupOwnerAddress.getHostAddress());
                //        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988);
                //        getActivity().startService(serviceIntent);
            case PLAY_VIDEO_RESULT_CODE:
                Log.d(WiFiDirectActivity.TAG, "Video play terminated with result code = " + PLAY_VIDEO_RESULT_CODE);
                controlpath.sendGoodBye();
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
        ((TextView) mContentView.findViewById(R.id.status_text)).setText(getResources()
                .getString(R.string.client_text));
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
                    final String uri = (String)msg.obj;
//                    Pattern pattern = Pattern.compile("(http://|https://){1}[//w//.//-.:]+");
                    //String test = uri.;
                    AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
                    dialog.setTitle(uri.substring(7)+"is sharing video");
                    dialog.setMessage("Want to play it ?");
                    dialog.setCancelable(false);
                    dialog.setPositiveButton("Play", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            Intent intent = new Intent(getActivity().getApplicationContext(), VideoViewActivity.class);
                            intent.setDataAndType(Uri.parse(uri), "video/*");
                            startActivityForResult(intent, PLAY_VIDEO_RESULT_CODE);
                        }
                    });
                    dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog,int which){

                        }
                    });
                    dialog.show();
                    break;
                case MSG_PORT:
                    //Check http server is running or not, preventing fake msg;
                    if(mServer!=null&&!mServer.isRunning()){
                        listener++;
                        Toast.makeText(getActivity(),listener +" received server address ",Toast.LENGTH_LONG).show();
                    }
                    break;
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
            if(msg.contains("Hello:")){
                Log.d(WiFiDirectActivity.TAG,"IP notified");
                return HELLO;
            }
            else if(msg.contains("PORT:")){
                //get portnum from message;
                String url = msg.substring(msg.indexOf("http://"));
                Log.d(WiFiDirectActivity.TAG, url);
                handle.obtainMessage(ACTIVE,url).sendToTarget();
                try{
                    out.write("PORT OK");
                    Log.d(WiFiDirectActivity.TAG,"Reply to server");
                    out.flush();
                }catch (IOException e){
                    Log.e(WiFiDirectActivity.TAG,e.getMessage());
                }
                return OTHER;
            }
            else if(msg.contains("CUT:")){
                Log.d(WiFiDirectActivity.TAG,"Server wanna shut down server");
                //active cut thread , and notifies peer
                return OTHER;
            }
            else if(msg.contains("GOODBYE:")){
                Log.d(WiFiDirectActivity.TAG,"Client has play off");
                //destroy server thread;
                return OTHER;
            }
            else if(msg.contains("PORT OK")){
                handle.obtainMessage(MSG_PORT,true).sendToTarget();
                Log.d(WiFiDirectActivity.TAG,"has received PORT");
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
                //if(myIP==it.next().toString()){
                //    Log.d(WiFiDirectActivity.TAG,"previous set is error");
                //    continue;
                //}
                Sendthread msendthread = new Sendthread(it.next().toString(), 9000,"PORT:"+httpuri);
                msendthread.start();
                Log.d(WiFiDirectActivity.TAG,Integer.toString(time++));
            }
        }

        public void sendGoodBye(){

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
