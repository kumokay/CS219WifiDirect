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
import java.util.Scanner;

import com.example.streamlocalfile.LocalFileStreamingServer;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements ConnectionInfoListener {

    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    private View mContentView = null;
    private WifiP2pDevice device;
    private WifiP2pInfo info;
    private Socket peerSocket = null;
    private String myIP = null;
    private String peerIP = null;
    private ProgressDialog progressDialog = null;
    private LocalFileStreamingServer mServer = null;

//    private Socket peerSocket;
//    private Socket mySocket;
    private InputStream peerIS;
    private OutputStream peerOS;
//    private PrintStream peerPrintStream;
//    private Scanner peerScanner;
    private BufferedReader peerReader;
    private BufferedWriter peerWriter;

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

        return mContentView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        // User has picked an image.
        Uri uri = data.getData();
        TextView statusText = (TextView) mContentView.findViewById(R.id.status_text);
        statusText.setText("Sending: " + uri);
        //Log.d(WiFiDirectActivity.TAG, "Intent(DeviceDetailFragment)----------- " + uri);

        // Initiating and start LocalFileStreamingServer
        mServer = new LocalFileStreamingServer(new File(getRealPathFromURI(uri)), myIP, peerWriter);

        //String deviceIp = info.groupOwnerAddress.getHostAddress();
//        String httpUri = mServer.init(myIP, peerWriter);
        if (null != mServer && !mServer.isRunning())
            mServer.start();
//        Log.d(WiFiDirectActivity.TAG, "Local File Streaming Server Initiated at" + httpUri);


//        Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
//        serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
//        serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, uri.toString());
//        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
//                info.groupOwnerAddress.getHostAddress());
//        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988);
//        getActivity().startService(serviceIntent);
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

        if (info.groupFormed && info!=null) {
            exchangeIP();
//            new StreamingAsyncTask(getActivity(), mContentView.findViewById(R.id.status_text), peerReader)
//                    .execute();

            if(info.isGroupOwner) {

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
        // hide the connect button
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
                case 0:
                    if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                    }
                    break;
            }
//            new StreamingAsyncTask(getActivity(), mContentView.findViewById(R.id.status_text), peerReader)
//                    .execute();
        }
    };
    public void exchangeIP(){
//        OutputStream outputStream = null;
//        InputStream inputStream = null;
        Log.d(WiFiDirectActivity.TAG,"exchange?");
        new Thread() {
            @Override
            public void run() {
                Socket socket = null;
                if (info.isGroupOwner) {
                    //group owner
                    try {
                        ServerSocket serverSocket = new ServerSocket(9000);
                        Log.d(WiFiDirectActivity.TAG, "My IP" + info.groupOwnerAddress.getHostAddress());
                        socket = serverSocket.accept();
                        peerSocket = socket;
                        Log.d(WiFiDirectActivity.TAG, "exServer: connection done");

                        //peerOS = socket.getOutputStream();
                        peerWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                        //peerPrintStream = new PrintStream(peerOS);
                        peerReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                        peerWriter.write(socket.getRemoteSocketAddress().toString()+"\n");
                        peerWriter.flush();
                        String line = socket.getRemoteSocketAddress().toString();
                        Log.d(WiFiDirectActivity.TAG, "Peer IP addr: " + line);
                        myIP = info.groupOwnerAddress.getHostAddress();
                        peerIP = line.substring(1, line.indexOf(':'));
                        //writer.close();
                        //socket.close();
                        Log.d(WiFiDirectActivity.TAG, "Exchange Completed: " +
                                "myIP = " + myIP + ", peerIP = "  + peerIP);



                    } catch (IOException e) {
                        Log.e(WiFiDirectActivity.TAG, e.getMessage());
                    }

                } else {
                    //peer
                    try {
                        Thread.sleep(500);
                        socket = new Socket();
                        Log.d(WiFiDirectActivity.TAG, "Opening control socket - ");
                        socket.bind(null);
                        peerSocket = socket;
                        socket.connect((new InetSocketAddress(info.groupOwnerAddress.getHostAddress(), 9000)), 5000);
                        Log.d(WiFiDirectActivity.TAG, info.groupOwnerAddress.getHostAddress());
                        Log.d(WiFiDirectActivity.TAG, "Connection control socket");

                        //peerIS = socket.getInputStream();
                        peerReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        //peerScanner = new Scanner(peerIS);
                        peerWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                        Log.d(WiFiDirectActivity.TAG, "Attempt to read line");
                        String line = peerReader.readLine();
                        myIP = line.substring(1, line.indexOf(':'));
                        peerIP = info.groupOwnerAddress.getHostAddress();
                        //bff.close();
                        Log.d(WiFiDirectActivity.TAG, "Exchange Completed: " +
                                "myIP = " + myIP + ", peerIP = " + peerIP);



                    } catch (InterruptedException e) {
                        Log.e(WiFiDirectActivity.TAG, e.getMessage());
                    } catch (Exception e) {
                        Log.e(WiFiDirectActivity.TAG, e.getMessage());
                    }

                }

                try {
                    // Exchange completed
                    handle.sendEmptyMessage(0);
                    Log.d(WiFiDirectActivity.TAG, "Dialog dismissed");

                    String url = peerReader.readLine();
                    Log.d(WiFiDirectActivity.TAG, "HTTP Server IP Address: " + url);

                    //Intent intent = new Intent(Intent.ACTION_VIEW);
                    //intent.setDataAndType(Uri.parse(url), "video/*");
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    Log.d(WiFiDirectActivity.TAG, "Intent sent");
                }
                catch (Exception e) {
                    Log.e(WiFiDirectActivity.TAG, "Error: " + e.getMessage());
                }
            }

        }.start();
    }
}
