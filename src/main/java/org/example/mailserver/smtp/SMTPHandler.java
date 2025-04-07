package org.example.mailserver.smtp;

import java.io.*;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.example.mailserver.database.DatabaseConnection;

import java.io.*;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.example.mailserver.auth.AuthService;
import org.example.mailserver.database.DatabaseConnection;
public class SMTPHandler extends Thread {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private SMTPState state;
    private String sender;
    private List<String> recipients;
    private StringBuilder emailContent;
    private boolean isDataInProgress;
    private AuthService authService;

    public SMTPHandler(Socket socket) {
        this.socket = socket;
        this.state = SMTPState.INIT;
        this.recipients = new ArrayList<>();
        this.emailContent = new StringBuilder();
        this.isDataInProgress = false;

        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            this.authService = (AuthService) registry.lookup("AuthService");
        } catch (Exception e) {
            System.err.println("Error connecting to AuthService: " + e.getMessage());
        }
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
                if (isDataInProgress) {
                    storeEmail();
                }
                socket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void handleCommand(String command) {
        switch (state) {
            case INIT:
                if (command.startsWith("HELO") || command.startsWith("helo")) {
                    out.println("250 Hello");
                    state = SMTPState.HELO;
                } else if (command.equals("NOOP") || command.startsWith("noop")) {
                    out.println("250 OK");
                } else if (command.startsWith("VRFY ") || command.startsWith("vrfy ")) {
                    handleVrfy(command);
                } else if (command.startsWith("QUIT") || command.startsWith("quit")) {
                    out.println("221 Bye");
                    closeConnection();
                } else {
                    out.println("503 Bad sequence of commands");
                }
                break;

            case HELO:
                if (command.startsWith("MAIL FROM:") || command.startsWith("mail from:")) {
                    handleMailFrom(command);
                } else if (command.startsWith("QUIT") || command.startsWith("quit")) {
                    out.println("221 Bye");
                    closeConnection();
                } else if (command.equals("NOOP") || command.startsWith("noop")) {
                    out.println("250 OK");
                } else if (command.startsWith("VRFY ") || command.startsWith("vrfy ")) {
                    handleVrfy(command);
                } else {
                    out.println("503 Bad sequence of commands");
                }
                break;

            case MAIL_FROM:
                if (command.startsWith("RCPT TO:") || command.startsWith("rcpt to:")) {
                    state = SMTPState.RCPT_TO;
                    handleRcptTo(command);
                } else if (command.startsWith("QUIT") || command.startsWith("quit")) {
                    out.println("221 Bye");
                    closeConnection();
                } else if (command.equals("NOOP") || command.startsWith("noop")) {
                    out.println("250 OK");
                } else if (command.startsWith("VRFY ") || command.startsWith("vrfy ")) {
                    handleVrfy(command);
                } else {
                    out.println("503 Bad sequence of commands");
                }
                break;

            case RCPT_TO:
                if (command.startsWith("RCPT TO:") || command.startsWith("rcpt to:")) {
                    handleRcptTo(command);
                } else if (command.equals("DATA") || command.startsWith("data")) {
                    handleDataCommand();
                } else if (command.startsWith("QUIT") || command.startsWith("quit")) {
                    out.println("221 Bye");
                    closeConnection();
                } else if (command.equals("NOOP") || command.startsWith("noop")) {
                    out.println("250 OK");
                } else if (command.startsWith("VRFY ") || command.startsWith("vrfy ")) {
                    handleVrfy(command);
                } else {
                    out.println("503 Bad sequence of commands");
                }
                break;

            case DATA:
                if (command.equals(".")) {
                    handleEndOfData();
                } else {
                    handleEmailContent(command);
                }
                break;

            case QUIT:
                if (command.startsWith("QUIT") || command.startsWith("quit")) {
                    out.println("221 Bye");
                    closeConnection();
                } else if (command.equals("NOOP") || command.startsWith("noop")) {
                    out.println("250 OK");
                } else if (command.startsWith("VRFY ") || command.startsWith("vrfy ")) {
                    handleVrfy(command);
                } else {
                    out.println("503 Bad sequence of commands");
                }
                break;
        }
    }

