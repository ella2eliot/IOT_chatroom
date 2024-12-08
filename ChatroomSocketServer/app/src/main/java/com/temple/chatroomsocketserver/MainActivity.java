package com.temple.chatroomsocketserver;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "ChatServerMain";
    private TextView serverStatus;
    private TextView clientsList;
    private ArrayList<String> clients = new ArrayList<>();
    private ClientHandler handler = new ClientHandler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "MainActivity onCreate called");
        setContentView(R.layout.activity_main);

        serverStatus = findViewById(R.id.server_status);
        clientsList = findViewById(R.id.clients_list);

        // Get and display the server's IP address
        String serverIP = getLocalIpAddress();
        if (serverIP != null) {
            String status = "Server is running on IP: " + serverIP;
            serverStatus.setText(status);
            Log.d(TAG, status);
        } else {
            String status = "Failed to get server IP";
            serverStatus.setText(status);
            Log.e(TAG, status);
        }

        // Start the server service
        try {
            Log.d(TAG, "Attempting to start ServerService");
            Intent serviceIntent = new Intent(this, ServerService.class);
            if (startService(serviceIntent) != null) {
                Log.d(TAG, "ServerService started successfully");
                Toast.makeText(this, "Server service started", Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "Failed to start ServerService");
                Toast.makeText(this, "Failed to start server service", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting ServerService: " + e.getMessage());
            Toast.makeText(this, "Error starting server: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        // Set handler in the service to communicate with UI
        ServerService.setHandler(handler);
        Log.d(TAG, "Handler set in ServerService");
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "MainActivity onDestroy called");
        try {
            Intent serviceIntent = new Intent(this, ServerService.class);
            stopService(serviceIntent);
            Log.d(TAG, "ServerService stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping ServerService: " + e.getMessage());
        }
        super.onDestroy();
    }

    private void updateClientsList() {
        StringBuilder clientsDisplay = new StringBuilder("Connected Clients:\n");
        for (String client : clients) {
            clientsDisplay.append(client).append("\n");
        }
        String display = clientsDisplay.toString();
        clientsList.setText(display);
        Log.d(TAG, "Updated clients list: " + display);
    }

    private class ClientHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            String clientInfo = (String) msg.obj;
            Log.d(TAG, "Handler received message: what=" + msg.what + ", info=" + clientInfo);

            switch (msg.what) {
                case -1: // Error
                    Toast.makeText(MainActivity.this, clientInfo, Toast.LENGTH_LONG).show();
                    serverStatus.setText("Server Error: " + clientInfo);
                    break;
                case 0: // Disconnected client
                    clients.remove(clientInfo);
                    Toast.makeText(MainActivity.this, "Client disconnected: " + clientInfo, Toast.LENGTH_SHORT).show();
                    break;
                case 1: // Connected client
                    clients.add(clientInfo);
                    Toast.makeText(MainActivity.this, "New client connected: " + clientInfo, Toast.LENGTH_SHORT).show();
                    break;
                case 2: // Server status update
                    serverStatus.setText(clientInfo);
                    break;
            }
            updateClientsList();
        }
    }

    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (!ni.isLoopback() && ni.isUp()) {  // Only consider non-loopback, active interfaces
                    Enumeration<InetAddress> addresses = ni.getInetAddresses();
                    for (InetAddress address : Collections.list(addresses)) {
                        if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                            String ip = address.getHostAddress();
                            Log.d(TAG, "Found local IP: " + ip + " on interface: " + ni.getDisplayName());
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting IP address: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}