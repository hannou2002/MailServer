package org.example.mailserver.pop3;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class POP3Handler extends Thread {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private POP3State state;
    private String user;

    public POP3Handler(Socket socket) {
        this.socket = socket;
        this.state = POP3State.AUTHORIZATION;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println("+OK POP3 server ready");

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Received: " + inputLine);
                handleCommand(inputLine);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void handleCommand(String command) {
        switch (state) {
            case AUTHORIZATION:
                if (command.startsWith("USER ")) {
                    user = command.substring(5);
                    out.println("+OK");
                    state = POP3State.TRANSACTION;
                } else {
                    out.println("-ERR");
                }
                break;
            case TRANSACTION:
                if (command.startsWith("PASS ")) {
                    out.println("+OK");
                    state = POP3State.UPDATE;
                } else if (command.equals("STAT")) {
                    out.println("+OK 0 0");
                } else if (command.equals("LIST")) {
                    out.println("+OK");
                    out.println(".");
                } else if (command.startsWith("RETR ")) {
                    out.println("+OK");
                    out.println(".");
                } else if (command.startsWith("DELE ")) {
                    out.println("+OK");
                } else if (command.equals("QUIT")) {
                    out.println("+OK");
                    state = POP3State.AUTHORIZATION;
                } else {
                    out.println("-ERR");
                }
                break;
            case UPDATE:
                if (command.equals("QUIT")) {
                    out.println("+OK");
                    state = POP3State.AUTHORIZATION;
                } else {
                    out.println("-ERR");
                }
                break;
        }
    }
}