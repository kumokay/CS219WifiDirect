package com.example.android.wifidirect;

import android.app.Fragment;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Map;

public class TerminalFragment extends Fragment {

    private WifiP2pInfo info = null;
    private ExecutionLayer executionLayer = null;
    private ControlLayer controlLayer = null;
    private ControlLayerInfo controlLayerInfo = null;
    private View mContentView = null;

    private void start_executionLayer()
    {
        if(executionLayer == null)
        {
            executionLayer = new ExecutionLayer(this);
            executionLayer.start();
        }
    }
    private void stop_executionLayer()
    {
        if(executionLayer != null)
        {
            sendCommandAndPrintToTerminalView("exit");
        }
        executionLayer = null;
    }
    private void start_controlLayer()
    {
        if(controlLayer == null)
        {
            controlLayer = new ControlLayer(info.isGroupOwner, info.groupOwnerAddress.getHostAddress(), this);
            controlLayer.start();
        }
    }
    private void stop_controlLayer()
    {
        controlLayer = null;
    }

    public void updateControlLayerInfo(ControlLayerInfo info)
    {
        if(controlLayerInfo == null)
        {
            // initial SYNC, new IP list
            controlLayerInfo = info;
        }
        else if(!controlLayerInfo.equals(info))
        {
            // SYNC for update IP list, compare if info has been changed
            controlLayerInfo = info;
            if(executionLayer != null)
            {
                sendCommandAndPrintToTerminalView("hd-daemon update " + controlLayerInfo_peerIPList());
            }
        }
    }

    private String controlLayerInfo_peerIPList()
    {
        // return NUM_OF_IPs IP1 IP2 IP3...IPn
        String ip_list = String.valueOf(controlLayerInfo.peerIP.size()-1); // do not count GO
        for (Map.Entry<String, IPStatus> entry : controlLayerInfo.peerIP.entrySet())
        {
            if(!entry.getKey().equals(controlLayerInfo.goIP))
            {
                // print all IPs but GO's IP
                ip_list += " " + entry.getKey();
            }

        }
        return ip_list;
    }

    public void onDestroyView()
    {
        super.onDestroyView();
        this.stop_controlLayer();
        this.stop_executionLayer();
    }

    public void disableTerminal()
    {
        mContentView.setVisibility(View.GONE);
        this.stop_controlLayer();
        this.stop_executionLayer();
        final EditText editText_cmd = (EditText) mContentView.findViewById(R.id.editText_Command);
        editText_cmd.setText("");
        final TextView textView_terminal = (TextView) mContentView.findViewById(R.id.textView_Terminal);
        textView_terminal.setText("");
        this.info = null;
    }

    public void enableTerminal(WifiP2pInfo info) {
        mContentView.setVisibility(View.VISIBLE);

        this.info = info;
        this.start_controlLayer();
        this.start_executionLayer();

        final Button btn_send = (Button) mContentView.findViewById(R.id.button_Send);
        final Button btn_clear = (Button) mContentView.findViewById(R.id.button_Clear);
        final Button btn_ipSync = (Button) mContentView.findViewById(R.id.button_IpSync);
        final Button btn_startStopHadoop = (Button) mContentView.findViewById(R.id.button_startStopHadoop);
        final EditText editText_cmd = (EditText) mContentView.findViewById(R.id.editText_Command);
        final TextView textView_terminal = (TextView) mContentView.findViewById(R.id.textView_Terminal);
        textView_terminal.setMaxLines(30);
        textView_terminal.setVerticalScrollBarEnabled(true);
        textView_terminal.setGravity(Gravity.BOTTOM);
        textView_terminal.setMovementMethod(new ScrollingMovementMethod());

        btn_startStopHadoop.setText("Start Hadoop");

        btn_send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String command = editText_cmd.getText().toString();
                sendCommandAndPrintToTerminalView(command);
            }
        });
        btn_clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                editText_cmd.setText("");
            }
        });
        btn_ipSync.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // for each entity in this group, including GP and peers, start sync IP
                controlLayer.sendMessage("START", controlLayer.goIP);
            }
        });
        btn_startStopHadoop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(btn_startStopHadoop.getText().equals("Start Hadoop"))
                {
                    start_executionLayer(); // in case of execution layer is not running
                    sendCommandAndPrintToTerminalView("./data/bootlinux.sh /data/ubuntu-14.04.img");
                    if(controlLayerInfo.isOwner == true)
                    {
                        sendCommandAndPrintToTerminalView("hd-daemon startmaster " + controlLayerInfo_peerIPList());
                    }
                    else
                    {
                        sendCommandAndPrintToTerminalView("hd-daemon startslave " + controlLayerInfo_peerIPList());
                    }
                    btn_startStopHadoop.setText("Stop Hadoop");
                }
                else // stop hadoop
                {
                    if(controlLayerInfo.isOwner == true)
                    {
                        sendCommandAndPrintToTerminalView("hd-daemon stopmaster");
                    }
                    else
                    {
                        sendCommandAndPrintToTerminalView("hd-daemon stopslave");
                    }
                    btn_startStopHadoop.setText("Start Hadoop");
                }
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {

        mContentView = inflater.inflate(R.layout.terminal_frag, null);
        mContentView.setVisibility(View.GONE);

        return mContentView;
    }

    public void writeResultToTerminal(String result) {
        final String str = result;
        getActivity().runOnUiThread(new Runnable() {
            public void run() {
                final TextView textView_terminal = (TextView) mContentView.findViewById(R.id.textView_Terminal);
                textView_terminal.append(str);
            }
        });
    }

    private void sendCommandAndPrintToTerminalView(String command)
    {
        final TextView textView_terminal = (TextView) mContentView.findViewById(R.id.textView_Terminal);
        writeResultToTerminal("cmd> " + command + "\n");
        executionLayer.executeCommand(command);
    }


}
