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
import android.content.CursorLoader;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.example.android.wifidirect.DeviceListFragment.DeviceActionListener;



/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements ConnectionInfoListener {

    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    protected static final int PLAY_VIDEO_RESULT_CODE = 21;

    private View mContentView = null;
    private WifiP2pDevice device;
    private WifiP2pInfo info;
    private ProgressDialog progressDialog = null;
    //private ControlLayer controlLayer = null;
    //private ExecutionLayer executionLayer = null;


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

                        ((DeviceActionListener) getActivity()).disconnect();
                        resetViews();
                    }
                });
        /*mContentView.findViewById(R.id.btn_start_terminal).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        //terminalView_intent = new Intent(getActivity(), TerminalFragment.class);
                        //terminalView_intent.putExtra("WifiP2pInfo", info); // pass Parcelable object to activity
                        //getActivity().startActivity(terminalView_intent);
                        TerminalFragment fragment = (TerminalFragment) getFragmentManager()
                                .findFragmentById(R.id.frag_terminal);

                    }
                });*/
/*
        mContentView.findViewById(R.id.btn_start_hadoop).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // Start Hadoop client or master mode
//                        if (executionLayer == null){
//                            executionLayer = new ExecutionLayer();
//                            executionLayer.start();
//                       }

                        if(info.isGroupOwner){
                            // Launch Hadoop as Master node
                            // exec(./hd-daemon startmaster 1 ip);

//                            executionLayer.executeCommand("sh /data/bootubuntu.sh /data/ubuntu-14.04.img");
//                            executionLayer.executeCommand("uname");


                        } else {
                            // Launch Hadoop as Client node

//                            executionLayer.executeCommand("sh /data/bootubuntu.sh /data/ubuntu-14.04.img");
//                            executionLayer.executeCommand("uname");
                        }
                        controlLayer.sendMessage("START", controlLayer.goIP);

                        mContentView.findViewById(R.id.btn_start_hadoop).setVisibility(View.GONE);
                        mContentView.findViewById(R.id.btn_stop_hadoop).setVisibility(View.VISIBLE);

//
//                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
//                        intent.setType("video/*");
//                        startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
                    }
                });

        mContentView.findViewById(R.id.btn_stop_hadoop).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Stop Execution Layer
                        if (executionLayer != null){
                            executionLayer.stop();
                            executionLayer = null;
                        }

                        mContentView.findViewById(R.id.btn_stop_hadoop).setVisibility(View.GONE);
                        mContentView.findViewById(R.id.btn_start_hadoop).setVisibility(View.VISIBLE);
                    }
                }
        );
*/
        return mContentView;
    }

//    @Override
//    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//        Log.d(WiFiDirectActivity.TAG, "requestCode is " + Integer.toString(requestCode) + ";resultCode is" + Integer.toString(resultCode));
//        switch (requestCode) {
//            case CHOOSE_FILE_RESULT_CODE:
//                if(resultCode== Activity.RESULT_OK) {
//                    Log.d(WiFiDirectActivity.TAG, "File chosen with result code = " + CHOOSE_FILE_RESULT_CODE);
//                    // User has picked an image.
//                    listener = 0;
//                    Uri uri = data.getData();
//                    server_file_uri = uri.toString();
//                    String server_file_path = getRealPathFromURI(uri);
//                    Log.d(WiFiDirectActivity.TAG, "Intent(DeviceDetailFragment)----------- " + server_file_path);
//
//                    // Initiating and start LocalFileStreamingServer
//                    mServer = new LocalFileStreamingServer(new File(server_file_path), myIP, controlpath);
//                    if (null != mServer && !mServer.isRunning())
//                        mServer.start();
//                    mContentView.findViewById(R.id.stop_server).setVisibility(View.VISIBLE);
//                    mContentView.findViewById(R.id.btn_start_client).setVisibility(View.GONE);
//                }
//                break;
//            case PLAY_VIDEO_RESULT_CODE:
//                if(resultCode==Activity.RESULT_CANCELED) {
//                    Log.d(WiFiDirectActivity.TAG, "Video play terminated with result code = " + Integer.toString(requestCode));
//                    controlpath.sendGoodBye();
//                }
//                break;
//            default:
//                Log.d(WiFiDirectActivity.TAG, "unknown result code=" + resultCode);
//        }
//    }

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
                + ((info.isGroupOwner) ? getResources().getString(R.string.yes)
                : getResources().getString(R.string.no)));

        // InetAddress from WifiP2pInfo struct.
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText("Group Owner IP - " + info.groupOwnerAddress.getHostAddress());

        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.

//        final Handler handler = new Handler(){
//
//            private static final int MASTER = 1;
//
//            @Override
//            public void handleMessage(Message msg) {
//                switch(msg.what){
//                    case MASTER:
//                        mContentView.findViewById(R.id.btn_start_hadoop).setEnabled(true);
//                        break;
//                    default:
//                        Log.e(WiFiDirectActivity.TAG, "Error: unexpected message in handler");
//                }
//            }
//        };

        if (info.groupFormed) {
            // kumokay: move control layer to terminal activity
            /*if (controlLayer == null){
                controlLayer = new ControlLayer(info.isGroupOwner, info.groupOwnerAddress.getHostAddress());
                controlLayer.start();
            }*/
            //mContentView.findViewById(R.id.btn_start_terminal).setVisibility(View.VISIBLE);
            TerminalFragment fragment = (TerminalFragment) getFragmentManager()
                    .findFragmentById(R.id.frag_terminal);
            fragment.enableTerminal(this.info);
        }

        /*mContentView.findViewById(R.id.btn_start_hadoop).setVisibility(View.VISIBLE);
        if (info.isGroupOwner)
            mContentView.findViewById(R.id.btn_start_hadoop).setEnabled(true);
        else
            mContentView.findViewById(R.id.btn_start_hadoop).setEnabled(false);*/

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
        //mContentView.findViewById(R.id.btn_start_terminal).setVisibility(View.GONE);

        resetdata();
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.status_text);
        view.setText(R.string.empty);

        /*mContentView.findViewById(R.id.btn_start_hadoop).setVisibility(View.GONE);*/

        this.getView().setVisibility(View.GONE);

        TerminalFragment fragment = (TerminalFragment) getFragmentManager()
                .findFragmentById(R.id.frag_terminal);
        fragment.disableTerminal();
    }

    public void resetdata(){
        String myIP = null;
        info = null;
        Log.d(WiFiDirectActivity.TAG, "Data Reset");
    }
}
