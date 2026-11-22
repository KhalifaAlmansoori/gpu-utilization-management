package gpu.ui;

import gpu.model.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/** Login uses the lecture's JFrame, nested panels, grid layout and named listeners. */
public class LoginFrame extends JFrame {
    private AppController app;
    private ActionListener navigation;
    private JTextField username;
    private JPasswordField password;
    private JTextField message;

    public LoginFrame(AppController app) {
        this(app, null);
    }

    // Main supplies the listener that opens the destination requested by the controller.
    public LoginFrame(AppController app, ActionListener navigation) {
        this.app = app;
        this.navigation = navigation;
        setTitle("GPU Utilization Management - Login");
        setSize(850, 240);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        username = new JTextField(25);
        password = new JPasswordField(25);
        message = new JTextField(60);
        message.setEditable(false);
        JPanel fields = new JPanel();
        fields.setLayout(new GridLayout(2, 2));
        fields.add(new JLabel("Username / university ID", SwingConstants.RIGHT));
        fields.add(username);
        fields.add(new JLabel("Password", SwingConstants.RIGHT));
        fields.add(password);

        JButton loginButton = new JButton("Log in");
        loginButton.addActionListener(new LoginButtonHandler());
        JButton registerButton = new JButton("Register as a student");
        registerButton.addActionListener(new RegisterButtonHandler());
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(new SaveButtonHandler());
        JButton exitButton = new JButton("Exit");
        exitButton.addActionListener(new ExitButtonHandler());
        JPanel buttons = new JPanel();
        buttons.setLayout(new FlowLayout());
        buttons.add(loginButton);
        buttons.add(registerButton);
        buttons.add(saveButton);
        buttons.add(exitButton);
        JPanel bottom = new JPanel();
        bottom.setLayout(new BorderLayout());
        bottom.add(message, BorderLayout.PAGE_START);
        bottom.add(buttons, BorderLayout.PAGE_END);

        // The outer panel keeps the form at its normal size.
        JPanel form = new JPanel();
        form.setLayout(new FlowLayout());
        form.add(fields);
        Container pane = getContentPane();
        pane.setLayout(new BorderLayout());
        pane.add(new JLabel("GPU Utilization Management", SwingConstants.CENTER), BorderLayout.PAGE_START);
        pane.add(form, BorderLayout.CENTER);
        pane.add(bottom, BorderLayout.PAGE_END);
        refresh();
    }

    public void clearFields() {
        username.setText("");
        password.setText("");
    }

    public void refresh() {
        message.setText(app.status());
    }

    // The frame can also be used alone, without a navigation listener.
    private void updateScreen(ActionEvent event) {
        refresh();
        if (navigation != null) {
            navigation.actionPerformed(event);
        }
    }

    private class LoginButtonHandler implements ActionListener {
        public void actionPerformed(ActionEvent event) {
            char[] entered = password.getPassword();
            // Build the String expected by AccountService using a basic array loop.
            String enteredText = "";
            for (int i = 0; i < entered.length; i++) {
                enteredText += entered[i];
            }
            try {
                User user = app.accounts().authenticate(username.getText(), enteredText);
                if (user == null) {
                    app.report("Incorrect login details, or your registration is not approved.");
                } else {
                    clearFields();
                    app.openSession(user);
                }
            } finally {
                // Lecture 9 array traversal clears the temporary password characters.
                for (int i = 0; i < entered.length; i++) {
                    entered[i] = ' ';
                }
                password.setText("");
                updateScreen(event);
            }
        }
    }

    private class RegisterButtonHandler implements ActionListener {
        public void actionPerformed(ActionEvent event) {
            clearFields();
            app.showRegistration();
            updateScreen(event);
        }
    }

    private class SaveButtonHandler implements ActionListener {
        public void actionPerformed(ActionEvent event) {
            app.save();
            updateScreen(event);
        }
    }

    private class ExitButtonHandler implements ActionListener {
        public void actionPerformed(ActionEvent event) {
            app.close();
            updateScreen(event);
        }
    }
}
