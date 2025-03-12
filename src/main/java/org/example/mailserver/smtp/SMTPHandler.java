package org.example.mailserver.smtp;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SMTPHandler extends Thread {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private SMTPState state;

    public SMTPHandler(Socket socket) {
        this.socket = socket;
        this.state = SMTPState.INIT;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println("220 Welcome to SMTP Server");

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
            case INIT:
                if (command.startsWith("HELO")) {
                    out.println("250 Hello");
                    state = SMTPState.HELO;
                } else if (command.startsWith("QUIT")) {
                    out.println("221 Bye");
                    state = SMTPState.INIT;
                } else {
                    out.println("503 Bad sequence of commands");
                }
                break;
            case HELO:
                if (command.startsWith("MAIL FROM:")) {
                    out.println("250 OK");
                    state = SMTPState.MAIL_FROM;
                } else if (command.startsWith("QUIT")) {
                    out.println("221 Bye");
                    state = SMTPState.INIT;
                } else {
                    out.println("503 Bad sequence of commands");
                }
                break;
            case MAIL_FROM:
                if (command.startsWith("RCPT TO:")) {
                    out.println("250 OK");
                    state = SMTPState.RCPT_TO;
                } else if (command.startsWith("QUIT")) {
                    out.println("221 Bye");
                    state = SMTPState.INIT;
                } else {
                    out.println("503 Bad sequence of commands");
                }
                break;
            case RCPT_TO:
                if (command.startsWith("RCPT TO:")) {
                    out.println("250 OK");
                } else if (command.equals("DATA")) {
                    out.println("354 Start mail input; end with <CRLF>.<CRLF>");
                    state = SMTPState.DATA;
                } else if (command.startsWith("QUIT")) {
                    out.println("221 Bye");
                    state = SMTPState.INIT;
                } else {
                    out.println("503 Bad sequence of commands");
                }
                break;
            case DATA:
                if (command.equals(".")) {
                    out.println("250 OK");
                    state = SMTPState.QUIT;
                } else {
                    storeEmail(command);
                }
                break;
            case QUIT:
                if (command.startsWith("QUIT")) {
                    out.println("221 Bye");
                    state = SMTPState.INIT;
                } else {
                    out.println("503 Bad sequence of commands");
                }
                break;
        }
    }

    private void storeEmail(String emailContent) {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            Path userDir = Paths.get("mailserver/user1");
            if (!Files.exists(userDir)) {
                Files.createDirectories(userDir);
            }
            Path emailFile = userDir.resolve(timestamp + ".txt");
            Files.write(emailFile, emailContent.getBytes());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}