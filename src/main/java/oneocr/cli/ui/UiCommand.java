package oneocr.cli.ui;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import oneocr.cli.OcrTool;
import oneocr.cli.folder.FolderOcrCommand;
import oneocr.cli.pdf.PdfOcrCommand;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.List;

/**
 * UI subcommand that provides a Swing interface for OCR operations
 * Delegates actual work to existing subcommands (folder, pdf, image)
 */
@Command(
    name = "ui", 
    description = "Launch graphical user interface for OCR operations",
    mixinStandardHelpOptions = true
)
public class UiCommand implements Callable<Integer> {

    @picocli.CommandLine.ParentCommand
    private OcrTool parentCommand;

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    private boolean verbose = false;

    private ExecutionHistory executionHistory;
    private CountDownLatch uiClosedLatch;
    private JFrame mainFrame;

    public enum ProcessingMode {
        FOLDER("Process Folder", "Process all images and PDFs in a folder"),
        PDF("Process PDF", "Process a single PDF file"),
        IMAGE("Process Image", "Process a single image file");

        private final String displayName;
        private final String description;

        ProcessingMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    @Override
    public Integer call() throws Exception {
        if (verbose) {
            System.out.println("Starting UI command...");
        }

        // Initialize execution history
        try {
            executionHistory = new ExecutionHistory();
            if (verbose) {
                System.out.println("Execution history initialized");
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not initialize execution history: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
        }

        // Set system look and feel for better UI appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            if (verbose) {
                System.out.println("System look and feel set");
            }
        } catch (Exception e) {
            if (verbose) {
                System.err.println("Could not set system look and feel: " + e.getMessage());
            }
        }

        // Create a latch to wait for UI to close
        uiClosedLatch = new CountDownLatch(1);

        SwingUtilities.invokeLater(() -> {
            try {
                createAndShowUI();
                if (verbose) {
                    System.out.println("UI created and shown");
                }
            } catch (Exception e) {
                System.err.println("Error creating UI: " + e.getMessage());
                if (verbose) {
                    e.printStackTrace();
                }
                uiClosedLatch.countDown(); // Release the latch on error
            }
        });

        // Wait for the UI to be closed
        try {
            if (verbose) {
                System.out.println("Waiting for UI to close...");
            }
            uiClosedLatch.await();
            if (verbose) {
                System.out.println("UI closed, exiting");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 1;
        }

        return 0;
    }

