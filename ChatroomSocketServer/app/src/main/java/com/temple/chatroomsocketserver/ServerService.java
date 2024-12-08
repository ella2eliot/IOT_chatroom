package com.temple.chatroomsocketserver;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.BindException;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ServerService extends Service {
    private static final String TAG = "ChatServer";
    private static final int PORT = 9999;
    private boolean isRunning = false;
    private static Handler handler;
    private final Set<ClientHandler> clientHandlers = new CopyOnWriteArraySet<>();
    private ServerSocket serverSocket;

    public static void setHandler(Handler handler) {
        ServerService.handler = handler;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Server service created");
        new Thread(new ServerThread()).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        Log.d(TAG, "Server service started");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        Log.d(TAG, "Server service being destroyed");
        cleanup();
    }

    private void cleanup() {
        try {
            for (ClientHandler handler : clientHandlers) {
                handler.cleanup();
            }
            clientHandlers.clear();

            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error during cleanup: " + e.getMessage());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class ServerThread implements Runnable {
        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(PORT);
//                Log.d(TAG, "Server started on port " + PORT);
                Log.d(TAG, "Server started on ip " + getLocalIpAddress() + ", port: " + PORT);
                notifyHandler("Server started on ip " + getLocalIpAddress() + ", port: " + PORT, 2);

                while (isRunning) {
                    try {
                        Socket socket = serverSocket.accept();
                        String clientInfo = socket.getInetAddress().toString();
                        Log.d(TAG, "New client connected: " + clientInfo);

                        ClientHandler clientHandler = new ClientHandler(socket);
                        clientHandlers.add(clientHandler);
                        new Thread(clientHandler).start();

                        notifyHandler("Client connected: " + clientInfo, 1);
                    } catch (IOException e) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting client connection: " + e.getMessage());
                        }
                    }
                }
            } catch (BindException e) {
                Log.e(TAG, "Port " + PORT + " already in use");
                notifyHandler("Error: Port " + PORT + " already in use", -1);
            } catch (IOException e) {
                Log.e(TAG, "Error starting server: " + e.getMessage());
                notifyHandler("Error starting server: " + e.getMessage(), -1);
            }
        }
    }

    private void notifyHandler(String message, int what) {
        if (handler != null) {
            Message msg = handler.obtainMessage(what, message);
            handler.sendMessage(msg);
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private boolean isRunning = true;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String message;
                while (isRunning && (message = in.readLine()) != null) {
                    Log.d(TAG, "Received: " + message);
                    broadcast(message, this);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error handling client: " + e.getMessage());
            } finally {
                cleanup();
            }
        }

        public void sendMessage(String message) {
            if (out != null) {
                out.println(message);
            }
        }

        public void cleanup() {
            isRunning = false;
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }

                clientHandlers.remove(this);
                String clientInfo = socket.getInetAddress().toString();
                notifyHandler("Client disconnected: " + clientInfo, 0);
            } catch (IOException e) {
                Log.e(TAG, "Error during client cleanup: " + e.getMessage());
            }
        }
    }

    private void broadcast(String message, ClientHandler sender) {
        for (ClientHandler clientHandler : clientHandlers) {
            if (clientHandler != sender) {
                clientHandler.sendMessage(message);
            }
        }
    }

    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfaces)) {
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                for (InetAddress address : Collections.list(addresses)) {
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) { // Check for IPv4 only
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
