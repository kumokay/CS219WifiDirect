// Copyright 2011 Google Inc. All Rights Reserved.

package com.example.android.wifidirect;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.CursorLoader;
import android.net.Uri;
import android.util.Log;
import android.provider.MediaStore;
import android.database.Cursor;

import com.example.streamlocalfile.LocalFileStreamingServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

/**
 * A service that process each file transfer request i.e Intent by opening a
 * socket connection with the WiFi Direct Group Owner and writing the file
 */
public class FileTransferService extends IntentService {

    private static final int SOCKET_TIMEOUT = 5000;
    public static final String ACTION_SEND_FILE = "com.example.android.wifidirect.SEND_FILE";
    public static final String EXTRAS_FILE_PATH = "file_url";
    public static final String EXTRAS_GROUP_OWNER_ADDRESS = "go_host";
    public static final String EXTRAS_GROUP_OWNER_PORT = "go_port";
    LocalFileStreamingServer mServer = null;

    public FileTransferService(String name) {
        super(name);
    }

    public FileTransferService() {
        super("FileTransferService");
    }

    /*
     * (non-Javadoc)
     * @see android.app.IntentService#onHandleIntent(android.content.Intent)
     */
    @Override
    protected void onHandleIntent(Intent intent) {

        Context context = getApplicationContext();
        if (intent.getAction().equals(ACTION_SEND_FILE)) {
            String fileUri = intent.getExtras().getString(EXTRAS_FILE_PATH);
            String host = intent.getExtras().getString(EXTRAS_GROUP_OWNER_ADDRESS);
            Socket socket = new Socket();
            int port = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);

            try {
                /*
                Log.d(WiFiDirectActivity.TAG, "Opening client socket - ");
                socket.bind(null);
                socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);

                Log.d(WiFiDirectActivity.TAG, "Client socket - " + socket.isConnected());

                OutputStream stream = socket.getOutputStream();
                ContentResolver cr = context.getContentResolver();
                InputStream is = null;
                try {
                    is = cr.openInputStream(Uri.parse(fileUri));
                } catch (FileNotFoundException e) {
                    Log.d(WiFiDirectActivity.TAG, e.toString());
                }
                DeviceDetailFragment.copyFile(is, stream);
                Log.d(WiFiDirectActivity.TAG, "Client: Data written");
                */
                Log.d(WiFiDirectActivity.TAG, "Intent (FileTransferService)----------- " + fileUri);


//                try{
//                    Log.e(LocalFileStreamingServer.TAG, "TEST: Attempt to open File as FileInputStream...");
//                    //FileInputStream inputStreamTest = new FileInputStream(new File(fileUri));
//
//                    Log.e(LocalFileStreamingServer.TAG, "TEST: contentUri = " + fileUri);
//                    //ContentResolver cr = context.getContentResolver();
//                    String realPath = getRealPathFromURI(Uri.parse(fileUri));
//                    Log.e(LocalFileStreamingServer.TAG, "TEST: fileUri = " + realPath);
//                    File test = new File(realPath);
//                    FileInputStream inputStreamTest = new FileInputStream(test);
//                    Log.e(LocalFileStreamingServer.TAG, "TEST: Succeeded at opening File as FileInputStream...");
//                }
//                catch(IOException e)
//                {
//                    Log.e(LocalFileStreamingServer.TAG, "TEST: Error opening file", e);
//                }


//                mServer = new LocalFileStreamingServer(new File(getRealPathFromURI(Uri.parse(fileUri))));

                String deviceIp = host;
//                String httpUri = mServer.init(deviceIp);
//                if (null != mServer && !mServer.isRunning())
//                    mServer.start();
//                Log.d(WiFiDirectActivity.TAG, "Streaming----------- " + httpUri);
            } catch (Exception e) {
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
            } finally {
                if (socket != null) {
                    if (socket.isConnected()) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            // Give up
                            e.printStackTrace();
                        }
                    }
                }
            }

        }
    }

    private String getRealPathFromURI(Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        CursorLoader loader = new CursorLoader(getApplicationContext(), contentUri, proj, null, null, null);
        Cursor cursor = loader.loadInBackground();
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String result = cursor.getString(column_index);
        cursor.close();
        return result;
    }
}
