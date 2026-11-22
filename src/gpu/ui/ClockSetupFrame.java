package gpu.ui;

import java.awt.*;
import java.awt.event.*;
import java.text.*;
import javax.swing.*;

/** Initializes the simulation clock once, using the guide's approved date/time picker. */
public class ClockSetupFrame extends JFrame {
    private AppController app;
    private ActionListener navigation;
    private JSpinner startPicker = new JSpinner(new SpinnerDateModel());
    private JSpinner.DateEditor dateEditor;
    private JButton startButton;
    private JLabel message = new JLabel(" ");

    public ClockSetupFrame(AppController app) {
        this(app, null);
    }

    public ClockSetupFrame(AppController app, ActionListener navigation) {
        this.app = app;
        this.navigation = navigation;
        setTitle("Set simulation clock");
        setSize(850, 260);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        message.setForeground(Color.RED);

        dateEditor = new JSpinner.DateEditor(startPicker, "dd/MM/yyyy HH:mm");
        dateEditor.getFormat().setLenient(false); // Reject impossible dates such as 31 February.
        // Preserve invalid edits so Start can report them instead of silently restoring an old date.
        dateEditor.getTextField().setFocusLostBehavior(JFormattedTextField.PERSIST);
        startPicker.setEditor(dateEditor);

        JPanel fields = new JPanel();
        fields.setLayout(new GridLayout(1, 2));
        fields.add(new JLabel("Start (dd/MM/yyyy HH:mm)"));
        fields.add(startPicker);
        JPanel form = new JPanel();
        form.setLayout(new FlowLayout());
        form.add(fields);
        JPanel buttons = new JPanel();
        buttons.setLayout(new FlowLayout());
        startButton = button("Start clock");
        buttons.add(startButton);
        buttons.add(button("Back to dashboard"));
        buttons.add(button("Save"));
        JPanel bottom = new JPanel();
        bottom.setLayout(new BorderLayout());
        bottom.add(message, BorderLayout.PAGE_START);
        bottom.add(buttons, BorderLayout.PAGE_END);
        Container pane = getContentPane();
        pane.setLayout(new BorderLayout());
        pane.add(new JLabel("Set the clock once. Afterwards, use Advance Minute.",
                SwingConstants.CENTER), BorderLayout.PAGE_START);
        pane.add(form, BorderLayout.CENTER);
        pane.add(bottom, BorderLayout.PAGE_END);
        refresh();
    }

    private JButton button(String label) {
        JButton button = new JButton(label);
        button.addActionListener(new ButtonHandler(label));
        return button;
    }

    public void refresh() {
        message.setText(app.status());
        startPicker.setEnabled(!app.clock().isSet());
        startButton.setEnabled(!app.clock().isSet());
    }

    private class ButtonHandler implements ActionListener {
        private String action;

        public ButtonHandler(String action) {
            this.action = action;
        }

        public void actionPerformed(ActionEvent event) {
            try {
                if (action.equals("Start clock")) {
                    // Keep the original text: committing can normalize malformed input.
                    String chosenTime = dateEditor.getTextField().getText();
                    startPicker.commitEdit();
                    if (app.initializeClock(chosenTime)) {
                        app.showAdmin(0);
                    }
                } else if (action.equals("Back to dashboard")) {
                    app.showAdmin(0);
                } else {
                    app.save();
                }
            } catch (ParseException exception) {
                app.report("Choose a valid date and time as dd/MM/yyyy HH:mm.");
            }
            refresh();
            if (navigation != null) {
                navigation.actionPerformed(event);
            }
        }
    }
}
