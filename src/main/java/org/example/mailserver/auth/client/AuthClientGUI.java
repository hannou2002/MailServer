package org.example.mailserver.auth.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
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
            JOptionPane.showMessageDialog(this, "Error connecting to AuthService: " + e.getMessage(),
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void initializeUI() {
        setTitle("User Account Manager");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Create tabbed pane
        JTabbedPane tabbedPane = new JTabbedPane();

        // Add tabs
        tabbedPane.addTab("Create Account", createCreateAccountPanel());
        tabbedPane.addTab("Update Account", createUpdateAccountPanel());
        tabbedPane.addTab("Delete Account", createDeleteAccountPanel());

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createCreateAccountPanel() {
        JPanel panel = new JPanel(new GridLayout(4, 2, 5, 5));

        JLabel userLabel = new JLabel("Username:");
        JTextField userField = new JTextField();
        JLabel passLabel = new JLabel("Password:");
        JPasswordField passField = new JPasswordField();
        JButton createButton = new JButton("Create Account");

        createButton.addActionListener(e -> {
            String username = userField.getText().trim();
            String password = new String(passField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username and password cannot be empty",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                boolean success = authService.createAccount(username, password);
                if (success) {
                    JOptionPane.showMessageDialog(this, "Account created successfully");
                    userField.setText("");
                    passField.setText("");
                } else {
                    JOptionPane.showMessageDialog(this, "Account already exists",
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error creating account: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });

        panel.add(userLabel);
        panel.add(userField);
        panel.add(passLabel);
        panel.add(passField);
        panel.add(new JLabel()); // Empty cell
        panel.add(createButton);

        return panel;
    }

    private JPanel createUpdateAccountPanel() {
        JPanel panel = new JPanel(new GridLayout(4, 2, 5, 5));

        JLabel userLabel = new JLabel("Username:");
        JTextField userField = new JTextField();
        JLabel passLabel = new JLabel("New Password:");
        JPasswordField passField = new JPasswordField();
        JButton updateButton = new JButton("Update Account");

        updateButton.addActionListener(e -> {
            String username = userField.getText().trim();
            String password = new String(passField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username and password cannot be empty",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                boolean success = authService.updateAccount(username, password);
                if (success) {
                    JOptionPane.showMessageDialog(this, "Account updated successfully");
                    userField.setText("");
                    passField.setText("");
                } else {
                    JOptionPane.showMessageDialog(this, "Account does not exist",
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error updating account: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });

        panel.add(userLabel);
        panel.add(userField);
        panel.add(passLabel);
        panel.add(passField);
        panel.add(new JLabel()); // Empty cell
        panel.add(updateButton);

        return panel;
    }

    private JPanel createDeleteAccountPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));

        JLabel userLabel = new JLabel("Username:");
        JTextField userField = new JTextField();
        JButton deleteButton = new JButton("Delete Account");

        deleteButton.addActionListener(e -> {
            String username = userField.getText().trim();

            if (username.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username cannot be empty",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete this account?\nAll emails will be lost.",
                    "Confirm Deletion", JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    boolean success = authService.deleteAccount(username);
                    if (success) {
                        JOptionPane.showMessageDialog(this, "Account deleted successfully");
                        userField.setText("");
                    } else {
                        JOptionPane.showMessageDialog(this, "Account does not exist",
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error deleting account: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        });

        panel.add(userLabel);
        panel.add(userField);
        panel.add(new JLabel()); // Empty cell
        panel.add(deleteButton);

        return panel;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AuthClientGUI client = new AuthClientGUI();
            client.setVisible(true);
        });
    }
}