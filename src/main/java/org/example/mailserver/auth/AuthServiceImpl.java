package org.example.mailserver.auth;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.nio.file.*;
import java.util.Comparator;
import org.example.mailserver.database.DatabaseConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
public class AuthServiceImpl extends UnicastRemoteObject implements AuthService {

    public AuthServiceImpl() throws RemoteException {
        super();
    }

    @Override
    public boolean authenticate(String username, String password) throws RemoteException {
        String sql = "SELECT password FROM users WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getString("password").equals(password);
            }
            return false;

        } catch (SQLException e) {
            throw new RemoteException("Database error during authentication", e);
        }
    }

    @Override
    public boolean createAccount(String username, String password) throws RemoteException {
        String sql = "INSERT INTO users (username, password) VALUES (?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, password);

            int rowsAffected = stmt.executeUpdate();

            // Créer le dossier utilisateur pour les emails
            if (rowsAffected > 0) {
                createUserMailDirectory(username);
                return true;
            }
            return false;

        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) { // Duplicate entry
                return false;
            }
            throw new RemoteException("Database error during account creation", e);
        }
    }

    @Override
    public boolean updateAccount(String username, String newPassword) throws RemoteException {
        String sql = "UPDATE users SET password = ? WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newPassword);
            stmt.setString(2, username);

            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new RemoteException("Database error during account update", e);
        }
    }

    @Override
    public boolean deleteAccount(String username) throws RemoteException {
        String sql = "DELETE FROM users WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            boolean deleted = stmt.executeUpdate() > 0;

            if (deleted) {
                deleteUserMailDirectory(username);
            }
            return deleted;

        } catch (SQLException e) {
            throw new RemoteException("Database error during account deletion", e);
        }
    }

    @Override
    public boolean userExists(String username) throws RemoteException {
        String sql = "SELECT id FROM users WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            throw new RemoteException("Database error checking user existence", e);
        }
    }

    private void createUserMailDirectory(String username) throws RemoteException {
        try {
            Path path = Paths.get("mailserver/" + username);
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RemoteException("Could not create user mail directory", e);
        }
    }

    private void deleteUserMailDirectory(String username) throws RemoteException {
        try {
            Path path = Paths.get("mailserver/" + username);
            if (Files.exists(path)) {
                Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.delete(p); }
                            catch (IOException e) { e.printStackTrace(); }
                        });
            }
        } catch (IOException e) {
            throw new RemoteException("Could not delete user mail directory", e);
        }
    }
}