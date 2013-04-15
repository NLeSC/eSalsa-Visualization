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

import nl.esciencecenter.esight.ESightInterfacePanel;
import nl.esciencecenter.esight.io.netcdf.NetCDFUtil;
import nl.esciencecenter.esight.swing.ColormapInterpreter;
import nl.esciencecenter.esight.swing.CustomJSlider;
import nl.esciencecenter.esight.swing.GoggleSwing;
import nl.esciencecenter.esight.swing.RangeSlider;
import nl.esciencecenter.esight.swing.RangeSliderUI;
import nl.esciencecenter.visualization.esalsa.data.ImauTimedPlayer;
import nl.esciencecenter.visualization.esalsa.data.SurfaceTextureDescription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImauPanel extends ESightInterfacePanel {
    public static enum TweakState {
        NONE, DATA, VISUAL, MOVIE
    }

    private final ImauSettings settings = ImauSettings.getInstance();
    private final static Logger logger = LoggerFactory
            .getLogger(ImauPanel.class);

    private static final long serialVersionUID = 1L;

    protected CustomJSlider timeBar;

    protected JFormattedTextField frameCounter, stepSizeField;
    private TweakState currentConfigState = TweakState.NONE;

    private final JPanel configPanel;

    private final JPanel dataConfig, visualConfig, movieConfig;

    private static ImauTimedPlayer timer;

    private File file1;
    private ArrayList<String> variables;

    protected GLCanvas glCanvas;

    public ImauPanel(String path, String cmdlnfileName, String cmdlnfileName2) {
        setLayout(new BorderLayout(0, 0));

        // this.imauWindow = imauWindow;

        variables = new ArrayList<String>();

        timeBar = new CustomJSlider(new BasicSliderUI(timeBar));
        timeBar.setValue(0);
        timeBar.setMajorTickSpacing(5);
        timeBar.setMinorTickSpacing(1);
        timeBar.setMaximum(0);
        timeBar.setMinimum(0);
        timeBar.setPaintTicks(true);
        timeBar.setSnapToTicks(true);

        timer = new ImauTimedPlayer(timeBar, frameCounter);

        // Make the menu bar
        final JMenuBar menuBar = new JMenuBar();
        menuBar.setLayout(new BoxLayout(menuBar, BoxLayout.X_AXIS));

        final JMenu file = new JMenu("File");
        final JMenuItem open = new JMenuItem("Open");
        open.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                final File file = openFile();
                file1 = file;
                handleFile(file);
            }
        });
        file.add(open);
        menuBar.add(file);

        // final JMenuItem open2 = new JMenuItem("Open Second");
        // open2.addActionListener(new ActionListener() {
        // @Override
        // public void actionPerformed(ActionEvent arg0) {
        // final File file = openFile();
        // handleFile(file1, file);
        // }
        // });
        // file.add(open2);

        // final JMenuItem makeMovie = new JMenuItem("Make movie.");
        // makeMovie.addActionListener(new ActionListener() {
        // @Override
        // public void actionPerformed(ActionEvent arg0) {
        // setTweakState(TweakState.MOVIE);
        // }
        // });
        // options.add(makeMovie);

        final JMenu options = new JMenu("Options");
        final JMenuItem showDataTweakPanel = new JMenuItem(
                "Show data configuration panel.");
        showDataTweakPanel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                setTweakState(TweakState.DATA);
            }
        });
        options.add(showDataTweakPanel);

        // final JMenuItem showVisualTweakPanel = new JMenuItem(
        // "Show visual configuration panel.");
        // showVisualTweakPanel.addActionListener(new ActionListener() {
        // @Override
        // public void actionPerformed(ActionEvent arg0) {
        // setTweakState(TweakState.VISUAL);
        // }
        // });
        // options.add(showVisualTweakPanel);
        //
        // // a group of radio button menu items, to control output options
        // options.addSeparator();
        //
        // JRadioButtonMenuItem rbMenuItem;
        // ButtonGroup screenCountGroup = new ButtonGroup();
        //
        // rbMenuItem = new JRadioButtonMenuItem("2x2");
        // rbMenuItem.setSelected(true);
        // screenCountGroup.add(rbMenuItem);
        // rbMenuItem.addActionListener(new ActionListener() {
        // @Override
        // public void actionPerformed(ActionEvent e) {
        // JRadioButtonMenuItem item = (JRadioButtonMenuItem) e
        // .getSource();
        // item.setSelected(true);
        //
        // settings.setNumberOfScreens(2, 2);
        // dataConfig.setVisible(false);
        // createDataTweakPanel();
        // dataConfig.setVisible(true);
        // }
        // });
        // options.add(rbMenuItem);
        //
        // rbMenuItem = new JRadioButtonMenuItem("3x3");
        // screenCountGroup.add(rbMenuItem);
        // rbMenuItem.addActionListener(new ActionListener() {
        // @Override
        // public void actionPerformed(ActionEvent e) {
        // JRadioButtonMenuItem item = (JRadioButtonMenuItem) e
        // .getSource();
        // item.setSelected(true);
        //
        // settings.setNumberOfScreens(3, 3);
        // dataConfig.setVisible(false);
        // createDataTweakPanel();
        // dataConfig.setVisible(true);
        // }
        // });
        // options.add(rbMenuItem);

        menuBar.add(options);

        menuBar.add(Box.createHorizontalGlue());

        final JMenuBar menuBar2 = new JMenuBar();

        ImageIcon nlescIcon = GoggleSwing.createResizedImageIcon(
                "images/ESCIENCE_logo.png", "eScienceCenter Logo", 50, 28);
        JLabel nlesclogo = new JLabel(nlescIcon);
        nlesclogo.setMinimumSize(new Dimension(300, 20));
        nlesclogo.setMaximumSize(new Dimension(311, 28));

        ImageIcon saraIcon = GoggleSwing.createResizedImageIcon(
                "images/logo_sara.png", "SARA Logo", 50, 28);
        JLabel saralogo = new JLabel(saraIcon);
        saralogo.setMinimumSize(new Dimension(40, 20));
        saralogo.setMaximumSize(new Dimension(41, 28));
        menuBar2.add(Box.createHorizontalStrut(3));

        ImageIcon imauIcon = GoggleSwing.createResizedImageIcon(
                "images/logo_imau.png", "IMAU Logo", 50, 28);
        JLabel imaulogo = new JLabel(imauIcon);
        imaulogo.setMinimumSize(new Dimension(50, 20));
        imaulogo.setMaximumSize(new Dimension(52, 28));

        // ImageIcon qrIcon = GoggleSwing.createResizedImageIcon(
        // "images/qrcode_nlesc.png", "QR", 28, 28);
        // JLabel qr = new JLabel(qrIcon);
        // qr.setMinimumSize(new Dimension(20, 20));
        // qr.setMaximumSize(new Dimension(28, 28));

        menuBar2.add(Box.createHorizontalGlue());
        menuBar2.add(imaulogo);
        menuBar2.add(Box.createHorizontalStrut(20));
        menuBar2.add(saralogo);
        menuBar2.add(Box.createHorizontalStrut(20));
        menuBar2.add(nlesclogo);
        menuBar2.add(Box.createHorizontalGlue());

        Container menuContainer = new Container();
        menuContainer.setLayout(new BoxLayout(menuContainer, BoxLayout.Y_AXIS));

        menuContainer.add(menuBar);
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
        // visualConfig.setLayout(new BoxLayout(visualConfig,
        // BoxLayout.Y_AXIS));
        // visualConfig.setMinimumSize(visualConfig.getPreferredSize());
        // createVisualTweakPanel();

        movieConfig = new JPanel();
        // movieConfig.setLayout(new BoxLayout(movieConfig, BoxLayout.Y_AXIS));
        // movieConfig.setMinimumSize(configPanel.getPreferredSize());
        // createMovieTweakPanel();

        add(bottomPanel, BorderLayout.SOUTH);

        // Read command line file information
        if (cmdlnfileName != null) {
            if (cmdlnfileName2 != null) {
                final File cmdlnfile1 = new File(cmdlnfileName);
                final File cmdlnfile2 = new File(cmdlnfileName2);
                handleFile(cmdlnfile1, cmdlnfile2);
            } else {
                final File cmdlnfile = new File(cmdlnfileName);
                handleFile(cmdlnfile);
            }
        }

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
            public Component getComponentAfter(Container aContainer,
                    Component aComponent) {
                return null;
            }

            @Override
            public Component getComponentBefore(Container aContainer,
                    Component aComponent) {
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

        final JButton oneForwardButton = GoggleSwing.createImageButton(
                "images/media-playback-oneforward.png", "Next", null);
        final JButton oneBackButton = GoggleSwing.createImageButton(
                "images/media-playback-onebackward.png", "Previous", null);
        final JButton rewindButton = GoggleSwing.createImageButton(
                "images/media-playback-rewind.png", "Rewind", null);
        final JButton screenshotButton = GoggleSwing.createImageButton(
                "images/camera.png", "Screenshot", null);
        final JButton playButton = GoggleSwing.createImageButton(
                "images/media-playback-start.png", "Start", null);
        final ImageIcon playIcon = GoggleSwing.createImageIcon(
                "images/media-playback-start.png", "Start");
        final ImageIcon stopIcon = GoggleSwing.createImageIcon(
                "images/media-playback-stop.png", "Start");

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
                final JFormattedTextField source = (JFormattedTextField) e
                        .getSource();
                if (source.hasFocus()) {
                    if (source == stepSizeField) {
                        settings.setTimestep((Integer) ((Number) source
                                .getValue()));
                    }
                }
            }
        });
        bottomPanel1.add(stepSizeField);

        screenshotButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // timer.stop();
                // final ImauInputHandler inputHandler = ImauInputHandler
                // .getInstance();
                // final String fileName = "screenshot: " + "{"
                // + inputHandler.getRotation().get(0) + ","
                // + inputHandler.getRotation().get(1) + " - "
                // + Float.toString(inputHandler.getViewDist()) + "} ";
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
                final JFormattedTextField source = (JFormattedTextField) e
                        .getSource();
                if (source.hasFocus()) {
                    if (source == frameCounter) {
                        if (timer.isInitialized()) {
                            timer.setFrame(
                                    ((Number) frameCounter.getValue())
                                            .intValue() - timeBar.getMinimum(),
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
                    timer.setFrame(timeBar.getValue() - timeBar.getMinimum(),
                            false);
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
        movieConfig.add(GoggleSwing.checkboxBox(
                "",
                new GoggleSwing.CheckBoxItem("Rotation", settings
                        .getMovieRotate(), checkBoxListener)));

        final JLabel rotationSetting = new JLabel(""
                + settings.getMovieRotationSpeedDef());
        final ChangeListener movieRotationSpeedListener = new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                final JSlider source = (JSlider) e.getSource();
                if (source.hasFocus()) {
                    settings.setMovieRotationSpeed(source.getValue() * .25f);
                    rotationSetting.setText(""
                            + settings.getMovieRotationSpeedDef());
                }
            }
        };
        movieConfig.add(GoggleSwing.sliderBox("Rotation Speed",
                movieRotationSpeedListener,
                (int) (settings.getMovieRotationSpeedMin() * 4f),
                (int) (settings.getMovieRotationSpeedMax() * 4f), 1,
                (int) (settings.getMovieRotationSpeedDef() * 4f),
                rotationSetting));

        movieConfig.add(GoggleSwing.buttonBox("",
                new GoggleSwing.ButtonBoxItem("Start Recording",
                        new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                timer.movieMode();
                            }
                        })));
    }

    private void createDataTweakPanel() {
        dataConfig.removeAll();

        final ItemListener listener = new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent arg0) {
                setTweakState(TweakState.NONE);
            }
        };
        dataConfig.add(GoggleSwing.titleBox("Configuration", listener));

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
        dataConfig.add(GoggleSwing.sliderBox("Depth setting", depthListener,
                settings.getDepthMin(), settings.getDepthMax(), 1,
                settings.getDepthDef(), depthSetting));

        final ArrayList<Component> vcomponents = new ArrayList<Component>();
        JLabel windowlabel = new JLabel("Window Selection");
        windowlabel.setMaximumSize(new Dimension(200, 25));
        windowlabel.setAlignmentX(CENTER_ALIGNMENT);

        vcomponents.add(windowlabel);
        vcomponents.add(Box.createHorizontalGlue());

        String[] screenSelection = new String[1 + settings.getNumScreensRows()
                * settings.getNumScreensCols()];
        screenSelection[0] = "All Screens";
        for (int i = 0; i < settings.getNumScreensRows()
                * settings.getNumScreensCols(); i++) {
            screenSelection[i + 1] = "Screen Number " + i;
        }

        final JComboBox comboBox = new JComboBox(screenSelection);
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

        final String[] colorMaps = ColormapInterpreter.getColormapNames();

        for (int i = 0; i < settings.getNumScreensRows()
                * settings.getNumScreensCols(); i++) {
            final int currentScreen = i;

            final ArrayList<Component> screenVcomponents = new ArrayList<Component>();

            JLabel screenLabel = new JLabel("Screen " + currentScreen);
            screenVcomponents.add(screenLabel);

            SurfaceTextureDescription selectionDescription = settings
                    .getSurfaceDescription(currentScreen);

            final ArrayList<Component> screenHcomponents = new ArrayList<Component>();

            JComboBox dataModeComboBox = new JComboBox(dataModes);
            ActionListener al = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    JComboBox cb = (JComboBox) e.getSource();
                    int selection = cb.getSelectedIndex();

                    if (selection == 1) {
                        settings.setDataMode(currentScreen, false, false, true);
                    } else if (selection == 2) {
                        settings.setDataMode(currentScreen, false, true, false);
                    } else {
                        settings.setDataMode(currentScreen, false, false, false);
                    }
                }
            };
            dataModeComboBox.addActionListener(al);
            dataModeComboBox.setSelectedIndex(selectionDescription
                    .getDataModeIndex());
            dataModeComboBox.setMinimumSize(new Dimension(50, 25));
            dataModeComboBox.setMaximumSize(new Dimension(100, 25));
            screenHcomponents.add(dataModeComboBox);

            final JComboBox variablesComboBox = new JComboBox(
                    variables.toArray(new String[0]));
            variablesComboBox
                    .setSelectedItem(selectionDescription.getVarName());
            variablesComboBox.setMinimumSize(new Dimension(50, 25));
            variablesComboBox.setMaximumSize(new Dimension(100, 25));
            screenHcomponents.add(variablesComboBox);

            screenVcomponents.add(GoggleSwing.hBoxedComponents(
                    screenHcomponents, true));

            final JComboBox colorMapsComboBox = ColormapInterpreter
                    .getLegendJComboBox(new Dimension(200, 25));
            colorMapsComboBox.setSelectedItem(ColormapInterpreter
                    .getIndexOfColormap(selectionDescription.getColorMap()));
            colorMapsComboBox.setMinimumSize(new Dimension(100, 25));
            colorMapsComboBox.setMaximumSize(new Dimension(200, 25));
            screenVcomponents.add(colorMapsComboBox);

            final RangeSlider selectionLegendSlider = new RangeSlider();
            ((RangeSliderUI) selectionLegendSlider.getUI())
                    .setRangeColorMap(selectionDescription.getColorMap());
            selectionLegendSlider.setMinimum(0);
            selectionLegendSlider.setMaximum(100);
            selectionLegendSlider.setValue(settings
                    .getRangeSliderLowerValue(currentScreen));
            selectionLegendSlider.setUpperValue(settings
                    .getRangeSliderUpperValue(currentScreen));

            colorMapsComboBox.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    settings.setColorMap(currentScreen,
                            colorMaps[colorMapsComboBox.getSelectedIndex()]);

                    ((RangeSliderUI) selectionLegendSlider.getUI())
                            .setRangeColorMap(colorMaps[colorMapsComboBox
                                    .getSelectedIndex()]);
                    selectionLegendSlider.invalidate();
                }
            });

            selectionLegendSlider.addChangeListener(new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    RangeSlider slider = (RangeSlider) e.getSource();
                    SurfaceTextureDescription texDesc = settings
                            .getSurfaceDescription(currentScreen);

                    String var = texDesc.getVarName();
                    settings.setVariableRange(currentScreen, var,
                            slider.getValue(), slider.getUpperValue());
                }
            });

            variablesComboBox.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    String var = (String) ((JComboBox) e.getSource())
                            .getSelectedItem();

                    settings.setVariable(currentScreen, var);
                    selectionLegendSlider.setValue(settings
                            .getRangeSliderLowerValue(currentScreen));
                    selectionLegendSlider.setUpperValue(settings
                            .getRangeSliderUpperValue(currentScreen));
                }
            });

            screenVcomponents.add(selectionLegendSlider);

            dataConfig.add(GoggleSwing
                    .vBoxedComponents(screenVcomponents, true));
        }
        dataConfig.add(Box.createVerticalGlue());
    }

    private void createVisualTweakPanel() {
        visualConfig.removeAll();

        final float heightDistortionSpacing = 0.001f;
        final JLabel heightDistortionSetting = new JLabel(""
                + settings.getHeightDistortion());
        final ChangeListener heightDistortionListener = new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                final JSlider source = (JSlider) e.getSource();
                if (source.hasFocus()) {
                    settings.setHeightDistortion(source.getValue()
                            * heightDistortionSpacing);
                    heightDistortionSetting.setText(""
                            + settings.getHeightDistortion());
                }
            }
        };
        visualConfig.add(GoggleSwing.sliderBox("Height Distortion",
                heightDistortionListener, settings.getHeightDistortionMin(),
                settings.getHeightDistortionMax(), heightDistortionSpacing,
                settings.getHeightDistortion(), heightDistortionSetting));

        // final ItemListener checkBoxListener = new ItemListener() {
        // @Override
        // public void itemStateChanged(ItemEvent e) {
        // if (e.getStateChange() == ItemEvent.SELECTED) {
        // settings.setDynamicDimensions(true);
        // } else {
        // settings.setDynamicDimensions(false);
        // }
        // timer.redraw();
        // }
        // };
        // visualConfig.add(GoggleSwing.checkboxBox(
        // "",
        // new GoggleSwing.CheckBoxItem("Dynamic dimensions", settings
        // .isDynamicDimensions(), checkBoxListener)));

    }

    protected void handleFile(File file1, File file2) {
        if (file1 != null && file2 != null
                && NetCDFUtil.isAcceptableFile(file1, new String[] { ".nc" })
                && NetCDFUtil.isAcceptableFile(file2, new String[] { ".nc" })) {
            if (timer.isInitialized()) {
                timer.close();
            }
            timer = new ImauTimedPlayer(timeBar, frameCounter);

            timer.init(file1, file2);
            new Thread(timer).start();

            variables = new ArrayList<String>();
            for (String v : timer.getVariables()) {
                variables.add(v);
            }
            createDataTweakPanel();

            final String path = NetCDFUtil.getPath(file1) + "screenshots/";

            settings.setScreenshotPath(path);
        } else {
            if (null != file1 && null != file2) {
                final JOptionPane pane = new JOptionPane();
                pane.setMessage("Tried to open invalid file type.");
                final JDialog dialog = pane.createDialog("Alert");
                dialog.setVisible(true);
            } else {
                logger.error("File is null");
                System.exit(1);
            }
        }
    }

    protected void handleFile(File file) {
        if (file != null
                && NetCDFUtil.isAcceptableFile(file, new String[] { ".nc" })) {
            if (timer.isInitialized()) {
                timer.close();
            }
            timer = new ImauTimedPlayer(timeBar, frameCounter);

            timer.init(file);
            new Thread(timer).start();

            variables = new ArrayList<String>();
            for (String v : timer.getVariables()) {
                variables.add(v);
            }
            createDataTweakPanel();

            final String path = NetCDFUtil.getPath(file) + "screenshots/";

            settings.setScreenshotPath(path);
        } else {
            if (null != file) {
                final JOptionPane pane = new JOptionPane();
                pane.setMessage("Tried to open invalid file type.");
                final JDialog dialog = pane.createDialog("Alert");
                dialog.setVisible(true);
            } else {
                logger.error("File is null");
                System.exit(1);
            }
        }
    }

    private File openFile() {
        final JFileChooser fileChooser = new JFileChooser();

        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        final int result = fileChooser.showOpenDialog(this);

        // user clicked Cancel button on dialog
        if (result == JFileChooser.CANCEL_OPTION) {
            return null;
        } else {
            return fileChooser.getSelectedFile();
        }
    }

    // Callback methods for the various ui actions and listeners
    public void setTweakState(TweakState newState) {
        configPanel.setVisible(false);
        configPanel.remove(dataConfig);
        // configPanel.remove(visualConfig);
        // configPanel.remove(movieConfig);

        currentConfigState = newState;

        if (currentConfigState == TweakState.NONE) {
        } else if (currentConfigState == TweakState.DATA) {
            configPanel.setVisible(true);
            configPanel.add(dataConfig, BorderLayout.WEST);
            // } else if (currentConfigState == TweakState.VISUAL) {
            // configPanel.setVisible(true);
            // configPanel.add(visualConfig, BorderLayout.WEST);
            // } else if (currentConfigState == TweakState.MOVIE) {
            // configPanel.setVisible(true);
            // configPanel.add(movieConfig, BorderLayout.WEST);
        }
    }

    public static ImauTimedPlayer getTimer() {
        return timer;
    }
}
