package nl.esciencecenter.visualization.esalsa;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;

import javax.media.opengl.awt.GLCanvas;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicSliderUI;

import nl.esciencecenter.neon.NeonInterfacePanel;
import nl.esciencecenter.neon.swing.CustomJSlider;
import nl.esciencecenter.neon.swing.GoggleSwing;
import nl.esciencecenter.neon.swing.RangeSlider;
import nl.esciencecenter.neon.swing.RangeSliderUI;
import nl.esciencecenter.neon.swing.SimpleImageIcon;
import nl.esciencecenter.visualization.esalsa.data.SurfaceTextureDescription;
import nl.esciencecenter.visualization.esalsa.data.TimedPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImauPanel extends NeonInterfacePanel {
    public static enum TweakState {
        NONE, DATA, VISUAL, MOVIE
    }

    private final ImauSettings settings = ImauSettings.getInstance();
    private final static Logger logger = LoggerFactory.getLogger(ImauPanel.class);

    private static final long serialVersionUID = 1L;

    protected CustomJSlider timeBar;

    protected JFormattedTextField frameCounter, stepSizeField;
    private TweakState currentConfigState = TweakState.DATA;

    private final JPanel configPanel;

    private final JPanel dataConfig, visualConfig, movieConfig;

    private static TimedPlayer timer;

    private ArrayList<String> variables;

    protected GLCanvas glCanvas;

    private final boolean demomode = false;

    public ImauPanel() {
        setLayout(new BorderLayout(0, 0));

        variables = new ArrayList<String>();

        timeBar = new CustomJSlider(new BasicSliderUI(timeBar));
        timeBar.setValue(0);
        timeBar.setMajorTickSpacing(5);
        timeBar.setMinorTickSpacing(1);
        timeBar.setMaximum(0);
        timeBar.setMinimum(0);
        timeBar.setPaintTicks(true);
        timeBar.setSnapToTicks(true);

        timer = new TimedPlayer(timeBar, frameCounter);

        // Make the menu bar
        final JMenuBar menuBar = new JMenuBar();
        menuBar.setLayout(new BoxLayout(menuBar, BoxLayout.X_AXIS));

        final JMenu file = new JMenu("File");
        final JMenuItem open = new JMenuItem("Open");
        open.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                final File[] files = openFile();
                handleFiles(files);
            }
        });
        file.add(open);
        menuBar.add(file);
        menuBar.add(Box.createHorizontalGlue());

        if (!demomode) {
            final JMenu options = new JMenu("Options");
            final JMenuItem showDataTweakPanel = new JMenuItem("Show data configuration panel.");
            showDataTweakPanel.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent arg0) {
                    setTweakState(TweakState.DATA);
                }
            });
            options.add(showDataTweakPanel);

            menuBar.add(options);

            menuBar.add(Box.createHorizontalGlue());
        }

        final JMenuBar menuBar2 = new JMenuBar();

        ImageIcon nlescIcon = GoggleSwing.createResizedImageIcon("images/ESCIENCE_logo.png", "eScienceCenter Logo", 50,
                28);
        JLabel nlesclogo = new JLabel(nlescIcon);
        nlesclogo.setMinimumSize(new Dimension(300, 20));
        nlesclogo.setMaximumSize(new Dimension(311, 28));

        ImageIcon saraIcon = GoggleSwing.createResizedImageIcon("images/logo_sara.png", "SARA Logo", 50, 28);
        JLabel saralogo = new JLabel(saraIcon);
        saralogo.setMinimumSize(new Dimension(40, 20));
        saralogo.setMaximumSize(new Dimension(41, 28));
        menuBar2.add(Box.createHorizontalStrut(3));

        ImageIcon imauIcon = GoggleSwing.createResizedImageIcon("images/logo_imau.png", "IMAU Logo", 50, 28);
        JLabel imaulogo = new JLabel(imauIcon);
        imaulogo.setMinimumSize(new Dimension(50, 20));
        imaulogo.setMaximumSize(new Dimension(52, 28));

        menuBar2.add(Box.createHorizontalGlue());
        menuBar2.add(imaulogo);
        menuBar2.add(Box.createHorizontalStrut(20));
        menuBar2.add(saralogo);
        menuBar2.add(Box.createHorizontalStrut(20));
        menuBar2.add(nlesclogo);
        menuBar2.add(Box.createHorizontalGlue());

        Container menuContainer = new Container();
        menuContainer.setLayout(new BoxLayout(menuContainer, BoxLayout.Y_AXIS));

        if (!demomode) {
            menuContainer.add(menuBar);
        }
        menuContainer.add(menuBar2);

        add(menuContainer, BorderLayout.NORTH);

        // Make the "media player" panel
        final JPanel bottomPanel = createBottomPanel();

        // Add the tweaks panels
        configPanel = new JPanel();
        add(configPanel, BorderLayout.WEST);
        configPanel.setLayout(new BoxLayout(configPanel, BoxLayout.Y_AXIS));
        configPanel.setPreferredSize(new Dimension(200, 0));
        configPanel.setVisible(false);

        dataConfig = new JPanel();
        dataConfig.setLayout(new BoxLayout(dataConfig, BoxLayout.Y_AXIS));
        dataConfig.setMinimumSize(configPanel.getPreferredSize());
        createDataTweakPanel();

        visualConfig = new JPanel();

        movieConfig = new JPanel();

        add(bottomPanel, BorderLayout.SOUTH);

        setTweakState(TweakState.DATA);

        setVisible(true);
    }

    void close() {
        // glCanvas.destroy();
    }

    public Point getCanvasLocation() {
        Point topLeft = glCanvas.getLocation();
        return topLeft;
    }

    private JPanel createBottomPanel() {
        final JPanel bottomPanel = new JPanel();
        bottomPanel.setFocusCycleRoot(true);
        bottomPanel.setFocusTraversalPolicy(new FocusTraversalPolicy() {
            @Override
            public Component getComponentAfter(Container aContainer, Component aComponent) {
                return null;
            }

            @Override
            public Component getComponentBefore(Container aContainer, Component aComponent) {
                return null;
            }

            @Override
            public Component getDefaultComponent(Container aContainer) {
                return null;
            }

            @Override
            public Component getFirstComponent(Container aContainer) {
                return null;
            }

            // No focus traversal here, as it makes stuff go bad (some things
            // react on focus).
            @Override
            public Component getLastComponent(Container aContainer) {
                return null;
            }
        });

        final JButton oneForwardButton = GoggleSwing.createImageButton("images/media-playback-oneforward.png", "Next",
                null);
        final JButton oneBackButton = GoggleSwing.createImageButton("images/media-playback-onebackward.png",
                "Previous", null);
        final JButton rewindButton = GoggleSwing.createImageButton("images/media-playback-rewind.png", "Rewind", null);
        final JButton screenshotButton = GoggleSwing.createImageButton("images/camera.png", "Screenshot", null);
        final JButton playButton = GoggleSwing.createImageButton("images/media-playback-start.png", "Start", null);
        final ImageIcon playIcon = GoggleSwing.createImageIcon("images/media-playback-start.png", "Start");
        final ImageIcon stopIcon = GoggleSwing.createImageIcon("images/media-playback-stop.png", "Start");

        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        final JPanel bottomPanel1 = new JPanel();
        final JPanel bottomPanel2 = new JPanel();
        bottomPanel1.setLayout(new BoxLayout(bottomPanel1, BoxLayout.X_AXIS));
        bottomPanel2.setLayout(new BoxLayout(bottomPanel2, BoxLayout.X_AXIS));

        stepSizeField = new JFormattedTextField();
        stepSizeField.setColumns(4);
        stepSizeField.setMaximumSize(new Dimension(40, 20));
        stepSizeField.setValue(settings.getTimestep());
        stepSizeField.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                final JFormattedTextField source = (JFormattedTextField) e.getSource();
                if (source.hasFocus()) {
                    if (source == stepSizeField) {
                        settings.setTimestep((Integer) ((Number) source.getValue()));
                    }
                }
            }
        });
        bottomPanel1.add(stepSizeField);

        screenshotButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                timer.setScreenshotNeeded(true);
            }
        });
        bottomPanel1.add(screenshotButton);

        rewindButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                timer.rewind();
                playButton.setIcon(playIcon);
                playButton.invalidate();
            }
        });
        bottomPanel1.add(rewindButton);

        oneBackButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                timer.oneBack();
                playButton.setIcon(playIcon);
                playButton.invalidate();
            }
        });
        bottomPanel1.add(oneBackButton);

        playButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (timer.isPlaying()) {
                    timer.stop();
                    playButton.setIcon(playIcon);
                    playButton.invalidate();
                } else {
                    timer.start();
                    playButton.setIcon(stopIcon);
                    playButton.invalidate();
                }
            }
        });
        bottomPanel1.add(playButton);

        oneForwardButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                timer.oneForward();
                playButton.setIcon(playIcon);
                playButton.invalidate();
            }
        });
        bottomPanel1.add(oneForwardButton);

        frameCounter = new JFormattedTextField();
        frameCounter.setValue(new Integer(1));
        frameCounter.setColumns(4);
        frameCounter.setMaximumSize(new Dimension(40, 20));
        frameCounter.setValue(0);
        frameCounter.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                final JFormattedTextField source = (JFormattedTextField) e.getSource();
                if (source.hasFocus()) {
                    if (source == frameCounter) {
                        if (timer.isInitialized()) {
                            timer.setFrameByIndex(((Number) frameCounter.getValue()).intValue() - timeBar.getMinimum(),
                                    false);
                        }
                        playButton.setIcon(playIcon);
                        playButton.invalidate();
                    }
                }
            }
        });

        bottomPanel1.add(frameCounter);

        timeBar.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                final JSlider source = (JSlider) e.getSource();
                if (source.hasFocus()) {
                    timer.setFrameByIndex(timeBar.getValue() - timeBar.getMinimum(), false);
                    playButton.setIcon(playIcon);
                    playButton.invalidate();
                }
            }
        });
        bottomPanel2.add(timeBar);

        bottomPanel.add(bottomPanel1);
        bottomPanel.add(bottomPanel2);

        return bottomPanel;
    }

    private void createMovieTweakPanel() {
        movieConfig.removeAll();

        final ItemListener listener = new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent arg0) {
                setTweakState(TweakState.NONE);
            }
        };
        movieConfig.add(GoggleSwing.titleBox("Movie Creator", listener));

        final ItemListener checkBoxListener = new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                settings.setMovieRotate(e.getStateChange());
                timer.redraw();
            }
        };
        movieConfig.add(GoggleSwing.checkboxBox("", new GoggleSwing.CheckBoxItem("Rotation", settings.getMovieRotate(),
                checkBoxListener)));

        final JLabel rotationSetting = new JLabel("" + settings.getMovieRotationSpeedDef());
        final ChangeListener movieRotationSpeedListener = new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                final JSlider source = (JSlider) e.getSource();
                if (source.hasFocus()) {
                    settings.setMovieRotationSpeed(source.getValue() * .25f);
                    rotationSetting.setText("" + settings.getMovieRotationSpeedDef());
                }
            }
        };
        movieConfig.add(GoggleSwing.sliderBox("Rotation Speed", movieRotationSpeedListener,
                (int) (settings.getMovieRotationSpeedMin() * 4f), (int) (settings.getMovieRotationSpeedMax() * 4f), 1,
                (int) (settings.getMovieRotationSpeedDef() * 4f), rotationSetting));

        movieConfig.add(GoggleSwing.buttonBox("", new GoggleSwing.ButtonBoxItem("Start Recording",
                new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        timer.movieMode();
                    }
                })));
    }

    private void createDataTweakPanel() {
        dataConfig.removeAll();

        if (timer.isInitialized()) {

            final ItemListener listener = new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent arg0) {
                    setTweakState(TweakState.NONE);
                }
            };
            dataConfig.add(GoggleSwing.titleBox("Configuration", listener));

            if (!demomode) {
                final JLabel depthSetting = new JLabel("" + settings.getDepthDef());
                final ChangeListener depthListener = new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        final JSlider source = (JSlider) e.getSource();
                        if (source.hasFocus()) {
                            settings.setDepth(source.getValue());
                            depthSetting.setText("" + settings.getDepthDef());
                        }
                    }
                };
                dataConfig.add(GoggleSwing.sliderBox("Depth setting", depthListener, settings.getDepthMin(),
                        settings.getDepthMax(), 1, settings.getDepthDef(), depthSetting));
            }

            final ArrayList<Component> vcomponents = new ArrayList<Component>();
            JLabel windowlabel = new JLabel("Window Selection");
            windowlabel.setMaximumSize(new Dimension(200, 25));
            windowlabel.setAlignmentX(CENTER_ALIGNMENT);

            vcomponents.add(windowlabel);
            vcomponents.add(Box.createHorizontalGlue());

            String[] screenSelection = new String[1 + settings.getNumScreensRows() * settings.getNumScreensCols()];
            screenSelection[0] = "All Screens";
            for (int i = 0; i < settings.getNumScreensRows() * settings.getNumScreensCols(); i++) {
                screenSelection[i + 1] = "Screen Number " + i;
            }

            final JComboBox<String> comboBox = new JComboBox<String>(screenSelection);
            comboBox.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    int selection = comboBox.getSelectedIndex();
                    settings.setWindowSelection(selection);
                }
            });
            comboBox.setMaximumSize(new Dimension(200, 25));
            vcomponents.add(comboBox);
            vcomponents.add(GoggleSwing.verticalStrut(5));

            dataConfig.add(GoggleSwing.vBoxedComponents(vcomponents, true));

            String[] dataModes = SurfaceTextureDescription.getDataModes();

            final String[] colorMaps = JOCLColormapper.getColormapNames();

            for (int i = 0; i < settings.getNumScreensRows() * settings.getNumScreensCols(); i++) {
                final int currentScreen = i;

                final ArrayList<Component> screenVcomponents = new ArrayList<Component>();

                JLabel screenLabel = new JLabel("Screen " + currentScreen);
                screenVcomponents.add(screenLabel);

                SurfaceTextureDescription selectionDescription = settings.getSurfaceDescription(currentScreen);

                final ArrayList<Component> screenHcomponents = new ArrayList<Component>();

                JComboBox<String> dataModeComboBox = new JComboBox<String>(dataModes);
                ActionListener al = new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (e != null && e.getSource() instanceof JComboBox
                                && e.getSource().getClass().getComponentType() == String.class) {
                            @SuppressWarnings("unchecked")
                            JComboBox<String> cb = (JComboBox<String>) e.getSource();
                            int selection = cb.getSelectedIndex();

                            if (selection == 1) {
                                settings.setDataMode(currentScreen, false, false, true);
                            } else if (selection == 2) {
                                settings.setDataMode(currentScreen, false, true, false);
                            } else {
                                settings.setDataMode(currentScreen, false, false, false);
                            }
                        }
                    }
                };
                dataModeComboBox.addActionListener(al);
                dataModeComboBox.setSelectedIndex(selectionDescription.getDataModeIndex());
                dataModeComboBox.setMinimumSize(new Dimension(50, 25));
                dataModeComboBox.setMaximumSize(new Dimension(100, 25));
                screenHcomponents.add(dataModeComboBox);

                final JComboBox<String> variablesComboBox = new JComboBox<String>(variables.toArray(new String[0]));
                variablesComboBox.setSelectedItem(selectionDescription.getVarName());
                variablesComboBox.setMinimumSize(new Dimension(50, 25));
                variablesComboBox.setMaximumSize(new Dimension(100, 25));
                screenHcomponents.add(variablesComboBox);

                final JCheckBox logCheckBox = new JCheckBox("log?", false);
                ItemListener logCheckboxListener = new ItemListener() {
                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        JCheckBox cb = (JCheckBox) e.getSource();
                        settings.setLogScale(currentScreen, cb.isSelected());

                    }
                };
                logCheckBox.addItemListener(logCheckboxListener);
                screenHcomponents.add(logCheckBox);

                screenVcomponents.add(GoggleSwing.hBoxedComponents(screenHcomponents, true));

                final JComboBox<SimpleImageIcon> colorMapsComboBox = JOCLColormapper.getLegendJComboBox(new Dimension(
                        200, 25));
                colorMapsComboBox.setSelectedItem(colorMapsComboBox.getItemAt(JOCLColormapper
                        .getIndexOfColormap(selectionDescription.getColorMap())));
                colorMapsComboBox.setMinimumSize(new Dimension(100, 25));
                colorMapsComboBox.setMaximumSize(new Dimension(200, 25));
                screenVcomponents.add(colorMapsComboBox);

                final RangeSlider selectionLegendSlider = new RangeSlider();
                ((RangeSliderUI) selectionLegendSlider.getUI()).setRangeColorMap(selectionDescription.getColorMap());
                selectionLegendSlider.setMinimum(0);
                selectionLegendSlider.setMaximum(100);
                selectionLegendSlider.setValue(settings.getRangeSliderLowerValue(currentScreen));
                selectionLegendSlider.setUpperValue(settings.getRangeSliderUpperValue(currentScreen));

                colorMapsComboBox.addItemListener(new ItemListener() {
                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        settings.setColorMap(currentScreen, colorMaps[colorMapsComboBox.getSelectedIndex()]);

                        ((RangeSliderUI) selectionLegendSlider.getUI()).setRangeColorMap(colorMaps[colorMapsComboBox
                                .getSelectedIndex()]);
                        selectionLegendSlider.invalidate();
                    }
                });

                selectionLegendSlider.addChangeListener(new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        RangeSlider slider = (RangeSlider) e.getSource();
                        SurfaceTextureDescription texDesc = settings.getSurfaceDescription(currentScreen);

                        String var = texDesc.getVarName();
                        settings.setVariableRange(currentScreen, var, slider.getValue(), slider.getUpperValue());
                    }
                });

                variablesComboBox.addItemListener(new ItemListener() {
                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        String var = (String) e.getItem();

                        settings.setVariable(currentScreen, var);
                        selectionLegendSlider.setValue(settings.getRangeSliderLowerValue(currentScreen));
                        selectionLegendSlider.setUpperValue(settings.getRangeSliderUpperValue(currentScreen));
                    }
                });

                screenVcomponents.add(selectionLegendSlider);

                dataConfig.add(GoggleSwing.vBoxedComponents(screenVcomponents, true));
            }
            dataConfig.add(Box.createVerticalGlue());
        }
    }

    private void createVisualTweakPanel() {
        visualConfig.removeAll();

        final float heightDistortionSpacing = 0.001f;
        final JLabel heightDistortionSetting = new JLabel("" + settings.getHeightDistortion());
        final ChangeListener heightDistortionListener = new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                final JSlider source = (JSlider) e.getSource();
                if (source.hasFocus()) {
                    settings.setHeightDistortion(source.getValue() * heightDistortionSpacing);
                    heightDistortionSetting.setText("" + settings.getHeightDistortion());
                }
            }
        };
        visualConfig.add(GoggleSwing.sliderBox("Height Distortion", heightDistortionListener,
                settings.getHeightDistortionMin(), settings.getHeightDistortionMax(), heightDistortionSpacing,
                settings.getHeightDistortion(), heightDistortionSetting));

    }

    private void handleFiles(File[] files) {
        boolean accept = true;
        for (File thisFile : files) {
            if (!isAcceptableFile(thisFile, new String[] { ".nc" })) {
                accept = false;
            }
        }

        if (accept) {
            if (timer.isInitialized()) {
                timer.close();
            }

            timer = new TimedPlayer(timeBar, frameCounter);
            timer.init(files);

            variables = new ArrayList<String>();
            for (String v : timer.getVariables()) {
                variables.add(v);
            }

            CacheFileManager cacheAtDataLocation = new CacheFileManager(files[0].getParent());
            settings.setCacheFileManagerAtDataLocation(cacheAtDataLocation);
            CacheFileManager cacheAtProgramLocation = new CacheFileManager(System.getProperty("user.dir"));
            settings.setCacheFileManagerAtProgramLocation(cacheAtProgramLocation);

            settings.initDefaultVariables(variables, timer.getInitialFrameNumber());

            createDataTweakPanel();

            final String path = files[0].getParent() + "screenshots/";

            settings.setScreenshotPath(path);

            new Thread(timer).start();

            setTweakState(TweakState.DATA);
        } else {
            final JOptionPane pane = new JOptionPane();
            pane.setMessage("Tried to open invalid file type.");
            final JDialog dialog = pane.createDialog("Alert");
            dialog.setVisible(true);
        }
    }

    private File[] openFile() {
        final JFileChooser fileChooser = new JFileChooser();// "/media/maarten/diskhdd1/eSalsaDATA/01deg_prod_run3/");

        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(true);
        final int result = fileChooser.showOpenDialog(this);

        // user clicked Cancel button on dialog
        if (result == JFileChooser.CANCEL_OPTION) {
            return null;
        } else {
            return fileChooser.getSelectedFiles();
        }
    }

    // Callback methods for the various ui actions and listeners
    public void setTweakState(TweakState newState) {
        configPanel.setVisible(false);
        configPanel.remove(dataConfig);

        currentConfigState = newState;

        if (currentConfigState == TweakState.NONE) {
        } else if (currentConfigState == TweakState.DATA) {
            configPanel.setVisible(true);
            configPanel.add(dataConfig, BorderLayout.WEST);
        }
    }

    public static TimedPlayer getTimer() {
        return timer;
    }

    /**
     * Check whether the file's extension is acceptable.
     * 
     * @param file
     *            The file to check.
     * @param accExts
     *            The list of acceptable extensions.
     * @return True if the file's extension is present in the list of acceptable
     *         extensions.
     */
    public static boolean isAcceptableFile(File file, String[] accExts) {
        final String path = file.getParent();
        final String name = file.getName();
        final String fullPath = path + name;
        final String[] ext = fullPath.split("[.]");

        boolean result = false;
        for (int i = 0; i < accExts.length; i++) {
            if (ext[ext.length - 1].compareTo(accExts[i]) != 0) {
                result = true;
            }
        }

        return result;
    }
}
