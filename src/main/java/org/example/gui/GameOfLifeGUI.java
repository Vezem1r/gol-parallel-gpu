package org.example.gui;

import org.example.model.Grid;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameOfLifeGUI extends JFrame {
    private final Grid grid;
    private final GridPanel gridPanel;
    private Timer animationTimer = null;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final JButton startStopButton;
    private final JButton stepButton;
    private final JLabel statusLabel;
    private final JSlider speedSlider;
    private final JLabel generationCountLabel;
    private final JCheckBox showGridLinesCheckbox;
    private long lastStepTime = 0;
    private int generation = 0;
    private int cellSize = 10; // Default cell size
    private Color aliveColor = Color.BLACK;
    private Color deadColor = Color.WHITE;
    private Color gridColor = Color.LIGHT_GRAY;

    // Variables to store the simulation settings
    private String mode;
    private int threads;

    public GameOfLifeGUI(Grid grid, String mode, int threads) {
        this.grid = grid;
        this.mode = mode;
        this.threads = threads;

        // Calculate optimal cell size based on grid dimensions
        adjustCellSize();

        // Set up the UI
        setTitle("Conway's Game of Life - " + mode.toUpperCase() + " Mode");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Create components with larger font
        Font largerFont = new Font("Arial", Font.PLAIN, 14);

        // Create grid panel
        gridPanel = new GridPanel();

        // Create control components
        JPanel controlPanel = new JPanel();
        controlPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        startStopButton = new JButton("Start");
        startStopButton.setFont(largerFont);
        startStopButton.setPreferredSize(new Dimension(100, 40));

        stepButton = new JButton("Step");
        stepButton.setFont(largerFont);
        stepButton.setPreferredSize(new Dimension(100, 40));

        speedSlider = new JSlider(JSlider.HORIZONTAL, 1, 60, 10);
        speedSlider.setMajorTickSpacing(10);
        speedSlider.setMinorTickSpacing(5);
        speedSlider.setPaintTicks(true);
        speedSlider.setPaintLabels(true);
        speedSlider.setFont(new Font("Arial", Font.PLAIN, 12));

        generationCountLabel = new JLabel("Generation: 0");
        generationCountLabel.setFont(largerFont);

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(largerFont);

        showGridLinesCheckbox = new JCheckBox("Show Grid Lines");
        showGridLinesCheckbox.setFont(largerFont);
        showGridLinesCheckbox.setSelected(true);

        // Add action listeners
        startStopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isRunning.get()) {
                    stopAnimation();
                } else {
                    startAnimation();
                }
            }
        });

        stepButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                step();
            }
        });

        showGridLinesCheckbox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                gridPanel.repaint();
            }
        });

        speedSlider.addChangeListener(e -> {
            if (animationTimer.isRunning()) {
                int fps = speedSlider.getValue();
                animationTimer.setDelay(1000 / fps);
                statusLabel.setText("Speed: " + fps + " FPS");
            }
        });

        // Layout UI
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Configure gridPanel with scrolling for large grids
        JScrollPane scrollPane = new JScrollPane(gridPanel);
        scrollPane.setPreferredSize(new Dimension(800, 600));
        scrollPane.setBorder(BorderFactory.createLoweredBevelBorder());
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Create control panel with better layout
        controlPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Speed control panel
        JPanel speedPanel = new JPanel(new BorderLayout(5, 5));
        speedPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Simulation Speed",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                largerFont));

        JLabel speedLabel = new JLabel("FPS:");
        speedLabel.setFont(largerFont);
        speedPanel.add(speedLabel, BorderLayout.WEST);
        speedPanel.add(speedSlider, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        buttonPanel.add(startStopButton);
        buttonPanel.add(stepButton);

        // Status panel
        JPanel statusPanel = new JPanel(new BorderLayout(5, 5));
        statusPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Status",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                largerFont));
        statusPanel.add(generationCountLabel, BorderLayout.WEST);
        statusPanel.add(statusLabel, BorderLayout.EAST);

        // Options panel
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionsPanel.add(showGridLinesCheckbox);

        // Add components to control panel
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        controlPanel.add(buttonPanel, gbc);

        gbc.gridy = 1;
        controlPanel.add(speedPanel, gbc);

        gbc.gridy = 2;
        controlPanel.add(statusPanel, gbc);

        gbc.gridy = 3;
        controlPanel.add(optionsPanel, gbc);

        mainPanel.add(controlPanel, BorderLayout.SOUTH);

        getContentPane().add(mainPanel);

        // Configure animation timer
        animationTimer = new Timer(1000 / 10, e -> step());

        // Add window listener for cleanup
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (animationTimer.isRunning()) {
                    animationTimer.stop();
                }
                grid.cleanup();
            }
        });

        pack();
        setLocationRelativeTo(null);

        // Set icon
        try {
            Image icon = createGameOfLifeIcon(32);
            setIconImage(icon);
        } catch (Exception e) {
            // Ignore if icon creation fails
        }
    }

    private Image createGameOfLifeIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // Draw a simple glider pattern as the icon
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, size, size);

        int cellSize = size / 5;
        g2d.setColor(Color.BLACK);

        // Glider pattern
        g2d.fillRect(cellSize * 1, cellSize * 0, cellSize, cellSize);
        g2d.fillRect(cellSize * 2, cellSize * 1, cellSize, cellSize);
        g2d.fillRect(cellSize * 0, cellSize * 2, cellSize, cellSize);
        g2d.fillRect(cellSize * 1, cellSize * 2, cellSize, cellSize);
        g2d.fillRect(cellSize * 2, cellSize * 2, cellSize, cellSize);

        g2d.dispose();
        return image;
    }

    private void adjustCellSize() {
        // Calculate optimal cell size based on grid dimensions
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int maxWidth = (int)(screenSize.width * 0.8);
        int maxHeight = (int)(screenSize.height * 0.8);

        int optimalWidthSize = maxWidth / grid.getWidth();
        int optimalHeightSize = maxHeight / grid.getHeight();

        // Choose the smaller dimension to ensure it fits
        cellSize = Math.min(optimalWidthSize, optimalHeightSize);

        // Ensure cell size is at least 2 pixels and at most 20 pixels
        cellSize = Math.max(2, Math.min(cellSize, 20));
    }

    public void step() {
        generation++;
        generationCountLabel.setText("Generation: " + generation);

        long currentTime = System.currentTimeMillis();
        if (lastStepTime > 0) {
            long stepTime = currentTime - lastStepTime;
            if (stepTime > 0) {
                int fps = (int)(1000 / stepTime);
                statusLabel.setText("Speed: " + fps + " FPS");
            }
        }
        lastStepTime = currentTime;

        // Update the simulation based on the selected mode
        if ("parallel".equals(mode)) {
            grid.stepParallel(threads);
        } else if ("gpu".equals(mode)) {
            grid.stepGpu();
        } else {
            // Default to sequential
            grid.stepSequential();
        }

        // Repaint the grid
        gridPanel.repaint();
    }

    public void startAnimation() {
        isRunning.set(true);
        startStopButton.setText("Stop");
        startStopButton.setBackground(new Color(255, 100, 100));
        stepButton.setEnabled(false);

        // Update timer delay based on slider value
        int fps = speedSlider.getValue();
        int delay = 1000 / fps;
        animationTimer.setDelay(delay);
        statusLabel.setText("Speed: " + fps + " FPS");
        animationTimer.start();
    }

    public void stopAnimation() {
        isRunning.set(false);
        startStopButton.setText("Start");
        startStopButton.setBackground(new Color(100, 255, 100));
        stepButton.setEnabled(true);
        animationTimer.stop();
        statusLabel.setText("Paused");
    }

    // Inner class for grid rendering
    private class GridPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // Set up anti-aliasing
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw background
            g2d.setColor(deadColor);
            g2d.fillRect(0, 0, getWidth(), getHeight());

            // Draw cells
            boolean[][] currentState = grid.getCurrentState();
            int width = grid.getWidth();
            int height = grid.getHeight();

            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    if (currentState[i][j]) {
                        g2d.setColor(aliveColor);
                        g2d.fillRect(j * cellSize, i * cellSize, cellSize, cellSize);
                    }

                    // Draw grid lines if enabled
                    if (showGridLinesCheckbox.isSelected()) {
                        g2d.setColor(gridColor);
                        g2d.drawRect(j * cellSize, i * cellSize, cellSize, cellSize);
                    }
                }
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(grid.getWidth() * cellSize, grid.getHeight() * cellSize);
        }
    }

    // Factory method to create a GUI with appropriate mode
    public static GameOfLifeGUI createAndShow(Grid grid, String mode, int threads) {
        GameOfLifeGUI gui = new GameOfLifeGUI(grid, mode, threads);

        // Show the GUI
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                gui.setVisible(true);
            }
        });

        return gui;
    }
}