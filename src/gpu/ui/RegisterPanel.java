package gpu.ui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/** Collects registration details; the account service validates and stores the request. */
public class RegisterPanel extends JPanel {
    private AppController app;
    private ActionListener navigation;
    private JTextField universityId = new JTextField(24);
    private JTextField fullName = new JTextField(24);
    private JTextField email = new JTextField(24);
    private JTextField phone = new JTextField(24);
    private JPasswordField password = new JPasswordField(24);
    private JPasswordField confirmation = new JPasswordField(24);
    private JLabel message = new JLabel(" ");

    // Main supplies navigation; null allows standalone use while reviewing this screen.
    public RegisterPanel(AppController app) {
        this(app, null);
    }

    public RegisterPanel(AppController app, ActionListener navigation) {
        this.app = app;
        this.navigation = navigation;
        setLayout(new BorderLayout());
        message.setForeground(Color.RED);

        JPanel fields = new JPanel();
        fields.setLayout(new GridLayout(6, 2));
        String[] labels = {"University ID", "Full name", "Email", "Phone", "Password", "Confirm password"};
        JTextField[] inputs = {universityId, fullName, email, phone, password, confirmation};
        // Matching array positions pair labels with inputs. Password fields extend JTextField.
        for (int i = 0; i < labels.length; i++) {
            fields.add(new JLabel(labels[i], SwingConstants.RIGHT));
            fields.add(inputs[i]);
        }
        JPanel form = new JPanel();
        form.setLayout(new FlowLayout());
        form.add(fields);
        JPanel buttons = new JPanel();
        buttons.setLayout(new FlowLayout());
        String[] actions = {"Back to login", "Submit registration", "Save"};
        for (String action : actions) {
            JButton button = new JButton(action);
            button.addActionListener(new ButtonHandler(action));
            buttons.add(button);
        }
        JPanel bottom = new JPanel();
        bottom.setLayout(new BorderLayout());
        bottom.add(message, BorderLayout.PAGE_START);
        bottom.add(buttons, BorderLayout.PAGE_END);
        add(new JLabel("Student registration - administrator approval required",
                SwingConstants.CENTER), BorderLayout.PAGE_START);
        add(form, BorderLayout.CENTER);
        add(bottom, BorderLayout.PAGE_END);
        refresh();
    }

    public void clearFields() {
        JTextField[] inputs = {universityId, fullName, email, phone, password, confirmation};
        for (JTextField input : inputs) {
            input.setText("");
        }
    }

    public void refresh() {
        message.setText(app.status());
    }

    private void register() {
        char[] characters = password.getPassword();
        char[] repeated = confirmation.getPassword();
        String passwordText = "";
        for (int i = 0; i < characters.length; i++) {
            passwordText += characters[i];
        }
        try {
            // Arrays match only when their lengths and every character match.
            boolean same = characters.length == repeated.length;
            for (int i = 0; same && i < characters.length; i++) {
                if (characters[i] != repeated[i]) {
                    same = false;
                }
            }
            if (!same) {
                app.report("The passwords do not match.");
            } else if (app.registerStudent(universityId.getText(), fullName.getText(),
                    email.getText(), phone.getText(), passwordText)) {
                clearFields();
                app.showLogin();
            }
        } finally {
            for (int i = 0; i < characters.length; i++) {
                characters[i] = ' ';
            }
            for (int i = 0; i < repeated.length; i++) {
                repeated[i] = ' ';
            }
            password.setText("");
            confirmation.setText("");
        }
    }

    private class ButtonHandler implements ActionListener {
        private String action;

        public ButtonHandler(String action) {
            this.action = action;
        }

        public void actionPerformed(ActionEvent event) {
            if (action.equals("Back to login")) {
                clearFields();
                app.showLogin();
            } else if (action.equals("Submit registration")) {
                register();
            } else {
                app.save();
            }
            refresh();
            if (navigation != null) {
                navigation.actionPerformed(event);
            }
        }
    }
}
