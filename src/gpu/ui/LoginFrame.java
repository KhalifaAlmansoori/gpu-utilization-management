package gpu.ui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/** Reads login details; AppController authenticates and Main opens the next screen. */
public class LoginFrame extends JFrame {
    private AppController app;
    private ActionListener navigation;
    private JTextField username = new JTextField(25);
    private JPasswordField password = new JPasswordField(25);
    private JLabel message = new JLabel(" ");

    // null lets this screen run alone; Main normally supplies the navigation listener.
    public LoginFrame(AppController app) {
        this(app, null);
    }

    public LoginFrame(AppController app, ActionListener navigation) {
        this.app = app;
        this.navigation = navigation;
        setTitle("GPU Utilization Management - Login");
        setSize(850, 240);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        message.setForeground(Color.RED);

        // GridLayout makes two rows: each label is followed by its input field.
        JPanel fields = new JPanel();
        fields.setLayout(new GridLayout(2, 2));
        fields.add(new JLabel("Username / university ID", SwingConstants.RIGHT));
        fields.add(username);
        fields.add(new JLabel("Password", SwingConstants.RIGHT));
        fields.add(password);
        JPanel form = new JPanel();
        form.setLayout(new FlowLayout());
        form.add(fields); // FlowLayout keeps the form at its preferred size.

        JPanel buttons = new JPanel();
        buttons.setLayout(new FlowLayout());
        String[] actions = {"Log in", "Register as a student", "Save", "Exit"};
        for (String action : actions) {
            JButton button = new JButton(action);
            button.addActionListener(new ButtonHandler(action));
            buttons.add(button);
        }
        JPanel bottom = new JPanel();
        bottom.setLayout(new BorderLayout());
        bottom.add(message, BorderLayout.PAGE_START);
        bottom.add(buttons, BorderLayout.PAGE_END);
        // The content pane holds the window's top, middle and bottom panels.
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

    private void logIn() {
        char[] characters = password.getPassword();
        String passwordText = "";
        // The account service accepts a String; the password field supplies a char array.
        for (int i = 0; i < characters.length; i++) {
            passwordText += characters[i];
        }
        try {
            if (app.login(username.getText(), passwordText)) {
                clearFields();
            }
        } finally {
            // Clear the temporary array and field after every attempt.
            for (int i = 0; i < characters.length; i++) {
                characters[i] = ' ';
            }
            password.setText("");
        }
    }

    /** Each button passes its label to the same named listener. */
    private class ButtonHandler implements ActionListener {
        private String action;

        public ButtonHandler(String action) {
            this.action = action;
        }

        public void actionPerformed(ActionEvent event) {
            if (action.equals("Log in")) {
                logIn();
            } else if (action.equals("Register as a student")) {
                clearFields();
                app.showRegistration();
            } else if (action.equals("Save")) {
                app.save();
            } else {
                app.close();
            }
            refresh();
            if (navigation != null) {
                navigation.actionPerformed(event); // Main handles window navigation.
            }
        }
    }
}
