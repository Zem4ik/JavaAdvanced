package ru.ifmo.ctddev.Zemtsov.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by vlad on 16.04.17.
 */
public class HelloUDPServer implements HelloServer {
    private DatagramSocket datagramSocket;
    private ExecutorService executorService;

    public static void main(String[] args) {
        if (args != null && args.length == 5 && args[0] != null && args[1] != null) {
            int port;
            int threads;
            try {
                port = Integer.parseInt(args[0]);
                threads = Integer.parseInt(args[1]);
                new HelloUDPServer().start(port, threads);
            } catch (NumberFormatException e) {
                System.err.println("Number expected at 1st and 2nd arguments");
                printUsageMessage();
            }
        } else {
            printUsageMessage();
        }
    }

    private static void printUsageMessage() {
        System.err.println("Usage: java HelloUDPServer <port> <threads>");
    }

    @Override
    public void start(int port, int threads) {
        try {
            datagramSocket = new DatagramSocket(port);
            executorService = Executors.newFixedThreadPool(threads);

            for (int i = 0; i < threads; ++i) {
                Runnable runnable = () -> {
                    byte[] buf = new byte[256];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            datagramSocket.receive(packet);
                            String message = "Hello, " + new String(packet.getData(), 0, packet.getLength(), Charset.forName("UTF-8"));
                            datagramSocket.send(new DatagramPacket(message.getBytes(), 0, message.getBytes().length, packet.getSocketAddress()));
                        } catch (IOException e) {
                            //e.printStackTrace();
                        }
                    }
                };
                executorService.submit(runnable);
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        executorService.shutdownNow();
        datagramSocket.close();
    }
}