    private void createAndShowUI() {
        mainFrame = new JFrame("Windows 11 OneOCR - GUI");
        mainFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        // Handle window closing to release the latch
        mainFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (verbose) {
                    System.out.println("Window closing event received");
                }
                mainFrame.dispose();
                uiClosedLatch.countDown();
                System.exit(0);
            }
        });
        mainFrame.setSize(700, 600);
        mainFrame.setLocationRelativeTo(null);

        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Title
        JLabel titleLabel = new JLabel("Windows 11 OneOCR", SwingConstants.CENTER);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // Center panel with tabs for different modes
        JTabbedPane tabbedPane = new JTabbedPane();

        // Folder processing tab
        JPanel folderPanel = createFolderPanel();
        tabbedPane.addTab("📁 Folder", folderPanel);

        // PDF processing tab
        JPanel pdfPanel = createPdfPanel();
        tabbedPane.addTab("📄 PDF", pdfPanel);

        // Image processing tab
        JPanel imagePanel = createImagePanel();
        tabbedPane.addTab("🖼️ Image", imagePanel);

        // History tab
        JPanel historyPanel = createHistoryPanel();
        tabbedPane.addTab("📋 History", historyPanel);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Status bar
        JLabel statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createLoweredBevelBorder());
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        mainFrame.add(mainPanel);
        mainFrame.setVisible(true);
    }

    private JPanel createFolderPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Input folder selection
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Input Folder:"), gbc);

        JTextField inputFolderField = new JTextField(30);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(inputFolderField, gbc);

        JButton browseInputButton = new JButton("Browse");
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(browseInputButton, gbc);

        // Output folder selection (optional)
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Output Folder (optional):"), gbc);

        JTextField outputFolderField = new JTextField(30);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(outputFolderField, gbc);

        JButton browseOutputButton = new JButton("Browse");
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(browseOutputButton, gbc);

        // Options
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Threads:"), gbc);

        JSpinner threadsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 8, 1));
        gbc.gridx = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(threadsSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        JCheckBox recursiveCheckBox = new JCheckBox("Recursive (include subfolders)");
        panel.add(recursiveCheckBox, gbc);

        gbc.gridy = 4;
        JCheckBox verboseCheckBox = new JCheckBox("Verbose output");
        panel.add(verboseCheckBox, gbc);

        gbc.gridy = 5;
        JCheckBox svgCheckBox = new JCheckBox("Generate SVG overlays");
        panel.add(svgCheckBox, gbc);

        // Process button
        JButton processButton = new JButton("🚀 Process Folder");
        processButton.setFont(processButton.getFont().deriveFont(Font.BOLD));
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(20, 5, 5, 5);
        panel.add(processButton, gbc);

        // Event handlers
        browseInputButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                inputFolderField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        browseOutputButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                outputFolderField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        processButton.addActionListener(e -> {
            String inputFolder = inputFolderField.getText().trim();
            if (inputFolder.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Please select an input folder", "Input Required", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Save to history and execute
            FolderExecutionConfig config = new FolderExecutionConfig(
                inputFolder,
                outputFolderField.getText().trim().isEmpty() ? null : outputFolderField.getText().trim(),
                (Integer) threadsSpinner.getValue(),
                recursiveCheckBox.isSelected(),
                verboseCheckBox.isSelected(),
                svgCheckBox.isSelected()
            );

            executeInBackground(processButton, () -> executeFolderCommand(config));
        });

        return panel;
    }

    private JPanel createPdfPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // PDF file selection
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("PDF File:"), gbc);

        JTextField pdfFileField = new JTextField(30);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(pdfFileField, gbc);

        JButton browsePdfButton = new JButton("Browse");
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(browsePdfButton, gbc);

        // Output directory selection (optional)
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Output Directory (optional):"), gbc);

        JTextField outputDirField = new JTextField(30);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(outputDirField, gbc);

        JButton browseOutputDirButton = new JButton("Browse");
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(browseOutputDirButton, gbc);

        // Options
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Threads:"), gbc);

        JSpinner threadsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 8, 1));
        gbc.gridx = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(threadsSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("Image Format:"), gbc);

        JComboBox<String> imageFormatCombo = new JComboBox<>(new String[]{"webp", "jpg", "png", "avif"});
        gbc.gridx = 1;
        panel.add(imageFormatCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        panel.add(new JLabel("Target DPI (optional):"), gbc);

        JSpinner dpiSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 300, 10));
        gbc.gridx = 1;
        panel.add(dpiSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        JCheckBox verboseCheckBox = new JCheckBox("Verbose output");
        panel.add(verboseCheckBox, gbc);

        // Process button
        JButton processButton = new JButton("🚀 Process PDF");
        processButton.setFont(processButton.getFont().deriveFont(Font.BOLD));
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(20, 5, 5, 5);
        panel.add(processButton, gbc);

        // Event handlers
        browsePdfButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("PDF Files", "pdf"));
            if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                pdfFileField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        browseOutputDirButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                outputDirField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        processButton.addActionListener(e -> {
            String pdfFile = pdfFileField.getText().trim();
            if (pdfFile.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Please select a PDF file", "Input Required", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Save to history and execute
            PdfExecutionConfig config = new PdfExecutionConfig(
                pdfFile,
                outputDirField.getText().trim().isEmpty() ? null : outputDirField.getText().trim(),
                (Integer) threadsSpinner.getValue(),
                (String) imageFormatCombo.getSelectedItem(),
                (Integer) dpiSpinner.getValue() == 0 ? null : (Integer) dpiSpinner.getValue(),
                verboseCheckBox.isSelected()
            );

            executeInBackground(processButton, () -> executePdfCommand(config));
        });

        return panel;
    }

    private JPanel createImagePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Image file selection
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Image File:"), gbc);

        JTextField imageFileField = new JTextField(30);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(imageFileField, gbc);

        JButton browseImageButton = new JButton("Browse");
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(browseImageButton, gbc);

        // Options
        gbc.gridx = 0; gbc.gridy = 1;
        JCheckBox generateSvgCheckBox = new JCheckBox("Generate SVG visualization");
        panel.add(generateSvgCheckBox, gbc);

        gbc.gridy = 2;
        JCheckBox generateJsonCheckBox = new JCheckBox("Generate JSON output");
        panel.add(generateJsonCheckBox, gbc);

        gbc.gridy = 3;
        JCheckBox generateXhtmlCheckBox = new JCheckBox("Generate XHTML output");
        panel.add(generateXhtmlCheckBox, gbc);

        gbc.gridy = 4;
        JCheckBox verboseCheckBox = new JCheckBox("Verbose output");
        panel.add(verboseCheckBox, gbc);

        // Process button
        JButton processButton = new JButton("🚀 Process Image");
        processButton.setFont(processButton.getFont().deriveFont(Font.BOLD));
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(20, 5, 5, 5);
        panel.add(processButton, gbc);

        // Event handlers
        browseImageButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Image Files", "jpg", "jpeg", "png", "bmp", "tiff", "webp"));
            if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                imageFileField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        processButton.addActionListener(e -> {
            String imageFile = imageFileField.getText().trim();
            if (imageFile.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Please select an image file", "Input Required", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Save to history and execute
            ImageExecutionConfig config = new ImageExecutionConfig(
                imageFile,
                generateSvgCheckBox.isSelected(),
                generateJsonCheckBox.isSelected(),
                generateXhtmlCheckBox.isSelected(),
                verboseCheckBox.isSelected()
            );

            executeInBackground(processButton, () -> executeImageCommand(config));
        });

        return panel;
    }

    private JPanel createHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // History list
        DefaultListModel<ExecutionHistory.ExecutionRecord> historyModel = new DefaultListModel<>();
        JList<ExecutionHistory.ExecutionRecord> historyList = new JList<>(historyModel);
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Custom renderer to show display names with HTML support and proper sizing
        historyList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                
                if (value instanceof ExecutionHistory.ExecutionRecord) {
                    String displayText = ((ExecutionHistory.ExecutionRecord) value).getDisplayName();
                    label.setText(displayText);
                    
                    // Set preferred size to accommodate multi-line text
                    label.setPreferredSize(new Dimension(0, 80)); // Fixed height for multi-line entries
                    label.setVerticalAlignment(SwingConstants.TOP);
                    
                    // Enable word wrapping for HTML content
                    label.setVerticalTextPosition(SwingConstants.TOP);
                }
                
                return label;
            }
        });
        
        // Set fixed cell height for multi-line entries
        historyList.setFixedCellHeight(80);
        
        JScrollPane scrollPane = new JScrollPane(historyList);
        scrollPane.setPreferredSize(new Dimension(600, 300));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton rerunButton = new JButton("Re-run Selected");
        JButton refreshButton = new JButton("Refresh");
        JButton clearHistoryButton = new JButton("Clear History");
        
        buttonPanel.add(rerunButton);
        buttonPanel.add(refreshButton);
        buttonPanel.add(clearHistoryButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        // Load history
        loadHistoryIntoList(historyModel);
        
        // Event handlers
        rerunButton.addActionListener(e -> {
            ExecutionHistory.ExecutionRecord selected = historyList.getSelectedValue();
            if (selected != null) {
                executeInBackground(rerunButton, () -> rerunHistoryRecord(selected));
            } else {
                JOptionPane.showMessageDialog(panel, "Please select a history item to re-run", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        
        refreshButton.addActionListener(e -> {
            loadHistoryIntoList(historyModel);
        });
        
        clearHistoryButton.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(panel, 
                "Are you sure you want to clear all execution history?", 
                "Clear History", 
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
                
            if (result == JOptionPane.YES_OPTION) {
                try {
                    if (executionHistory != null) {
                        executionHistory.clearHistory();
                        historyModel.clear();
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(panel, "Error clearing history: " + ex.getMessage(), 
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        
        return panel;
    }
    
    private void loadHistoryIntoList(DefaultListModel<ExecutionHistory.ExecutionRecord> historyModel) {
        historyModel.clear();
        if (executionHistory != null) {
            try {
                List<ExecutionHistory.ExecutionRecord> records = executionHistory.loadHistory();
                for (ExecutionHistory.ExecutionRecord record : records) {
                    historyModel.addElement(record);
                }
            } catch (Exception e) {
                // Silently continue if history cannot be loaded
            }
        }
    }
    
    private void rerunHistoryRecord(ExecutionHistory.ExecutionRecord record) {
        try {
            if (record instanceof ExecutionHistory.FolderExecutionRecord) {
                ExecutionHistory.FolderExecutionRecord folderRecord = (ExecutionHistory.FolderExecutionRecord) record;
                FolderExecutionConfig config = new FolderExecutionConfig(
                    folderRecord.inputFolder,
                    folderRecord.outputFolder,
                    folderRecord.threads,
                    folderRecord.recursive,
                    folderRecord.verbose,
                    folderRecord.generateSvg
                );
                executeFolderCommand(config);
            } else if (record instanceof ExecutionHistory.PdfExecutionRecord) {
                ExecutionHistory.PdfExecutionRecord pdfRecord = (ExecutionHistory.PdfExecutionRecord) record;
                PdfExecutionConfig config = new PdfExecutionConfig(
                    pdfRecord.pdfFile,
                    pdfRecord.outputDir,
                    pdfRecord.threads,
                    pdfRecord.imageFormat,
                    pdfRecord.targetDpi,
                    pdfRecord.verbose
                );
                executePdfCommand(config);
            } else if (record instanceof ExecutionHistory.ImageExecutionRecord) {
                ExecutionHistory.ImageExecutionRecord imageRecord = (ExecutionHistory.ImageExecutionRecord) record;
                ImageExecutionConfig config = new ImageExecutionConfig(
                    imageRecord.imageFile,
                    imageRecord.generateSvg,
                    imageRecord.generateJson,
                    imageRecord.generateXhtml,
                    imageRecord.verbose
                );
                executeImageCommand(config);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error re-running command: " + e.getMessage(), e);
        }
    }

    private void executeInBackground(JButton button, Runnable task) {
        // Disable the button to prevent multiple clicks
        button.setEnabled(false);
        button.setText("Processing...");
        
        new Thread(() -> {
            try {
                // Hide the UI immediately when processing starts (but don't count down latch yet)
                SwingUtilities.invokeLater(() -> {
                    if (verbose) {
                        System.out.println("Hiding UI as processing starts");
                    }
                    mainFrame.setVisible(false);
                });
                
                task.run();
                
                // After task completion, dispose and count down the latch
                SwingUtilities.invokeLater(() -> {
                    if (verbose) {
                        System.out.println("Processing completed, closing application");
                    }
                    mainFrame.dispose();
                    uiClosedLatch.countDown();
                });
                
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    // Show UI again and re-enable button if there was an error
                    mainFrame.setVisible(true);
                    button.setEnabled(true);
                    button.setText(button.getText().replace("Processing...", "Process"));
                    JOptionPane.showMessageDialog(null, "Error: " + e.getMessage(), "Execution Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void executeFolderCommand(FolderExecutionConfig config) {
        try {
            // Save to execution history
            if (executionHistory != null) {
                try {
                    ExecutionHistory.FolderExecutionRecord record = new ExecutionHistory.FolderExecutionRecord(
                        config.inputFolder, config.outputFolder, config.threads, 
                        config.recursive, config.verbose, config.generateSvg);
                    executionHistory.addRecord(record);
                } catch (Exception e) {
                    // Continue execution even if history save fails
                }
            }
            
            // Create command line arguments in correct order: global options first, then subcommand and its options
            java.util.List<String> fullArgs = new java.util.ArrayList<>();
            
            // Global options must come first (before subcommand)
            if (config.threads > 1) {
                fullArgs.add("--threads");
                fullArgs.add(String.valueOf(config.threads));
            }
            
            // Then the subcommand
            fullArgs.add("folder");
            fullArgs.add(config.inputFolder);
            
            // Then subcommand options
            if (config.outputFolder != null) {
                fullArgs.add("-o");
                fullArgs.add(config.outputFolder);
            }
            
            if (config.recursive) {
                fullArgs.add("--recursive");
            }
            
            if (config.verbose) {
                fullArgs.add("--verbose");
            }
            
            if (config.generateSvg) {
                fullArgs.add("--svg");
            }
            
            // Create the parent OcrTool command 
            OcrTool parentTool = new OcrTool();
            CommandLine parentCmdLine = new CommandLine(parentTool);
            
            if (verbose) {
                System.out.println("Executing folder command with args: " + fullArgs);
            }
            
            int exitCode = parentCmdLine.execute(fullArgs.toArray(new String[0]));
            
            SwingUtilities.invokeLater(() -> {
                String message = exitCode == 0 ? "Folder processing completed successfully!" : "Folder processing completed with errors.";
                int messageType = exitCode == 0 ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE;
                JOptionPane.showMessageDialog(null, message, "Processing Complete", messageType);
            });
            
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Error executing folder command: " + e.getMessage(), "Execution Error", JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    private void executePdfCommand(PdfExecutionConfig config) {
        try {
            // Save to execution history
            if (executionHistory != null) {
                try {
                    ExecutionHistory.PdfExecutionRecord record = new ExecutionHistory.PdfExecutionRecord(
                        config.pdfFile, config.outputDir, config.threads, 
                        config.imageFormat, config.targetDpi, config.verbose);
                    executionHistory.addRecord(record);
                } catch (Exception e) {
                    // Continue execution even if history save fails
                }
            }
            
            // Create command line arguments in correct order: global options first, then subcommand and its options
            java.util.List<String> fullArgs = new java.util.ArrayList<>();
            
            // Global options must come first (before subcommand)
            if (config.threads > 1) {
                fullArgs.add("--threads");
                fullArgs.add(String.valueOf(config.threads));
            }
            
            // Then the subcommand
            fullArgs.add("pdf");
            fullArgs.add(config.pdfFile);
            
            // Then subcommand options
            if (config.outputDir != null) {
                fullArgs.add("-o");
                fullArgs.add(config.outputDir);
            }
            
            fullArgs.add("--image-format");
            fullArgs.add(config.imageFormat);
            
            if (config.targetDpi != null) {
                fullArgs.add("--target-dpi");
                fullArgs.add(config.targetDpi.toString());
            }
            
            if (config.verbose) {
                fullArgs.add("--verbose");
            }
            
            // Create the parent OcrTool command
            OcrTool parentTool = new OcrTool();
            CommandLine parentCmdLine = new CommandLine(parentTool);
            
            if (verbose) {
                System.out.println("Executing PDF command with args: " + fullArgs);
            }
            
            int exitCode = parentCmdLine.execute(fullArgs.toArray(new String[0]));
            
            SwingUtilities.invokeLater(() -> {
                String message = exitCode == 0 ? "PDF processing completed successfully!" : "PDF processing completed with errors.";
                int messageType = exitCode == 0 ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE;
                JOptionPane.showMessageDialog(null, message, "Processing Complete", messageType);
            });
            
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Error executing PDF command: " + e.getMessage(), "Execution Error", JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    private void executeImageCommand(ImageExecutionConfig config) {
        try {
            // Save to execution history
            if (executionHistory != null) {
                try {
                    ExecutionHistory.ImageExecutionRecord record = new ExecutionHistory.ImageExecutionRecord(
                        config.imageFile, config.generateSvg, config.generateJson, 
                        config.generateXhtml, config.verbose);
                    executionHistory.addRecord(record);
                } catch (Exception e) {
                    // Continue execution even if history save fails
                }
            }
            
            // Create command line args for single image processing
            java.util.List<String> args = new java.util.ArrayList<>();
            args.add(config.imageFile);
            
            if (config.generateSvg) {
                args.add("--svg");
            }
            
            if (config.generateJson) {
                args.add("--json");
            }
            
            if (config.generateXhtml) {
                args.add("--xhtml");
            }
            
            if (config.verbose) {
                args.add("--verbose");
            }
            
            // Create the parent OcrTool command (image processing doesn't use threads parameter)
            OcrTool imageCommand = new OcrTool();
            CommandLine cmdLine = new CommandLine(imageCommand);
            int exitCode = cmdLine.execute(args.toArray(new String[0]));
            
            SwingUtilities.invokeLater(() -> {
                String message = exitCode == 0 ? "Image processing completed successfully!" : "Image processing completed with errors.";
                int messageType = exitCode == 0 ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE;
                JOptionPane.showMessageDialog(null, message, "Processing Complete", messageType);
            });
            
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Error executing image command: " + e.getMessage(), "Execution Error", JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    // Configuration classes for different execution modes
    private static class FolderExecutionConfig {
        final String inputFolder;
        final String outputFolder;
        final int threads;
        final boolean recursive;
        final boolean verbose;
        final boolean generateSvg;

        FolderExecutionConfig(String inputFolder, String outputFolder, int threads, boolean recursive, boolean verbose, boolean generateSvg) {
            this.inputFolder = inputFolder;
            this.outputFolder = outputFolder;
            this.threads = threads;
            this.recursive = recursive;
            this.verbose = verbose;
            this.generateSvg = generateSvg;
        }
    }

    private static class PdfExecutionConfig {
        final String pdfFile;
        final String outputDir;
        final int threads;
        final String imageFormat;
        final Integer targetDpi;
        final boolean verbose;

        PdfExecutionConfig(String pdfFile, String outputDir, int threads, String imageFormat, Integer targetDpi, boolean verbose) {
            this.pdfFile = pdfFile;
            this.outputDir = outputDir;
            this.threads = threads;
            this.imageFormat = imageFormat;
            this.targetDpi = targetDpi;
            this.verbose = verbose;
        }
    }

    private static class ImageExecutionConfig {
        final String imageFile;
        final boolean generateSvg;
        final boolean generateJson;
        final boolean generateXhtml;
        final boolean verbose;

        ImageExecutionConfig(String imageFile, boolean generateSvg, boolean generateJson, boolean generateXhtml, boolean verbose) {
            this.imageFile = imageFile;
            this.generateSvg = generateSvg;
            this.generateJson = generateJson;
            this.generateXhtml = generateXhtml;
            this.verbose = verbose;
        }
    }
}