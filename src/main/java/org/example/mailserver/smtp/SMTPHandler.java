package org.example.mailserver.smtp;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
// Add these imports at the top
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import org.example.mailserver.auth.AuthService;
public class SMTPHandler extends Thread {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private SMTPState state;
    private String sender;
    private List<String> recipients;
    private StringBuilder emailContent;
    private boolean isDataInProgress;

    public SMTPHandler(Socket socket) {
        this.socket = socket;
        this.state = SMTPState.INIT;
        this.recipients = new ArrayList<>();
        this.emailContent = new StringBuilder();
        this.isDataInProgress = false;
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
                // Si la commande DATA était en cours, enregistrer le message partiel
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
                if (command.startsWith("HELO")||command.startsWith("helo")) {
                    out.println("250 Hello");
                    state = SMTPState.HELO;
                } else if (command.equals("NOOP")||command.startsWith("noop")) {
                    out.println("250 OK"); // Réponse à la commande NOOP
                } else if (command.startsWith("VRFY ")||command.startsWith("vrfy ")) {
                    handleVrfy(command); // Gérer la commande VRFY
                } else if (command.startsWith("QUIT")||command.startsWith("quit")) {
                    out.println("221 Bye");
                    state = SMTPState.INIT;
                    closeConnection();
                } else {
                    out.println("503 Bad sequence of commands");
                }
                break;
            case HELO:
                if (command.startsWith("MAIL FROM:")||command.startsWith("mail from:")) {
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
                } else if (command.startsWith("QUIT")||command.startsWith("quit")) {
                    out.println("221 Bye");
                    state = SMTPState.INIT;
                    closeConnection();
                } else if (command.equals("NOOP")||command.startsWith("noop")) {
                    out.println("250 OK"); // Réponse à la commande NOOP
                } else if (command.startsWith("VRFY ")||command.startsWith("vrfy ")) {
                    handleVrfy(command); // Gérer la commande VRFY
                }   else {
                    out.println("503 Bad sequence of commands");
                }
                break;
            case MAIL_FROM:
                if (command.startsWith("RCPT TO:")||command.startsWith("rcpt to:")) {
                    state = SMTPState.RCPT_TO;
                    handleRcptTo(command); // Gérer RCPT TO dans une méthode séparée
                } else if (command.startsWith("QUIT")||command.startsWith("quit")) {
                    out.println("221 Bye");
                    state = SMTPState.INIT;
                    closeConnection();
                } else if (command.equals("NOOP")||command.startsWith("noop")) {
                    out.println("250 OK"); // Réponse à la commande NOOP
                } else if (command.startsWith("VRFY ")||command.startsWith("vrfy ")) {
                    handleVrfy(command); // Gérer la commande VRFY
                } else {
                    out.println("503 Bad sequence of commands");
                }
                break;
            case RCPT_TO:
                if (command.startsWith("RCPT TO:")||command.startsWith("rcpt to:")) {
                    handleRcptTo(command); // Gérer RCPT TO dans une méthode séparée
                } else if (command.equals("DATA")||command.startsWith("data")) {
                    if (recipients.isEmpty()) {
                        out.println("503 No valid recipients");
                    } else {
                        out.println("354 Start mail input; end with <CRLF>.<CRLF>");
                        state = SMTPState.DATA;
                        isDataInProgress = true;
                    }
                } else if (command.startsWith("QUIT")||command.startsWith("quit")) {
                    out.println("221 Bye");
                    state = SMTPState.INIT;
                    closeConnection();
                } else if (command.equals("NOOP")||command.startsWith("noop")) {
                    out.println("250 OK"); // Réponse à la commande NOOP
                } else if (command.startsWith("VRFY ")||command.startsWith("vrfy ")) {
                    handleVrfy(command); // Gérer la commande VRFY
                } else {
                    out.println("503 Bad sequence of commands");
                }
                break;
            case DATA:
                if (command.equals(".")) {
                    if (emailContent.length() == 0) {
                        out.println("554 No message content");
                    } else {
                        storeEmail();
                        out.println("250 OK");
                    }
                    state = SMTPState.HELO;
                    isDataInProgress = false;
                } else {
                    emailContent.append(command).append("\n");
                    // Vérifier si la taille du message dépasse une limite (par exemple, 10 Mo)
                    if (emailContent.length() > 10 * 1024 * 1024) { // 10 Mo
                        out.println("552 Message size exceeds fixed maximum");
                        state = SMTPState.QUIT;
                        isDataInProgress = false;
                        closeConnection();
                    }
                }
                break;
            case QUIT:
                if (command.startsWith("QUIT")||command.startsWith("quit")) {
                    out.println("221 Bye");
                    state = SMTPState.INIT;
                    closeConnection();
                } else if (command.equals("NOOP")||command.startsWith("noop")) {
                    out.println("250 OK"); // Réponse à la commande NOOP
                }  else if (command.startsWith("VRFY ")||command.startsWith("vrfy ")) {
                    handleVrfy(command); // Gérer la commande VRFY
                } else {
                    out.println("503 Bad sequence of commands");
                }
                break;
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
    private void handleVrfy(String command) {
        String emailOrUser = command.substring(5).trim();

        // Si l'argument est une adresse e-mail complète
        if (isValidEmailFormat(emailOrUser)) {
            String email = extractEmail(emailOrUser);
            if (isValidRecipient(email)) {
                out.println("250 " + email); // Adresse e-mail valide
            } else {
                out.println("550 User not found"); // Adresse e-mail invalide
            }
        }
        // Si l'argument est un nom d'utilisateur
        else {
            String username = emailOrUser.split("@")[0]; // Extraire le nom d'utilisateur
            Path userDir = Paths.get("mailserver/" + username);
            if (Files.exists(userDir)) {
                out.println("250 " + username + "@domain.com"); // Utilisateur valide
            } else {
                out.println("550 User not found"); // Utilisateur invalide
            }
        }
    }
    private boolean isValidEmailFormat(String email) {
        // Vérifie si l'email est au format <username@domain>
        return email.matches("<[^<>]+@[^<>]+>");
    }

    private String extractEmail(String email) {
        // Extrait l'adresse email entre < et >
        int start = email.indexOf('<');
        int end = email.indexOf('>');
        if (start != -1 && end != -1) {
            return email.substring(start + 1, end);
        }
        return email;
    }
    /*
    private boolean isValidSender(String sender) {
        // Extrait le username de l'adresse email
        String username = sender.split("@")[0];
        // Vérifie si le dossier de l'expéditeur existe
        Path senderDir = Paths.get("mailserver/" + username);
        return Files.exists(senderDir);
    }
    */
    // Modify the isValidSender and isValidRecipient methods
    private boolean isValidSender(String sender) {
        try {
            // Extract username from email
            String username = sender.split("@")[0];

            // Get RMI registry
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            AuthService authService = (AuthService) registry.lookup("AuthService");

            // Check if user exists via RMI
            return authService.userExists(username);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /*
    private boolean isValidRecipient(String recipient) {
        // Extrait le username de l'adresse email
        String username = recipient.split("@")[0];
        // Vérifie si le dossier du destinataire existe
        Path recipientDir = Paths.get("mailserver/" + username);
        return Files.exists(recipientDir);
    }
    */
    private boolean isValidRecipient(String recipient) {
        try {
            // Extract username from email
            String username = recipient.split("@")[0];

            // Get RMI registry
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            AuthService authService = (AuthService) registry.lookup("AuthService");

            // Check if user exists via RMI
            return authService.userExists(username);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    private void storeEmail() {
        try {
            // Pour chaque destinataire valide, stocke l'email dans son dossier
            for (String recipient : recipients) {
                String username = recipient.split("@")[0];
                Path recipientDir = Paths.get("mailserver/" + username);
                if (!Files.exists(recipientDir)) {
                    Files.createDirectories(recipientDir);
                }

                // Génère un nom de fichier basé sur le timestamp
                String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
                Path emailFile = recipientDir.resolve(timestamp + ".txt");

                // Écrit le contenu de l'email dans le fichier
                String email = "From: " + sender + "\n"
                        + "To: " + recipient + "\n"
                        + emailContent.toString();
                Files.write(emailFile, email.getBytes());
            }

            // Réinitialise le contenu de l'email et la liste des destinataires
            emailContent.setLength(0);
            recipients.clear();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void closeConnection() {
        try {
            // Si la commande DATA était en cours, enregistrer le message partiel
            if (isDataInProgress) {
                storeEmail();
            }
            socket.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}