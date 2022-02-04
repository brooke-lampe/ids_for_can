package com.network;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VirtualCAN {

    public static void getInterfaces() {
        try {
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();

            while (e.hasMoreElements()) {
                NetworkInterface ni = e.nextElement();
                System.out.println("Net interface: " + ni.getName() + " - " + ni.getDisplayName());

                Enumeration<InetAddress> e2 = ni.getInetAddresses();

                while (e2.hasMoreElements()) {
                    InetAddress ip = e2.nextElement();
                    System.out.println("IP address: " + ip.toString());
                }
                System.out.println();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void getVCAN0() {
        NetworkInterface nif;

        try {
            nif = NetworkInterface.getByName("vcan0");
            Enumeration<InetAddress> nifAddresses = nif.getInetAddresses();

            while (nifAddresses.hasMoreElements()) {
                InetAddress ip = nifAddresses.nextElement();
                System.out.println("IP address: " + ip.toString());
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    static void printHardwareAddresses() throws SocketException {
        if (System.getProperty("os.name").equals("Linux")) {

            // Read all available device names
            List<String> devices = new ArrayList<>();
            Pattern pattern = Pattern.compile("^ *(.*):");
            try (FileReader reader = new FileReader("/proc/net/dev")) {
                BufferedReader in = new BufferedReader(reader);
                String line = null;
                while( (line = in.readLine()) != null) {
                    Matcher m = pattern.matcher(line);
                    if (m.find()) {
                        devices.add(m.group(1));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // read the hardware address for each device
            for (String device : devices) {
                try (FileReader reader = new FileReader("/sys/class/net/" + device + "/address")) {
                    BufferedReader in = new BufferedReader(reader);
                    String addr = in.readLine();

                    System.out.println(String.format("%5s: %s", device, addr));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        } else {
            // use standard API for Windows & Others (need to test on each platform, though!!)
        }
    }

    public static void main(String[] args) {
        System.out.println("List all network interfaces example");
        System.out.println();
        getInterfaces();
        try {
            printHardwareAddresses();
        } catch (SocketException e) {
            e.printStackTrace();
        }

        System.out.println();
        getVCAN0();

        NetworkInterface nif;

        try {
            nif = NetworkInterface.getByName("vcan0");
            Enumeration<InetAddress> nifAddresses = nif.getInetAddresses();
            InetAddress ip = nifAddresses.nextElement();
            Socket socket = new Socket();
            socket.bind(new InetSocketAddress(ip, 0));
            socket.connect(new InetSocketAddress(ip, 0));
            System.out.println(socket.getOutputStream());
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}