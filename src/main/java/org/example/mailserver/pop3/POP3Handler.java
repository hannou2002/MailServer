package org.example.mailserver.pop3;

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
public class POP3Handler extends Thread {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private POP3State state;
    private String user;
    private List<Email> emails;
    private List<Boolean> deletedFlags;
    private AuthService authService;

    public POP3Handler(Socket socket) {
        this.socket = socket;
        this.state = POP3State.AUTHORIZATION;
        this.emails = new ArrayList<>();
        this.deletedFlags = new ArrayList<>();

        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            this.authService = (AuthService) registry.lookup("AuthService");
        } catch (Exception e) {
            System.err.println("Error connecting to AuthService: " + e.getMessage());
        }
    }

    private static class Email {
        private int id;
        private String sender;
        private String recipient;
        private String subject;
        private String content;
        private Timestamp sentAt;
        private int size;

        public Email(int id, String sender, String recipient, String subject,
                     String content, Timestamp sentAt, int size) {
            this.id = id;
            this.sender = sender;
            this.recipient = recipient;
            this.subject = subject;
            this.content = content;
            this.sentAt = sentAt;
            this.size = size;
        }

        // Getters
        public int getId() { return id; }
        public String getSender() { return sender; }
        public String getRecipient() { return recipient; }
        public String getSubject() { return subject; }
        public String getContent() { return content; }
        public Timestamp getSentAt() { return sentAt; }
        public int getSize() { return size; }
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
                handleAuthorization(command);
                break;
            case TRANSACTION:
                handleTransaction(command);
                break;
            case UPDATE:
                handleUpdate(command);
                break;
        }
    }

    private void handleAuthorization(String command) {
        if (command.startsWith("USER ")) {
            user = command.substring(5).trim();
            if (isValidUser(user)) {
                out.println("+OK User accepted");
            } else {
                out.println("-ERR Unknown user");
            }
        }
        else if (command.startsWith("PASS ")) {
            String password = command.substring(5).trim();
            if (isValidPassword(user, password)) {
                out.println("+OK Mailbox locked and ready");
                state = POP3State.TRANSACTION;
                loadEmails();
            } else {
                out.println("-ERR Invalid password");
            }
        }
        else if (command.equals("QUIT")) {
            out.println("+OK POP3 server signing off");
            closeConnection();
        }
        else {
            out.println("-ERR Unknown command");
        }
    }

    private void handleTransaction(String command) {
        if (command.equals("STAT")) {
            handleStat();
        }
        else if (command.equals("LIST")) {
            handleList();
        }
        else if (command.startsWith("RETR ")) {
            handleRetr(command);
        }
        else if (command.startsWith("DELE ")) {
            handleDele(command);
        }
        else if (command.equals("NOOP")) {
            out.println("+OK");
        }
        else if (command.startsWith("UIDL")) {
            handleUidl(command);
        }
        else if (command.startsWith("TOP ")) {
            handleTop(command);
        }
        else if (command.equals("RSET")) {
            handleRset();
        }
        else if (command.equals("QUIT")) {
            handleQuit();
        }
        else {
            out.println("-ERR Unknown command");
        }
    }

    private void handleUpdate(String command) {
        if (command.equals("QUIT")) {
            handleQuit();
        } else {
            out.println("-ERR Unknown command");
        }
    }

    private boolean isValidUser(String email) {
        try {
            String username = extractUsernameFromEmail(email);
            return username != null && authService.userExists(username);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean isValidPassword(String email, String password) {
        try {
            String username = extractUsernameFromEmail(email);
            return username != null && authService.authenticate(username, password);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String extractUsernameFromEmail(String email) {
        if (!email.matches("[^@]+@[^@]+")) {
            return null;
        }
        return email.split("@")[0];
    }

    private void loadEmails() {
        emails.clear();
        deletedFlags.clear();

        String sql = "SELECT id, sender, recipient, subject, content, sent_at, LENGTH(content) as size " +
                "FROM emails WHERE recipient = ? AND is_deleted = FALSE ORDER BY sent_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Email email = new Email(
                            rs.getInt("id"),
                            rs.getString("sender"),
                            rs.getString("recipient"),
                            rs.getString("subject"),
                            rs.getString("content"),
                            rs.getTimestamp("sent_at"),
                            rs.getInt("size")
                    );
                    emails.add(email);
                    deletedFlags.add(false);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            out.println("-ERR Error loading messages");
        }
    }

    private void handleStat() {
        int count = 0;
        int totalSize = 0;

        for (int i = 0; i < emails.size(); i++) {
            if (!deletedFlags.get(i)) {
                count++;
                totalSize += emails.get(i).getSize();
            }
        }

        out.println("+OK " + count + " " + totalSize);
    }

    private void handleList() {
        if (emails.isEmpty()) {
            out.println("+OK 0 messages");
            out.println(".");
            return;
        }

        out.println("+OK " + emails.size() + " messages");
        for (int i = 0; i < emails.size(); i++) {
            if (!deletedFlags.get(i)) {
                out.println((i + 1) + " " + emails.get(i).getSize());
            }
        }
        out.println(".");
    }

    private void handleRetr(String command) {
        try {
            int messageNumber = Integer.parseInt(command.substring(5).trim()) - 1;
            if (messageNumber >= 0 && messageNumber < emails.size() && !deletedFlags.get(messageNumber)) {
                Email email = emails.get(messageNumber);
                out.println("+OK " + email.getSize() + " octets");
                out.println("From: " + email.getSender());
                out.println("To: " + email.getRecipient());
                out.println("Subject: " + email.getSubject());
                out.println("Date: " + email.getSentAt());
                out.println();
                out.println(email.getContent());
                out.println(".");
            } else {
                out.println("-ERR No such message");
            }
        } catch (NumberFormatException ex) {
            out.println("-ERR Invalid message number");
        }
    }

    private void handleDele(String command) {
        try {
            int messageNumber = Integer.parseInt(command.substring(5).trim()) - 1;
            if (messageNumber >= 0 && messageNumber < emails.size() && !deletedFlags.get(messageNumber)) {
                deletedFlags.set(messageNumber, true);
                out.println("+OK Message " + (messageNumber + 1) + " marked for deletion");
            } else {
                out.println("-ERR No such message");
            }
        } catch (NumberFormatException ex) {
            out.println("-ERR Invalid message number");
        }
    }

    private void handleUidl(String command) {
        String[] parts = command.split(" ");
        try {
            if (parts.length == 1) {
                out.println("+OK");
                for (int i = 0; i < emails.size(); i++) {
                    if (!deletedFlags.get(i)) {
                        out.println((i + 1) + " " + emails.get(i).getId());
                    }
                }
                out.println(".");
            }
            else if (parts.length == 2) {
                int messageNumber = Integer.parseInt(parts[1]) - 1;
                if (messageNumber >= 0 && messageNumber < emails.size() && !deletedFlags.get(messageNumber)) {
                    out.println("+OK " + (messageNumber + 1) + " " + emails.get(messageNumber).getId());
                } else {
                    out.println("-ERR No such message");
                }
            }
        } catch (NumberFormatException ex) {
            out.println("-ERR Invalid message number");
        }
    }

    private void handleTop(String command) {
        try {
            String[] parts = command.split(" ");
            if (parts.length != 3) {
                out.println("-ERR Invalid syntax");
                return;
            }

            int messageNumber = Integer.parseInt(parts[1]) - 1;
            int numLines = Integer.parseInt(parts[2]);

            if (messageNumber < 0 || messageNumber >= emails.size() || deletedFlags.get(messageNumber)) {
                out.println("-ERR No such message");
                return;
            }

            Email email = emails.get(messageNumber);
            String[] lines = email.getContent().split("\n");

            out.println("+OK");
            out.println("From: " + email.getSender());
            out.println("To: " + email.getRecipient());
            out.println("Subject: " + email.getSubject());
            out.println("Date: " + email.getSentAt());
            out.println();

            int linesToShow = Math.min(numLines, lines.length);
            for (int i = 0; i < linesToShow; i++) {
                out.println(lines[i]);
            }
            out.println(".");
        } catch (NumberFormatException ex) {
            out.println("-ERR Invalid message number");
        }
    }

    private void handleRset() {
        for (int i = 0; i < deletedFlags.size(); i++) {
            deletedFlags.set(i, false);
        }
        out.println("+OK All messages unmarked");
    }

    private void handleQuit() {
        if (state == POP3State.TRANSACTION) {
            deleteMarkedMessages();
        }
        out.println("+OK POP3 server signing off");
        closeConnection();
    }

    private void deleteMarkedMessages() {
        String sql = "UPDATE emails SET is_deleted = TRUE WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < deletedFlags.size(); i++) {
                if (deletedFlags.get(i)) {
                    stmt.setInt(1, emails.get(i).getId());
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void closeConnection() {
        try {
            socket.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}