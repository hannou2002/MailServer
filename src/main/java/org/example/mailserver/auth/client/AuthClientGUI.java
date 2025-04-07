package org.example.mailserver.auth.client;

import javax.swing.*;
import java.awt.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import org.example.mailserver.auth.AuthService;

public class AuthClientGUI extends JFrame {
    private AuthService authService;

    public AuthClientGUI() {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            authService = (AuthService) registry.lookup("AuthService");
            initializeUI();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error connecting to AuthService: " + e.getMessage(),
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void initializeUI() {
        setTitle("User Account Manager (RMI)");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Create Account", createCreateAccountPanel());
        tabbedPane.addTab("Update Account", createUpdateAccountPanel());
        tabbedPane.addTab("Delete Account", createDeleteAccountPanel());

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createCreateAccountPanel() {
        JPanel panel = new JPanel(new GridLayout(5, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel userLabel = new JLabel("Username:");
        JTextField userField = new JTextField();
        JLabel passLabel = new JLabel("Password:");
        JPasswordField passField = new JPasswordField();
        JButton createButton = new JButton("Create Account");

        createButton.addActionListener(e -> {
            String username = userField.getText().trim();
            String password = new String(passField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                showError("Username and password cannot be empty");
                return;
            }

            try {
                boolean success = authService.createAccount(username, password);
                if (success) {
                    showSuccess("Account created successfully!");
                    userField.setText("");
                    passField.setText("");
                } else {
                    showError("Account already exists");
                }
            } catch (Exception ex) {
                showError("Error creating account: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        panel.add(userLabel);
        panel.add(userField);
        panel.add(passLabel);
        panel.add(passField);
        panel.add(new JLabel());
        panel.add(createButton);

        return panel;
    }

    private JPanel createUpdateAccountPanel() {
        JPanel panel = new JPanel(new GridLayout(5, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel userLabel = new JLabel("Username:");
        JTextField userField = new JTextField();
        JLabel passLabel = new JLabel("New Password:");
        JPasswordField passField = new JPasswordField();
        JButton updateButton = new JButton("Update Account");

        updateButton.addActionListener(e -> {
            String username = userField.getText().trim();
            String password = new String(passField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                showError("Username and password cannot be empty");
                return;
            }

            try {
                boolean success = authService.updateAccount(username, password);
                if (success) {
                    showSuccess("Account updated successfully!");
                    userField.setText("");
                    passField.setText("");
                } else {
                    showError("Account does not exist");
                }
            } catch (Exception ex) {
                showError("Error updating account: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        panel.add(userLabel);
        panel.add(userField);
        panel.add(passLabel);
        panel.add(passField);
        panel.add(new JLabel());
        panel.add(updateButton);

        return panel;
    }

    private JPanel createDeleteAccountPanel() {
        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel userLabel = new JLabel("Username:");
        JTextField userField = new JTextField();
        JButton deleteButton = new JButton("Delete Account");

        deleteButton.addActionListener(e -> {
            String username = userField.getText().trim();

            if (username.isEmpty()) {
                showError("Username cannot be empty");
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete this account?\nAll emails will be lost.",
                    "Confirm Deletion", JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    boolean success = authService.deleteAccount(username);
                    if (success) {
                        showSuccess("Account deleted successfully!");
                        userField.setText("");
                    } else {
                        showError("Account does not exist");
                    }
                } catch (Exception ex) {
                    showError("Error deleting account: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        });

        panel.add(userLabel);
        panel.add(userField);
        panel.add(new JLabel());
        panel.add(deleteButton);

        return panel;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void showSuccess(String message) {
        JOptionPane.showMessageDialog(this, message, "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AuthClientGUI client = new AuthClientGUI();
            client.setVisible(true);
        });
    }
}