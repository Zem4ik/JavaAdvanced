package ru.ifmo.ctddev.Zemtsov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.*;

/**
 * Created by vlad on 16.04.17.
 */
public class HelloUDPClient implements HelloClient {

    public static void main(String[] args) {
        if (args != null && args.length == 5) {
            String host;
            int port;
            String prefix;
            int requests;
            int threads;
            for (String string : args) {
                if (string == null) {
                    System.err.println("Wrong input arguments");
                    printUsageMessage();
                }
            }
            host = args[0];
            prefix = args[2];
            try {
                port = Integer.parseInt(args[1]);
                threads = Integer.parseInt(args[3]);
                requests = Integer.parseInt(args[4]);
                new HelloUDPClient().start(host, port, prefix, requests, threads);
            } catch (NumberFormatException e) {
                System.err.println("Number expected at 1st, 3rd and 4th arguments");
                printUsageMessage();
            }
        } else {
            printUsageMessage();
        }
    }

    private static void printUsageMessage() {
        System.err.println("Usage: java HelloUDPClient <host> <port> <prefix> <threads> <requests>");
    }

    @Override
    public void start(String host, int port, String prefix, int requests, int threads) {
        Queue<Future<Void>> futures = new ArrayDeque<>();
        try {
            InetAddress inetAddress = InetAddress.getByName(host);
            ExecutorService executorService = Executors.newFixedThreadPool(threads);
            for (int i = 0; i < threads; ++i) {
                int finalI = i;
                Callable<Void> callable = () -> {
                    try (DatagramSocket datagramSocket = new DatagramSocket()) {
                        datagramSocket.setSoTimeout(500);
                        byte[] buf = new byte[256];
                        DatagramPacket inPacket = new DatagramPacket(buf, buf.length);
                        for (int j = 0; j < requests && !Thread.currentThread().isInterrupted(); ++j) {
                            String messageString = prefix + Integer.toString(finalI) + "_" + Integer.toString(j);
                            byte[] messageBytes = messageString.getBytes();
                            DatagramPacket packet = new DatagramPacket(messageBytes, messageBytes.length, inetAddress, port);
                            datagramSocket.send(packet);

                            boolean flag = true;
                            while (flag && !Thread.currentThread().isInterrupted()) {
                                try {
                                    datagramSocket.receive(inPacket);
                                    String receivedMessage = new String(inPacket.getData(), 0, inPacket.getLength(), Charset.forName("UTF-8"));
                                    if (receivedMessage.equals("Hello, " + messageString)) {
                                        System.out.println(receivedMessage);
                                        flag = false;
                                    }
                                } catch (SocketTimeoutException | NumberFormatException e) {
                                    datagramSocket.send(packet);
                                }
                            }
                        }
                    }
                    return null;
                };
                futures.add(executorService.submit(callable));
                while (!futures.isEmpty()) {
                    Future<Void> future = futures.poll();
                    future.get();
                }
            }
            executorService.shutdownNow();
        } catch (UnknownHostException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
