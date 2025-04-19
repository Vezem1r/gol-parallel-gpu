package org.example;

import org.example.gui.GameOfLifeGUI;
import org.example.model.Grid;
import org.example.rle.RLEParser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * A launcher for the Game of Life application with a user-friendly interface
 * for selecting RLE patterns and simulation modes.
 */
public class GameOfLifeLauncher extends JFrame {
    private JComboBox<String> modeSelector;
    private JSpinner threadsSpinner;
    private JButton selectFileButton;
    private JButton startButton;
    private JLabel fileLabel;
    private File selectedFile;
    private JPanel mainPanel;

    public GameOfLifeLauncher() {
        setTitle("Game of Life Launcher");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(600, 400));
        setPreferredSize(new Dimension(600, 400));
        setLocationRelativeTo(null);

        initComponents();
        layoutComponents();
        pack();
    }

    private void initComponents() {
        // Create components with larger font
        Font largerFont = new Font("Arial", Font.PLAIN, 14);

        modeSelector = new JComboBox<>(new String[]{"Sequential", "Parallel", "GPU"});
        modeSelector.setFont(largerFont);

        SpinnerNumberModel threadsModel = new SpinnerNumberModel(
                Runtime.getRuntime().availableProcessors(), // initial value
                1, // min
                Runtime.getRuntime().availableProcessors() * 2, // max
                1 // step
        );
        threadsSpinner = new JSpinner(threadsModel);
        threadsSpinner.setFont(largerFont);
        // Make spinner wider
        JComponent editor = threadsSpinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JSpinner.DefaultEditor spinnerEditor = (JSpinner.DefaultEditor)editor;
            spinnerEditor.getTextField().setColumns(4);
            spinnerEditor.getTextField().setFont(largerFont);
        }

        selectFileButton = new JButton("Select RLE Pattern");
        selectFileButton.setFont(largerFont);
        selectFileButton.setPreferredSize(new Dimension(200, 40));

        startButton = new JButton("Start Simulation");
        startButton.setFont(new Font("Arial", Font.BOLD, 16));
        startButton.setPreferredSize(new Dimension(200, 50));
        startButton.setEnabled(false); // Disable until file is selected

        fileLabel = new JLabel("No file selected");
        fileLabel.setFont(largerFont);

        // Add listeners
        selectFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                FileNameExtensionFilter filter = new FileNameExtensionFilter(
                        "RLE Files", "rle");
                fileChooser.setFileFilter(filter);

                int result = fileChooser.showOpenDialog(GameOfLifeLauncher.this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    selectedFile = fileChooser.getSelectedFile();
                    fileLabel.setText("Selected: " + selectedFile.getName());
                    startButton.setEnabled(true);
                }
            }
        });

        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (selectedFile != null) {
                    startSimulation();
                }
            }
        });

        // Main panel with border for padding
        mainPanel = new JPanel();
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
    }

    private void layoutComponents() {
        mainPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Section title
        JLabel titleLabel = new JLabel("Game of Life Configuration");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(titleLabel, gbc);

        // Reset gridwidth
        gbc.gridwidth = 1;

        // Mode selector
        JLabel modeLabel = new JLabel("Simulation Mode:");
        modeLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(modeLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        mainPanel.add(modeSelector, gbc);

        // Threads spinner (only relevant for parallel mode)
        JLabel threadsLabel = new JLabel("Number of Threads:");
        threadsLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        mainPanel.add(threadsLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        mainPanel.add(threadsSpinner, gbc);

        // File selection
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(selectFileButton, gbc);

        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(fileLabel, gbc);

        // Separator
        JSeparator separator = new JSeparator();
        gbc.gridy = 5;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(20, 0, 20, 0);
        mainPanel.add(separator, gbc);

        // Reset insets
        gbc.insets = new Insets(10, 10, 10, 10);

        // Start button
        gbc.gridy = 6;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.weighty = 1.0;
        mainPanel.add(startButton, gbc);

        // Add main panel to frame
        setLayout(new BorderLayout());
        add(mainPanel, BorderLayout.CENTER);
    }

    private void startSimulation() {
        String mode = modeSelector.getSelectedItem().toString().toLowerCase();
        int threads = (Integer) threadsSpinner.getValue();

        try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

            Grid grid = RLEParser.parse(selectedFile);

            // Create and show the GUI
            SwingUtilities.invokeLater(() -> {
                GameOfLifeGUI gui = GameOfLifeGUI.createAndShow(grid, mode, threads);
                // Hide the launcher when simulation starts
                setCursor(Cursor.getDefaultCursor());
                setVisible(false);
            });

        } catch (Exception e) {
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this,
                    "Error loading pattern: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            // Set system look and feel
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

            // Increase default font size for all UI components
            setUIFont(new javax.swing.plaf.FontUIResource(
                    "Arial", Font.PLAIN, 14));

        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            GameOfLifeLauncher launcher = new GameOfLifeLauncher();
            launcher.setVisible(true);
        });
    }

    // Helper method to set default font
    public static void setUIFont(javax.swing.plaf.FontUIResource f) {
        java.util.Enumeration keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof javax.swing.plaf.FontUIResource) {
                UIManager.put(key, f);
            }
        }
    }
}