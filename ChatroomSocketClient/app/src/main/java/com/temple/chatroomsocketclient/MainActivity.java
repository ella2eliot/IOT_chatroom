package com.temple.chatroomsocketclient;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.*;
import java.net.Socket;
import java.net.ConnectException;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "ChatClient";
    private static final int SERVER_PORT = 9999;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private TextView chatView;
    private EditText messageInput;
    private EditText ipInput;
    private Button sendButton;
    private Button connectButton;
    private boolean isConnected = false;
    private Thread clientThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chatView = findViewById(R.id.chat_view);
        messageInput = findViewById(R.id.message_input);
        ipInput = findViewById(R.id.ip_input);
        sendButton = findViewById(R.id.send_button);
        connectButton = findViewById(R.id.connect_button);

        // Initially disable send button
        sendButton.setEnabled(false);

        connectButton.setOnClickListener(v -> {
            if (!isConnected) {
                String serverIP = ipInput.getText().toString().trim();
                if (serverIP.isEmpty()) {
                    showToast("Please enter server IP address");
                    return;
                }
                connectButton.setText("Disconnect");
                ipInput.setEnabled(false);
                clientThread = new Thread(new ClientThread(serverIP));
                clientThread.start();
            } else {
                disconnectFromServer();
            }
        });

        sendButton.setOnClickListener(v -> {
            String message = messageInput.getText().toString();
            if (!message.isEmpty() && isConnected) {
                sendMessage(message);
                messageInput.setText("");
            }
        });
    }

    private void disconnectFromServer() {
        isConnected = false;
        cleanup();
        connectButton.setText("Connect");
        ipInput.setEnabled(true);
        sendButton.setEnabled(false);
        chatView.append("Disconnected from server\n");
    }

    private void sendMessage(final String message) {
        new Thread(() -> {
            if (out != null && isConnected) {
                try {
                    out.println(message);
                    runOnUiThread(() -> chatView.append("Me: " + message + "\n"));
                } catch (Exception e) {
                    Log.e(TAG, "Error sending message: " + e.getMessage());
                    runOnUiThread(() -> showToast("Failed to send message"));
                }
            }
        }).start();
    }

    private void showToast(final String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    class ClientThread implements Runnable {
        private final String serverIP;

        ClientThread(String serverIP) {
            this.serverIP = serverIP;
        }

        @Override
        public void run() {
            try {
                Log.d(TAG, "Attempting to connect to " + serverIP + ":" + SERVER_PORT);
                showToast("Connecting to server...");

                socket = new Socket(serverIP, SERVER_PORT);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                isConnected = true;
                Log.d(TAG, "Connected to server successfully");
                showToast("Connected to server");

                runOnUiThread(() -> {
                    sendButton.setEnabled(true);
                    chatView.append("Connected to server\n");
                });

                String message;
                while (isConnected && (message = in.readLine()) != null) {
                    String finalMessage = message;
                    runOnUiThread(() -> chatView.append("Received: " + finalMessage + "\n"));
                }
            } catch (ConnectException e) {
                Log.e(TAG, "Connection refused: " + e.getMessage());
                showToast("Connection refused. Is the server running?");
                runOnUiThread(() -> {
                    disconnectFromServer();
                });
            } catch (IOException e) {
                Log.e(TAG, "Connection error: " + e.getMessage());
                showToast("Connection error: " + e.getMessage());
                runOnUiThread(() -> {
                    disconnectFromServer();
                });
            } finally {
                isConnected = false;
                runOnUiThread(() -> {
                    disconnectFromServer();
                });
            }
        }
    }

    private void cleanup() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            Log.e(TAG, "Error during cleanup: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isConnected = false;
        cleanup();
    }
}
