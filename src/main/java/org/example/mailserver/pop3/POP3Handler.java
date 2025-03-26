package org.example.mailserver.pop3;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
// Add these imports at the top
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import org.example.mailserver.auth.AuthService;
public class POP3Handler extends Thread {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private POP3State state;
    private String user;
    private List<Path> emails;
    private List<Boolean> deletedFlags;

    public POP3Handler(Socket socket) {
        this.socket = socket;
        this.state = POP3State.AUTHORIZATION;
        this.emails = new ArrayList<>();
        this.deletedFlags = new ArrayList<>();
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
                if (command.startsWith("USER ")||command.startsWith("user ")) {
                    user = command.substring(5).trim();
                    if (isValidUser(user)) {
                        out.println("+OK");
                    } else {
                        out.println("-ERR Invalid email format or user does not exist");
                    }
                } else if (command.startsWith("PASS ")||command.startsWith("pass ")) {
                    String password = command.substring(5).trim();
                    if (isValidPassword(user, password)) {
                        out.println("+OK User successfully logged in");
                        state = POP3State.TRANSACTION;
                        loadEmails();
                    } else {
                        out.println("-ERR Invalid password");
                    }
                } else if (command.startsWith("QUIT")||command.startsWith("quit")) {
                    out.println("+OK Bye");
                    state = POP3State.AUTHORIZATION;
                    closeConnection();
                } else {
                    out.println("-ERR Invalid command");
                }
                break;
            case TRANSACTION:
                if (command.equals("STAT")||command.startsWith("stat")) {
                    handleStat();
                } else if (command.equals("LIST")||command.startsWith("list")) {
                    handleList();
                } else if (command.startsWith("RETR ")||command.startsWith("retr ")) {
                    handleRetr(command);
                } else if (command.startsWith("DELE ")||command.startsWith("dele ")) {
                    handleDele(command);
                } else if (command.equals("NOOP")||command.startsWith("noop")) {
                    out.println("+OK");
                }else if (command.startsWith("UIDL")||command.startsWith("uidl")) {
                    handleUidl(command);
                }else if (command.startsWith("TOP ")||command.startsWith("top ")) {
                    handleTop(command);
                } else if (command.equals("RSET")||command.startsWith("rset")) {
                    handleRset();
                } else if (command.equals("QUIT")||command.startsWith("quit")) {
                    handleQuit();
                } else {
                    out.println("-ERR Invalid command");
                }
                break;
            case UPDATE:
                if (command.equals("QUIT")||command.startsWith("quit")) {
                    handleQuit();
                } else {
                    out.println("-ERR Invalid command");
                }
                break;
        }
    }
    /*
    private boolean isValidUser(String email) {
        // Vérifier si l'email est au format user1@domain.com
        if (!email.matches("[^@]+@[^@]+")) {
            return false; // Format invalide
        }

        // Extraire le username (tout ce qui précède le @)
        String username = extractUsernameFromEmail(email);
        if (username == null) {
            return false; // Format invalide
        }

        // Vérifier si l'utilisateur existe dans le fichier passwords.txt
        try (BufferedReader reader = new BufferedReader(new FileReader("passwords.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts[0].equals(username)) {
                    return true;
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return true;
    }
    */
    // Modify the isValidUser and isValidPassword methods
    private boolean isValidUser(String email) {
        try {
            // Get RMI registry
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            AuthService authService = (AuthService) registry.lookup("AuthService");

            // Extract username from email
            String username = extractUsernameFromEmail(email);
            if (username == null) return false;

            // Check if user exists via RMI
            return authService.userExists(username);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /*
    private boolean isValidPassword(String email, String password) {
        // Extraire le username de l'adresse email
        String username = extractUsernameFromEmail(email);
        if (username == null) {
            return false; // Format invalide
        }

        // Vérifier si le mot de passe correspond à celui stocké dans passwords.txt
        try (BufferedReader reader = new BufferedReader(new FileReader("C:\\Users\\pc\\Desktop\\TP1_enonce\\tp-gl-1\\MailServer\\src\\main\\resources\\passwords.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts[0].equals(username) && parts[1].equals(password)) {
                    return true;
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return false;
    }
    */

    private boolean isValidPassword(String email, String password) {
        try {
            // Get RMI registry
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            AuthService authService = (AuthService) registry.lookup("AuthService");

            // Extract username from email
            String username = extractUsernameFromEmail(email);
            if (username == null) return false;

            // Authenticate via RMI
            return authService.authenticate(username, password);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    private String extractUsernameFromEmail(String email) {
        // Vérifier si l'email est au format user1@domain.com
        if (!email.matches("[^@]+@[^@]+")) {
            return null; // Format invalide
        }

        // Extraire le username (tout ce qui précède le @)
        int atIndex = email.indexOf('@');
        if (atIndex == -1) {
            return null; // Format invalide
        }

        return email.substring(0, atIndex); // Retourne "user1" pour "user1@domain.com"
    }

    private void loadEmails() {
        emails.clear();
        deletedFlags.clear();

        // Extraire le username de l'adresse email
        String username = extractUsernameFromEmail(user);
        if (username == null) {
            return; // Format invalide
        }

        // Charger les emails du dossier correspondant au username
        Path userDir = Paths.get("mailserver/" + username);
        if (Files.exists(userDir)) {
            try {
                Files.list(userDir).forEach(emails::add);
                for (int i = 0; i < emails.size(); i++) {
                    deletedFlags.add(false);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
    private void handleTop(String command) {
        try {
            // Extraire le numéro du message et le nombre de lignes
            String[] parts = command.split(" ");
            if (parts.length != 3) {
                out.println("-ERR Invalid syntax for TOP command");
                return;
            }

            int messageNumber = Integer.parseInt(parts[1]) - 1; // Convertir en index basé sur 0
            int numLines = Integer.parseInt(parts[2]);

            // Vérifier si le message existe et n'est pas marqué pour suppression
            if (messageNumber < 0 || messageNumber >= emails.size() || deletedFlags.get(messageNumber)) {
                out.println("-ERR No such message");
                return;
            }

            // Lire le fichier du message
            Path emailPath = emails.get(messageNumber);
            List<String> lines = Files.readAllLines(emailPath);

            // Séparer les en-têtes et le corps du message
            int headerEndIndex = 0;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).isEmpty()) {
                    headerEndIndex = i;
                    break;
                }
            }

            // Envoyer les en-têtes
            out.println("+OK");
            for (int i = 0; i <= headerEndIndex; i++) {
                out.println(lines.get(i));
            }

            // Envoyer les lignes du corps demandées
            int bodyStartIndex = headerEndIndex + 1;
            int bodyEndIndex = Math.min(bodyStartIndex + numLines+1, lines.size());
            for (int i = bodyStartIndex; i < bodyEndIndex; i++) {
                out.println(lines.get(i));
            }

            // Envoyer le point final
            out.println(".");
        } catch (NumberFormatException ex) {
            out.println("-ERR Invalid message number or line count");
        } catch (IOException ex) {
            out.println("-ERR Error reading message");
        }
    }
    private void handleUidl(String command) {
        try {
            // Extraire le numéro du message (si présent)
            String[] parts = command.split(" ");
            if (parts.length == 1) {
                // Cas 1 : UIDL sans argument (renvoyer la liste des UID)
                out.println("+OK");
                for (int i = 0; i < emails.size(); i++) {
                    if (!deletedFlags.get(i)) {
                        String uid = generateUid(emails.get(i));
                        out.println((i + 1) + " " + uid);
                    }
                }
                out.println(".");
            } else if (parts.length == 2) {
                // Cas 2 : UIDL avec un argument (renvoyer l'UID d'un message spécifique)
                int messageNumber = Integer.parseInt(parts[1]) - 1; // Convertir en index basé sur 0

                // Vérifier si le message existe et n'est pas marqué pour suppression
                if (messageNumber < 0 || messageNumber >= emails.size() || deletedFlags.get(messageNumber)) {
                    out.println("-ERR No such message");
                    return;
                }

                // Générer et renvoyer l'UID du message
                String uid = generateUid(emails.get(messageNumber));
                out.println("+OK " + (messageNumber + 1) + " " + uid);
            } else {
                // Syntaxe invalide
                out.println("-ERR Invalid syntax for UIDL command");
            }
        } catch (NumberFormatException ex) {
            out.println("-ERR Invalid message number");
        }
    }

    /**
     * Génère un UID unique pour un message.
     * Ici, on utilise le hash du chemin du fichier comme UID.
     */
    private String generateUid(Path emailPath) {
        return Integer.toHexString(emailPath.hashCode());
    }
    private void handleStat() {
        int count = 0;
        long totalSize = 0;

        for (int i = 0; i < emails.size(); i++) {
            if (!deletedFlags.get(i)) {
                count++;
                try {
                    totalSize += Files.size(emails.get(i));
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }

        out.println("+OK " + count + " " + totalSize);
    }

    private void handleList() {
        out.println("+OK " + emails.size() + " messages");
        for (int i = 0; i < emails.size(); i++) {
            if (!deletedFlags.get(i)) {
                try {
                    long size = Files.size(emails.get(i));
                    out.println((i + 1) + " " + size);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        out.println(".");
    }

    private void handleRetr(String command) {
        try {
            int messageNumber = Integer.parseInt(command.substring(5).trim()) - 1;
            if (messageNumber >= 0 && messageNumber < emails.size() && !deletedFlags.get(messageNumber)) {
                out.println("+OK");
                Files.lines(emails.get(messageNumber)).forEach(out::println);
                out.println(".");
            } else {
                out.println("-ERR No such message");
            }
        } catch (NumberFormatException | IOException ex) {
            out.println("-ERR Invalid message number");
        }
    }

    private void handleDele(String command) {
        try {
            int messageNumber = Integer.parseInt(command.substring(5).trim()) - 1;
            if (messageNumber >= 0 && messageNumber < emails.size() && !deletedFlags.get(messageNumber)) {
                deletedFlags.set(messageNumber, true);
                out.println("+OK Message marked for deletion");
            } else {
                out.println("-ERR No such message");
            }
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
            for (int i = 0; i < deletedFlags.size(); i++) {
                if (deletedFlags.get(i)) {
                    try {
                        Files.delete(emails.get(i));
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
        out.println("+OK Bye");
        state = POP3State.AUTHORIZATION;
        closeConnection();
    }

    private void closeConnection() {
        try {
            socket.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}