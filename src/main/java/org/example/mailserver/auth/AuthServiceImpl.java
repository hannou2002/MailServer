package org.example.mailserver.auth;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.nio.file.*;
import java.io.*;
import org.json.*;
import java.util.Comparator;

public class AuthServiceImpl extends UnicastRemoteObject implements AuthService {
    private static final String ACCOUNTS_FILE = "accounts.json";

    public AuthServiceImpl() throws RemoteException {
        super();
        initializeAccountsFile();
    }

    private void initializeAccountsFile() {
        try {
            Path filePath = Paths.get(ACCOUNTS_FILE);
            if (!Files.exists(filePath)) {
                Files.createFile(filePath);
                Files.writeString(filePath, "{}"); // Initialize with empty JSON object
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean authenticate(String username, String password) throws RemoteException {
        try {
            String content = Files.readString(Paths.get(ACCOUNTS_FILE));
            JSONObject accounts = new JSONObject(content);

            if (accounts.has(username)) {
                return accounts.getJSONObject(username).getString("password").equals(password);
            }
            return false;
        } catch (IOException e) {
            throw new RemoteException("Error reading accounts file", e);
        }
    }

    @Override
    public boolean createAccount(String username, String password) throws RemoteException {
        try {
            String content = Files.readString(Paths.get(ACCOUNTS_FILE));
            JSONObject accounts = new JSONObject(content);

            if (accounts.has(username)) {
                return false; // User already exists
            }

            JSONObject user = new JSONObject();
            user.put("password", password);
            accounts.put(username, user);

            Files.writeString(Paths.get(ACCOUNTS_FILE), accounts.toString());

            // Create user directory for emails
            Path userDir = Paths.get("mailserver/" + username);
            Files.createDirectories(userDir);

            return true;
        } catch (IOException e) {
            throw new RemoteException("Error creating account", e);
        }
    }

    @Override
    public boolean updateAccount(String username, String newPassword) throws RemoteException {
        try {
            String content = Files.readString(Paths.get(ACCOUNTS_FILE));
            JSONObject accounts = new JSONObject(content);

            if (!accounts.has(username)) {
                return false; // User doesn't exist
            }

            accounts.getJSONObject(username).put("password", newPassword);
            Files.writeString(Paths.get(ACCOUNTS_FILE), accounts.toString());
            return true;
        } catch (IOException e) {
            throw new RemoteException("Error updating account", e);
        }
    }

    @Override
    public boolean deleteAccount(String username) throws RemoteException {
        try {
            String content = Files.readString(Paths.get(ACCOUNTS_FILE));
            JSONObject accounts = new JSONObject(content);

            if (!accounts.has(username)) {
                return false; // User doesn't exist
            }

            accounts.remove(username);
            Files.writeString(Paths.get(ACCOUNTS_FILE), accounts.toString());

            // Delete user directory for emails
            Path userDir = Paths.get("mailserver/" + username);
            if (Files.exists(userDir)) {
                Files.walk(userDir)
                        .sorted(Comparator.reverseOrder())  // This is where Comparator is used
                        .forEach(path -> {
                            try { Files.delete(path); }
                            catch (IOException e) { e.printStackTrace(); }
                        });
            }

            return true;
        } catch (IOException e) {
            throw new RemoteException("Error deleting account", e);
        }
    }

    @Override
    public boolean userExists(String username) throws RemoteException {
        try {
            String content = Files.readString(Paths.get(ACCOUNTS_FILE));
            JSONObject accounts = new JSONObject(content);
            return accounts.has(username);
        } catch (IOException e) {
            throw new RemoteException("Error checking user existence", e);
        }
    }
}