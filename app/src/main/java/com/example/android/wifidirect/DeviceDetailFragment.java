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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
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
    private final int WARNING = 4;
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
    private DataReceiver dataReceiver;
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {

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
                );

                ((DeviceActionListener) getActivity()).connect(config);
            }
        });
        mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
//                        if (mServer != null) {
//                            Log.d(WiFiDirectActivity.TAG, "HTTP Server stopped without being declared");
//                            handle.obtainMessage(WARNING,"")
//                            mServer.stop();
//                        }
//                        Log.d(WiFiDirectActivity.TAG, "HTTP Server Terminated");
//                        if(controlpath!=null) {
//                            controlpath.stop();
//                        }
                        ((DeviceActionListener) getActivity()).disconnect();
                        resetViews();
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
                        if (mServer != null) {
                            Log.d(WiFiDirectActivity.TAG, "HTTP Server stopped without being declared");
                            mServer.stop();
                            mServer = null;
                        }
                        mContentView.findViewById(R.id.stop_server).setVisibility(View.GONE);
                        ((TextView) mContentView.findViewById(R.id.btn_start_client)).setVisibility(View.VISIBLE);
                        ((TextView) mContentView.findViewById(R.id.status_text)).setText("");
                        listener = 0;
                    }
                }
        );

        return mContentView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(WiFiDirectActivity.TAG, "requestCode is " + Integer.toString(requestCode) + ";resultCode is" + Integer.toString(resultCode));
        switch (requestCode) {
            case CHOOSE_FILE_RESULT_CODE:
                if(resultCode== Activity.RESULT_OK) {
                    Log.d(WiFiDirectActivity.TAG, "File chosen with result code = " + CHOOSE_FILE_RESULT_CODE);
                    // User has picked an image.
                    listener = 0;
                    Uri uri = data.getData();
                    server_file_uri = uri.toString();
                    String server_file_path = getRealPathFromURI(uri);
                    Log.d(WiFiDirectActivity.TAG, "Intent(DeviceDetailFragment)----------- " + server_file_path);

                    // Initiating and start LocalFileStreamingServer
                    mServer = new LocalFileStreamingServer(new File(server_file_path), myIP, controlpath);
                    if (null != mServer && !mServer.isRunning())
                        mServer.start();
                    mContentView.findViewById(R.id.stop_server).setVisibility(View.VISIBLE);
                    mContentView.findViewById(R.id.btn_start_client).setVisibility(View.GONE);
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
        //if(info==null)
        this.info = info;
        //else{
        //    this.info=null;
        //}
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
            if(controlpath==null) {
                controlpath = new Controlpath(info.isGroupOwner, info.groupOwnerAddress.getHostAddress());
                controlpath.start();
            }
            else{
                Log.d(WiFiDirectActivity.TAG," previously declared one");
            }
        }
        if (mServer == null){
            mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);
        }
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
        mContentView.findViewById(R.id.stop_server).setVisibility(View.GONE);
        if(controlpath==null)
            Log.d(WiFiDirectActivity.TAG,"no valid control path");
        else{
            Log.d(WiFiDirectActivity.TAG, "There is a valid control path");
            if(myIP!=null&&myIP.equals("192.168.49.1"))
            {
                handle.obtainMessage(WARNING,"Connection with other peers has failed").sendToTarget();
            }
            else if(mServer ==null){
                handle.obtainMessage(WARNING,"Connection with other peers has failed").sendToTarget();
            }
            else if(mServer!=null){
                mServer.stop();
                handle.obtainMessage(WARNING,"Connection with other peers has failed").sendToTarget();
            }
            controlpath.stop();
        }
        resetdata();
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

    public void resetdata(){
        Log.d(WiFiDirectActivity.TAG, "myIP is exist? " + this.myIP);
        String myIP = null;
        String currentplayingIP = null;
        info = null;
        mServer = null;
        listener = 0 ;
        server_file_uri = null;
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

    /*
     *  receive info from listening thread or controlthread and interact with UI.
     */
    private Handler handle = new Handler(){
        @Override
        public void handleMessage(Message msg){
            switch(msg.what){
                case MSG_IP:
                    //update my IP in device Detail Fragment
                    myIP = (String)msg.obj;
                    break;

                case ACTIVE:
                    if(currentplayingIP==null) {
                        final String uri = (String) msg.obj;
                        //Use regular expression to match IP address
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
                                    if (mSelectedItems.contains(0)) {
                                        Log.d(WiFiDirectActivity.TAG, "Play video");
                                        Intent intent = new Intent(getActivity().getApplicationContext(), VideoViewActivity.class);
                                        intent.setDataAndType(Uri.parse(uri), "video/*");
                                        startActivityForResult(intent, PLAY_VIDEO_RESULT_CODE);
                                    }
                                    if (mSelectedItems.contains(1)) {
                                        // start download server
                                        if(!mSelectedItems.contains(0)) {
                                            Log.d(WiFiDirectActivity.TAG,"only downloading");
                                            controlpath.sendGoodBye();
                                        }
                                        Log.d(WiFiDirectActivity.TAG, "uri=" + uri);
                                        String server_ip = uri.substring(7, uri.indexOf(":", 7));
                                        int port_offset = Integer.parseInt(
                                                uri.substring(18, uri.indexOf(":", 18)));
                                        Log.d(WiFiDirectActivity.TAG, server_ip + "," + port_offset);
                                        controlpath.sendDonwloadRequest(server_ip, myIP, 9000 + port_offset);
                                        Log.d(WiFiDirectActivity.TAG, "Download data");
                                        FileServerAsyncTask task = new FileServerAsyncTask(
                                                getActivity(), 9000 + port_offset);//mContentView.findViewById(R.id.status_text)
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
                    if(listener <= 0)  //if listener's number equal to 0 which means no peer in this group want to watch this video, device will stop the http server
                    {
                        ((TextView) mContentView.findViewById(R.id.status_text)).setText("");
                        ((TextView) mContentView.findViewById(R.id.stop_server)).setVisibility(View.GONE);
                        ((TextView) mContentView.findViewById(R.id.btn_start_client)).setVisibility(View.VISIBLE);
                        if(mServer!=null)
                        {
                            mServer.stop();
                            mServer = null;
                        }
                    }
                    else
                        ((TextView) mContentView.findViewById(R.id.status_text)).setText(Integer.toString(listener) + " peer is playing");
                    break;
                case WARNING:
                    String warning = (String)msg.obj;
                    AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
                    dialog.setTitle("Warning");
                    dialog.setMessage(warning);
                    dialog.setCancelable(false);
                    dialog.setPositiveButton("I Know", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which){}
                        });
                    dialog.show();
                    break;
                default:
                    Log.w(WiFiDirectActivity.TAG, "handleMessage: unexpected msg: " + msg.what);
            }

        }
    };
    /*
    A Thread class that operate sending MSG to specific IP
    Sending model is that waiting for response after sending one MSG.(Blocking model)
    Then, let function ProcessRequest to handle response. In most case, response would be the "PORT OK"
    */
    class Sendthread extends Thread {

        private String IP = null;
        private int port;
        private String msg = null;
        private int retrynum = 10;
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
            }catch(IOException e){
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
                try {
                    Thread.sleep(50);
                    if(--retrynum>0)
                    run();
                    else
                    controlpath.peerIP.remove((String) IP);
                    return;
                }catch (InterruptedException error){
                    Log.e(WiFiDirectActivity.TAG, error.getMessage());
                }
            }
        }
    }


    private class DataReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context,Intent intent){
            int data = intent.getIntExtra("listen",0);
            handle.sendEmptyMessage(MSG_BYE);
        }

    }
}