    private void handleMailFrom(String command) {
        String email = command.substring(10).trim();
        if (!isValidEmailFormat(email)) {
            out.println("501 Syntax error in parameters or arguments");
        } else {
            sender = extractEmail(email);
            if (isValidSender(sender)) {
                out.println("250 OK");
                state = SMTPState.MAIL_FROM;
            } else {
                out.println("550 No such user");
            }
        }
    }

    private void handleRcptTo(String command) {
        String email = command.substring(8).trim();
        if (!isValidEmailFormat(email)) {
            out.println("501 Syntax error in parameters or arguments");
        } else {
            String recipient = extractEmail(email);
            if (isValidRecipient(recipient)) {
                recipients.add(recipient);
                out.println("250 OK");
            } else {
                out.println("550 No such user");
            }
        }
    }

    private void handleDataCommand() {
        if (recipients.isEmpty()) {
            out.println("503 No valid recipients");
        } else {
            out.println("354 Start mail input; end with <CRLF>.<CRLF>");
            state = SMTPState.DATA;
            isDataInProgress = true;
        }
    }

    private void handleEndOfData() {
        if (emailContent.length() == 0) {
            out.println("554 No message content");
        } else {
            storeEmail();
            out.println("250 OK");
        }
        state = SMTPState.HELO;
        isDataInProgress = false;
    }

    private void handleEmailContent(String command) {
        emailContent.append(command).append("\n");
        if (emailContent.length() > 10 * 1024 * 1024) { // 10 Mo
            out.println("552 Message size exceeds fixed maximum");
            state = SMTPState.QUIT;
            isDataInProgress = false;
            closeConnection();
        }
    }

    private void handleVrfy(String command) {
        String emailOrUser = command.substring(5).trim();
        if (isValidEmailFormat(emailOrUser)) {
            String email = extractEmail(emailOrUser);
            if (isValidRecipient(email)) {
                out.println("250 " + email);
            } else {
                out.println("550 User not found");
            }
        } else {
            String username = emailOrUser.split("@")[0];
            if (userExistsInDatabase(username)) {
                out.println("250 " + username + "@domain.com");
            } else {
                out.println("550 User not found");
            }
        }
    }

    private boolean isValidEmailFormat(String email) {
        return email.matches("<[^<>]+@[^<>]+>");
    }

    private String extractEmail(String email) {
        int start = email.indexOf('<');
        int end = email.indexOf('>');
        if (start != -1 && end != -1) {
            return email.substring(start + 1, end);
        }
        return email;
    }

    private boolean isValidSender(String sender) {
        try {
            String username = sender.split("@")[0];
            return authService.userExists(username);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean isValidRecipient(String recipient) {
        try {
            String username = recipient.split("@")[0];
            return authService.userExists(username);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean userExistsInDatabase(String username) {
        String sql = "SELECT id FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void storeEmail() {
        String sql = "INSERT INTO emails (sender, recipient, subject, content) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String subject = extractSubject(emailContent.toString());
            String content = emailContent.toString();

            for (String recipient : recipients) {
                stmt.setString(1, sender);
                stmt.setString(2, recipient);
                stmt.setString(3, subject);
                stmt.setString(4, content);
                stmt.executeUpdate();
            }

            emailContent.setLength(0);
            recipients.clear();

        } catch (SQLException ex) {
            ex.printStackTrace();
            out.println("451 Requested action aborted: local error in processing");
        }
    }

    private String extractSubject(String emailContent) {
        String[] lines = emailContent.split("\n");
        for (String line : lines) {
            if (line.startsWith("Subject:")) {
                return line.substring(8).trim();
            }
        }
        return "";
    }

    private void closeConnection() {
        try {
            if (isDataInProgress) {
                storeEmail();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}