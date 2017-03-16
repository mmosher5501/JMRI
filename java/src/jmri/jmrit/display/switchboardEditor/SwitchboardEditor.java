package jmri.jmrit.display.switchboardEditor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
//import java.awt.datatransfer.Clipboard;
//import java.awt.datatransfer.ClipboardOwner;
//import java.awt.datatransfer.DataFlavor;
//import java.awt.datatransfer.Transferable;
//import java.awt.datatransfer.UnsupportedFlavorException;
//import java.awt.dnd.DnDConstants;
//import java.awt.dnd.DropTarget;
//import java.awt.dnd.DropTargetDragEvent;
//import java.awt.dnd.DropTargetDropEvent;
//import java.awt.dnd.DropTargetEvent;
//import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import jmri.ConfigureManager;
import jmri.InstanceManager;
import jmri.Turnout;
import jmri.jmrit.catalog.CatalogPanel;
import jmri.jmrit.catalog.ImageIndexEditor;
import jmri.jmrit.catalog.NamedIcon;
import jmri.jmrit.display.CoordinateEdit;
import jmri.jmrit.display.Editor;
import jmri.jmrit.display.IndicatorTrack;
import jmri.jmrit.display.LinkingObject;
import jmri.jmrit.display.LocoIcon;
import jmri.jmrit.display.MemoryIcon;
import jmri.jmrit.display.Positionable;
import jmri.jmrit.display.PositionableIcon;
import jmri.jmrit.display.PositionableJComponent;
import jmri.jmrit.display.PositionableJPanel;
import jmri.jmrit.display.PositionableLabel;
import jmri.jmrit.display.PositionablePopupUtil;
import jmri.jmrit.display.SensorIcon;
import jmri.jmrit.display.ToolTip;
import jmri.jmrit.display.controlPanelEditor.shape.ShapeDrawer;
import jmri.jmrit.display.palette.ItemPalette;
import jmri.util.HelpUtil;
import jmri.util.JmriJFrame;
import jmri.util.SystemType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a simple editor for adding jmri.jmrit.display items to a captive
 * JFrame.
 * <P>
 * GUI is structured as a band of common parameters across the top, then a
 * series of things you can add.
 * <P>
 * All created objects are put specific levels depending on their type (higher
 * levels are in front):
 * <UL>
 * <LI>BKG background
 * <LI>ICONS icons and other drawing symbols
 * <LI>LABELS text labels
 * <LI>TURNOUTS turnouts and other variable track items
 * <LI>SENSORS sensors and other independently modified objects
 * </UL>
 * Note that higher numbers appear behind lower numbers.
 * <P>
 * The "contents" List keeps track of all the objects added to the target frame
 * for later manipulation.
 * No DnD for application as panels will automatically populated.
 * <P>
 * @author Pete Cressman Copyright (c) 2009, 2010, 2011
 * @author Egbert Broerse Copyright (c) 2017
 *
 */
public class SwitchboardEditor extends Editor {

    protected JMenuBar _menuBar;
    private JMenu _editorMenu;
    protected JMenu _editMenu;
    protected JMenu _fileMenu;
    protected JMenu _optionMenu;
    protected JMenu _iconMenu;
    protected JMenu _zoomMenu;
    private JMenu _markerMenu;
    private JMenu _drawMenu;
    private ArrayList<Positionable> _secondSelectionGroup;
    //private ShapeDrawer _shapeDrawer;
    private ItemPalette _itemPalette;
    private boolean _disableShapeSelection;
    ImageIcon iconPrev = new ImageIcon("resources/icons/misc/gui3/LafLeftArrow_m.gif");
    private JLabel prev = new JLabel(iconPrev);
    ImageIcon iconNext = new ImageIcon("resources/icons/misc/gui3/LafRightArrow_m.gif");
    private JLabel next = new JLabel(iconNext);
    ImageIcon appslideOff = new ImageIcon("resources/icons/misc/switchboard/appslide-off");
    ImageIcon appslideOn = new ImageIcon("resources/icons/misc/switchboard/appslide-on");

    // Switchboard items
    private JPanel navBarPanel = null;
    private int rangeMin = 1;
    private int rangeMax = 32;
    private int _range = rangeMax - rangeMin;
    JSpinner minSpinner = new JSpinner(new SpinnerNumberModel(rangeMin, rangeMin, rangeMax, 1));
    JSpinner maxSpinner = new JSpinner(new SpinnerNumberModel(rangeMax, rangeMin, rangeMax, 1));
    // toolbar (from LE)
    private JPanel floatEditHelpPanel = null;
    private JPanel editToolBarPanel = null;
    private JScrollPane editToolBarScroll = null;
    private JPanel editToolBarContainer = null;

    private JPanel helpBarPanel = null;
    private JPanel helpBar = new JPanel();
    private boolean showHelpBar = true;
    private int height = 100;
    private int width = 100;

    private JCheckBoxMenuItem useGlobalFlagBox = new JCheckBoxMenuItem(Bundle.getMessage("CheckBoxGlobalFlags"));
//    private JCheckBoxMenuItem editableBox = new JCheckBoxMenuItem(Bundle.getMessage("CloseEditor"));
    private JCheckBoxMenuItem positionableBox = new JCheckBoxMenuItem(Bundle.getMessage("CheckBoxPositionable"));
    private JCheckBoxMenuItem controllingBox = new JCheckBoxMenuItem(Bundle.getMessage("CheckBoxControlling"));
    private JCheckBoxMenuItem showTooltipBox = new JCheckBoxMenuItem(Bundle.getMessage("CheckBoxShowTooltips"));
    private JCheckBoxMenuItem hiddenBox = new JCheckBoxMenuItem(Bundle.getMessage("CheckBoxHidden"));
    private JCheckBoxMenuItem disableShapeSelect = new JCheckBoxMenuItem(Bundle.getMessage("disableShapeSelect"));
    private JRadioButtonMenuItem scrollBoth = new JRadioButtonMenuItem(Bundle.getMessage("ScrollBoth"));
    private JRadioButtonMenuItem scrollNone = new JRadioButtonMenuItem(Bundle.getMessage("ScrollNone"));
    private JRadioButtonMenuItem scrollHorizontal = new JRadioButtonMenuItem(Bundle.getMessage("ScrollHorizontal"));
    private JRadioButtonMenuItem scrollVertical = new JRadioButtonMenuItem(Bundle.getMessage("ScrollVertical"));

    //Default flow layout definitions for JPanels
    private FlowLayout leftRowLayout = new FlowLayout(FlowLayout.LEFT, 5, 0);       //5 pixel gap between items, no vertical gap
    private FlowLayout centerRowLayout = new FlowLayout(FlowLayout.CENTER, 5, 0);   //5 pixel gap between items, no vertical gap
    private FlowLayout rightRowLayout = new FlowLayout(FlowLayout.RIGHT, 5, 0);     //5 pixel gap between items, no vertical gap

    private JPanel turnoutNamePanel = new JPanel(leftRowLayout);

    // JLayeredpane demo
    private JLayeredPane switchboardLayeredPane;
    private JCheckBox onTop;
    private JComboBox layerList;
    private String[] beanTypeStrings = { Bundle.getMessage("BeanNameTurnout"),
            Bundle.getMessage("BeanNameSensor"),
            Bundle.getMessage("BeanNameLight")
    };
    private Color[] layerColors = { Color.yellow, Color.magenta,
            Color.cyan,   Color.red,
            Color.green };
    //Action commands
    private static String ON_TOP_COMMAND = "ontop";
    private static String LAYER_COMMAND = "layer";

    public SwitchboardEditor() {
    }

    public SwitchboardEditor(String name) {
        super(name, false, true);
        init(name);
    }

    @Override
    protected void init(String name) {
        //setVisible(false);
        Container contentPane = this.getContentPane(); // Editor
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));

        // make menus
        setGlobalSetsLocalFlag(false);
        setUseGlobalFlag(false);
        _menuBar = new JMenuBar();
        //makeDrawMenu();
        //makeIconMenu();
        //makeZoomMenu();
        makeOptionMenu();
        //makeEditMenu();
        makeFileMenu();

        setJMenuBar(_menuBar);
        addHelpMenu("package.jmri.jmrit.display.SwitchboardEditor", true);

        //super.setTargetPanel(null, makeFrame(name)); // original CPE version
        switchboardLayeredPane = new JLayeredPane();
        switchboardLayeredPane.setPreferredSize(new Dimension(300, 310));
        switchboardLayeredPane.setBorder(BorderFactory.createTitledBorder(
                "Click to toggle Turnout state (grey buttons not connected)"));
        switchboardLayeredPane.addMouseMotionListener(this);
        //This is the origin of the first label added.
        Point origin = new Point(10, 20);
        //This is the offset for computing the origin for the next label.
        int offset = 35;
        //Add several overlapping, colored labels to the layered pane
        //using absolute positioning/sizing.
//        for (int i = 0; i < beanTypeStrings.length; i++) {
//            JLabel label = createColoredLabel(beanTypeStrings[i],
//                    layerColors[i], origin);
//            switchboardLayeredPane.add(label, new Integer(i));
//            origin.x += offset;
//            origin.y += offset;
//        }
        //Add several labels to the layered pane.
        switchboardLayeredPane.setLayout(new GridLayout(8,4)); // vertical, horizontal
        addTurnoutButtonRange(rangeMin, rangeMax);
        //Add control pane and layered pane to this JPanel.
        //add(Box.createRigidArea(new Dimension(0, 10)));
        add(createControlPanel());
        //add(Box.createRigidArea(new Dimension(0, 10)));
        //add(switchboardLayeredPane);

        super.setTargetPanel(switchboardLayeredPane, makeFrame(name)); // provide a JLayeredPane to use

        super.setTargetPanelSize(300, 300);

        // create top bar, from Oracle demo
        //setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

        // set scrollbar initial state
        setScroll(SCROLL_BOTH);
        scrollBoth.setSelected(true);
        super.setDefaultToolTip(new ToolTip(null, 0, 0, new Font("Serif", Font.PLAIN, 12),
                Color.black, new Color(255, 250, 210), Color.black));
        // register the resulting panel for later configuration
        ConfigureManager cm = InstanceManager.getNullableDefault(jmri.ConfigureManager.class);
        if (cm != null) {
            cm.registerUser(this);
        }

        // navigation top row and to set range
        navBarPanel = new JPanel();
        navBarPanel.setLayout(new BoxLayout(navBarPanel, BoxLayout.X_AXIS));

        navBarPanel.add(prev);
        prev.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int oldMin = (Integer) minSpinner.getValue();
                int oldMax = (Integer) maxSpinner.getValue();
                _range = oldMax - oldMin;
                minSpinner.setValue(Math.max(rangeMin, oldMin - _range - 1));
                maxSpinner.setValue(Math.max(oldMax - _range - 1, Math.max(rangeMax, oldMax - _range - 1)));
                log.debug("oldMin =" + oldMin + ", oldMax =" + oldMax);
            }
        });
        prev.setToolTipText("Previous");
        navBarPanel.add(new JLabel ("Show from:"));
        navBarPanel.add(minSpinner);
        navBarPanel.add(new JLabel ("to:"));
        navBarPanel.add(maxSpinner);
        navBarPanel.add(next);

        next.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int oldMin = (Integer) minSpinner.getValue();
                int oldMax = (Integer) maxSpinner.getValue();
                _range = oldMax - oldMin;
                minSpinner.setValue(oldMax + 1);
                maxSpinner.setValue(oldMax + _range + 1);
            }
        });
        next.setToolTipText("Next");
        navBarPanel.add(Box.createHorizontalGlue());
        //contentPane.add(Box.createVerticalGlue());

        //getTargetPane(name).switchboardLayeredPane.setBackgroundColor(Color.RED); // test
        // put on which Frame?
        contentPane.add(navBarPanel); // on 2nd Editor Panel
        //super.getTargetFrame().add(navBarPanel); // on (top of) Switchboard Frame/Panel

        JPanel p1 = new JPanel();
        JButton addButton = new JButton("Update Switchboard");
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                log.debug("Update clicked");
                //super.getTargetFrame().addTurnoutButtonRange(rangeMin, rangeMax); // problem to redraw
            }
        });
        p1.add(addButton);
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.PAGE_AXIS));
        contentPane.add(p1);

        setupToolBar(); //re-layout all the toolbar items

        // add LE style help bar
        if (showHelpBar) {
            //not sure why… but this is the only way I could
            //get everything to layout correctly
            //when the helpbar is visible…
            boolean editMode = true; //editModeItem.isSelected();
            setAllEditable(!editMode);
            setAllEditable(editMode);
        } else {
            helpBarPanel.setVisible(isEditable() && showHelpBar);
        }

        pack();
        setVisible(true);

        class makeCatalog extends SwingWorker<CatalogPanel, Object> {

            @Override
            public CatalogPanel doInBackground() {
                return CatalogPanel.makeDefaultCatalog();
            }
        }
        (new makeCatalog()).execute();
        log.debug("Init SwingWorker launched");
    }

    private void addTurnoutButtonRange(int rangeMin, int rangeMax) {
        for (int i = rangeMax; i >= rangeMin; i--) {
            JButton beanButton = new JButton("LT " + i); // createColoredLabel(beanTypeStrings[i], layerColors[i]);
            beanButton.setIcon(appslideOff);
            beanButton.setPressedIcon(appslideOn);
            final int _address = i;
            final Turnout turnout = jmri.InstanceManager.turnoutManagerInstance().getTurnout("LT" + _address);
            beanButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (turnout != null && turnout.getCommandedState() != Turnout.CLOSED) {
                        // no need to set a state already set
                        turnout.setCommandedState(Turnout.CLOSED);
                        beanButton.setText("LT " + _address + ": C");
                    } else if (turnout != null && turnout.getCommandedState() != Turnout.THROWN) {
                        turnout.setCommandedState(Turnout.THROWN);
                        beanButton.setText("LT " + _address + ": T");
                    }
                    log.debug("Button {} clicked", "LT" + _address);
                }
            });
            if (turnout == null) {
                beanButton.setEnabled(false);
            }
            //beanButton.addEventChangeListener(turnout);
            switchboardLayeredPane.add(beanButton);
        }
    }

    // Create and set up a colored label.
    // From layeredpane demo
/*    private JLabel createColoredLabel(String text,
                                      Color color,
                                      Point origin) {
        JLabel label = new JLabel(text);
        label.setVerticalAlignment(JLabel.TOP);
        label.setHorizontalAlignment(JLabel.CENTER);
        label.setOpaque(true);
        label.setBackground(color);
        label.setForeground(Color.black);
        label.setBorder(BorderFactory.createLineBorder(Color.black));
        label.setBounds(origin.x, origin.y, 140, 140);
        return label;
    }*/
    //Create and set up a colored label. In Grid
    private JLabel createColoredLabel(String text,
                                      Color color) {
        JLabel label = new JLabel(text);
        label.setVerticalAlignment(JLabel.TOP);
        label.setHorizontalAlignment(JLabel.CENTER);
        label.setOpaque(true);
        label.setBackground(color);
        label.setForeground(Color.black);
        label.setBorder(BorderFactory.createLineBorder(Color.black));
        label.setPreferredSize(new Dimension(140, 140));
        return label;
    }

    //Create the control pane for the top of the frame.
    // From layeredpane demo
    private JPanel createControlPanel() {
        onTop = new JCheckBox("Top Position in Layer");
        onTop.setSelected(true);
        onTop.setActionCommand(ON_TOP_COMMAND);
        onTop.addActionListener(this);

        layerList = new JComboBox(beanTypeStrings);
        layerList.setSelectedIndex(2);    //cyan layer
        layerList.setActionCommand(LAYER_COMMAND);
        layerList.addActionListener(this);

        JPanel controls = new JPanel();
        controls.add(layerList);
        controls.add(onTop);
        controls.setBorder(BorderFactory.createTitledBorder(
                "Select the address range to display "));
        return controls;
    }

    private void setupToolBar() {
        //Initial setup for both horizontal and vertical
        Container contentPane = getContentPane();

        //remove these (if present) so we can add them back (without duplicates)
        if (editToolBarContainer != null) {
            editToolBarContainer.setVisible(false);
            contentPane.remove(editToolBarContainer);
        }

        if (helpBarPanel != null) {
            contentPane.remove(helpBarPanel);
        }

        editToolBarPanel = new JPanel();
        editToolBarPanel.setLayout(new BoxLayout(editToolBarPanel, BoxLayout.PAGE_AXIS));

        JPanel outerBorderPanel = editToolBarPanel;
        JPanel innerBorderPanel = editToolBarPanel;

        Border blacklineBorder = BorderFactory.createLineBorder(Color.black);

        innerBorderPanel = new JPanel();
        innerBorderPanel.setLayout(new BoxLayout(innerBorderPanel, BoxLayout.PAGE_AXIS));
        TitledBorder innerTitleBorder = BorderFactory.createTitledBorder(blacklineBorder, Bundle.getMessage("BeanNameTurnout"));
        innerTitleBorder.setTitleJustification(TitledBorder.CENTER);
        innerTitleBorder.setTitlePosition(TitledBorder.BOTTOM);
        innerBorderPanel.setBorder(innerTitleBorder);
        innerBorderPanel.add(new JLabel ("Closed/Thrown"));

        Dimension screenDim = Toolkit.getDefaultToolkit().getScreenSize();

        //row 2
        JPanel hTop2Panel = new JPanel();
        hTop2Panel.setLayout(new BoxLayout(hTop2Panel, BoxLayout.LINE_AXIS));
        //Row 2 : Left Components
        JPanel hTop2Center = new JPanel(centerRowLayout);
        hTop2Center.add(turnoutNamePanel);
        hTop2Panel.add(hTop2Center);

        innerBorderPanel.add(hTop2Panel);
        outerBorderPanel.add(innerBorderPanel);
//        editToolBarPanel.add(outerBorderPanel);

        editToolBarScroll = new JScrollPane(editToolBarPanel);
        //width = screenDim.width;
        height = editToolBarScroll.getPreferredSize().height;
        editToolBarContainer = new JPanel();
        editToolBarContainer.setLayout(new BoxLayout(editToolBarContainer, BoxLayout.PAGE_AXIS));
        editToolBarContainer.add(editToolBarScroll);
        editToolBarContainer.setMinimumSize(new Dimension(width, height));
        editToolBarContainer.setPreferredSize(new Dimension(width, height));

        helpBarPanel = new JPanel();
        helpBarPanel.add(helpBar);
        for (Component c : helpBar.getComponents()) {
            if (c instanceof JTextArea) {
                JTextArea j = (JTextArea) c;
                j.setSize(new Dimension(width, j.getSize().height));
                j.setLineWrap(false);
                j.setWrapStyleWord(false);
            }
        }
        contentPane.setLayout(new BoxLayout(contentPane, false ? BoxLayout.LINE_AXIS : BoxLayout.PAGE_AXIS));
        contentPane.add(editToolBarContainer);
        contentPane.add(helpBarPanel);
        helpBarPanel.setVisible(isEditable() && showHelpBar);
    }

    //@Override
    protected void makeIconMenu() {
//        _iconMenu = new JMenu(Bundle.getMessage("MenuIcon"));
//        _menuBar.add(_iconMenu, 0);
//        JMenuItem mi = new JMenuItem(Bundle.getMessage("MenuItemItemPalette"));
//        mi.addActionListener(new ActionListener() {
//            Editor editor;
//
//            ActionListener init(Editor ed) {
//                editor = ed;
//                return this;
//            }
//
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                if (_itemPalette == null) {
//                    _itemPalette = new ItemPalette(Bundle.getMessage("MenuItemItemPalette"), editor);
//                }
//                _itemPalette.setVisible(true);
//            }
//        }.init(this));
//        if (SystemType.isMacOSX()) {
//            mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, ActionEvent.META_MASK));
//        } else {
//            mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, ActionEvent.CTRL_MASK));
//        }
//        _iconMenu.add(mi);
//        _iconMenu.add(new jmri.jmrit.beantable.ListedTableAction(Bundle.getMessage("MenuItemTableList")));
//        mi = (JMenuItem) _iconMenu.getMenuComponent(2);
//        if (SystemType.isMacOSX()) {
//            mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, ActionEvent.META_MASK));
//        } else {
//            mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, ActionEvent.CTRL_MASK));
//        }
    }

//    protected void makeDrawMenu() {
//        if (_drawMenu == null) {
//            _drawMenu = _shapeDrawer.makeMenu();
//            _drawMenu.add(disableShapeSelect);
//            disableShapeSelect.addActionListener(new ActionListener() {
//                @Override
//                public void actionPerformed(ActionEvent event) {
//                    _disableShapeSelection = disableShapeSelect.isSelected();
//                }
//            });
//        }
//        _menuBar.add(_drawMenu, 0);
//    }

    public boolean getShapeSelect() {
        return !_disableShapeSelection;
    }

    public void setShapeSelect(boolean set) {
        _disableShapeSelection = !set;
        disableShapeSelect.setSelected(_disableShapeSelection);
    }

    //public ShapeDrawer getShapeDrawer() {
//        return _shapeDrawer;
//    }

    //@Override
    protected void makeOptionMenu() {
        _optionMenu = new JMenu(Bundle.getMessage("MenuOptions"));
        _menuBar.add(_optionMenu, 0);
        // use globals item
        _optionMenu.add(useGlobalFlagBox);
        useGlobalFlagBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                setUseGlobalFlag(useGlobalFlagBox.isSelected());
            }
        });
        useGlobalFlagBox.setSelected(useGlobalFlag());
        // positionable item
        _optionMenu.add(positionableBox);
        positionableBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                setAllPositionable(positionableBox.isSelected());
            }
        });
        positionableBox.setSelected(allPositionable());
        // controlable item
        _optionMenu.add(controllingBox);
        controllingBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                setAllControlling(controllingBox.isSelected());
            }
        });
        controllingBox.setSelected(allControlling());
        // hidden item
        _optionMenu.add(hiddenBox);
        hiddenBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                setShowHidden(hiddenBox.isSelected());
            }
        });
        hiddenBox.setSelected(showHidden());

        _optionMenu.add(showTooltipBox);
        showTooltipBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setAllShowTooltip(showTooltipBox.isSelected());
            }
        });
        showTooltipBox.setSelected(showTooltip());

        // Show/Hide Scroll Bars
        JMenu scrollMenu = new JMenu(Bundle.getMessage("ComboBoxScrollable"));
        _optionMenu.add(scrollMenu);
        ButtonGroup scrollGroup = new ButtonGroup();
        scrollGroup.add(scrollBoth);
        scrollMenu.add(scrollBoth);
        scrollBoth.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                setScroll(SCROLL_BOTH);
            }
        });
        scrollGroup.add(scrollNone);
        scrollMenu.add(scrollNone);
        scrollNone.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                setScroll(SCROLL_NONE);
            }
        });
        scrollGroup.add(scrollHorizontal);
        scrollMenu.add(scrollHorizontal);
        scrollHorizontal.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                setScroll(SCROLL_HORIZONTAL);
            }
        });
        scrollGroup.add(scrollVertical);
        scrollMenu.add(scrollVertical);
        scrollVertical.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                setScroll(SCROLL_VERTICAL);
            }
        });
    }

    private void makeFileMenu() {
        _fileMenu = new JMenu(Bundle.getMessage("MenuFile"));
        _menuBar.add(_fileMenu, 0);
        _fileMenu.add(new jmri.jmrit.display.NewPanelAction(Bundle.getMessage("MenuItemNew")));

        _fileMenu.add(new jmri.configurexml.StoreXmlUserAction(Bundle.getMessage("MenuItemStore")));
        JMenuItem storeIndexItem = new JMenuItem(Bundle.getMessage("MIStoreImageIndex"));
        _fileMenu.add(storeIndexItem);
        storeIndexItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                jmri.jmrit.catalog.ImageIndexEditor.storeImageIndex();
            }
        });

        JMenuItem editItem = new JMenuItem(Bundle.getMessage("renamePanelMenu", "..."));
        PositionableJComponent z = new PositionableJComponent(this);
        z.setScale(getPaintScale());
        editItem.addActionListener(CoordinateEdit.getNameEditAction(z));
        _fileMenu.add(editItem);

        editItem = new JMenuItem(Bundle.getMessage("editIndexMenu"));
        _fileMenu.add(editItem);
        editItem.addActionListener(new ActionListener() {
            SwitchboardEditor panelEd;

            @Override
            public void actionPerformed(ActionEvent e) {
                ImageIndexEditor ii = ImageIndexEditor.instance(panelEd);
                ii.pack();
                ii.setVisible(true);
            }

            ActionListener init(SwitchboardEditor pe) {
                panelEd = pe;
                return this;
            }
        }.init(this));

//        editItem = new JMenuItem(Bundle.getMessage("PEView"));
//        _fileMenu.add(editItem);
//        editItem.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent event) {
//                changeView("jmri.jmrit.display.panelEditor.PanelEditor");
//                if (_itemPalette != null) {
//                    _itemPalette.dispose();
//                }
//            }
//        });

        _fileMenu.addSeparator();
        JMenuItem deleteItem = new JMenuItem(Bundle.getMessage("DeletePanel"));
        _fileMenu.add(deleteItem);
        deleteItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (deletePanel()) {
                    dispose(true);
                }
            }
        });
        _fileMenu.addSeparator();
        editItem = new JMenuItem(Bundle.getMessage("CloseEditor"));
        _fileMenu.add(editItem);
        editItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                setAllEditable(false);
            }
        });
    }

    private JMenu makeSelectTypeMenu() {
        JMenu menu = new JMenu(Bundle.getMessage("SelectType"));
        ButtonGroup typeGroup = new ButtonGroup();
        // I18N use existing jmri.NamedBeanBundle keys
        JRadioButtonMenuItem button = makeSelectTypeButton("BeanNameTurnout", "jmri.jmrit.display.TurnoutIcon");
        typeGroup.add(button);
        menu.add(button);
        button = makeSelectTypeButton("BeanNameSensor", "jmri.jmrit.display.SensorIcon");
        typeGroup.add(button);
        menu.add(button);
        button = makeSelectTypeButton("Shape", "jmri.jmrit.display.controlPanelEditor.shape.PositionableShape");
        typeGroup.add(button);
        menu.add(button);
        button = makeSelectTypeButton("MemoryInput", "jmri.jmrit.display.PositionableJPanel");
        typeGroup.add(button);
        menu.add(button);
        button = makeSelectTypeButton("BeanNameLight", "jmri.jmrit.display.LightIcon");
        typeGroup.add(button);
        menu.add(button);
        return menu;
    }

    private JRadioButtonMenuItem makeSelectTypeButton(String label, String className) {
        JRadioButtonMenuItem button = new JRadioButtonMenuItem(Bundle.getMessage(label));
        button.addActionListener(new ActionListener() {
            String cName;

            ActionListener init(String name) {
                cName = name;
                return this;
            }

            @Override
            public void actionPerformed(ActionEvent event) {
                selectType(cName);
            }
        }.init(className));
        return button;
    }

    private void selectType(String name) {
        try {
            Class<?> cl = Class.forName(name);
            _selectionGroup = new ArrayList<Positionable>();
            Iterator<Positionable> it = _contents.iterator();
            while (it.hasNext()) {
                Positionable pos = it.next();
                if (cl.isInstance(pos)) {
                    _selectionGroup.add(pos);
                }
            }
        } catch (ClassNotFoundException cnfe) {
            log.error("selectType Menu " + cnfe.toString());
        }
        _targetPanel.repaint();
    }

    private JMenu makeSelectLevelMenu() {
        JMenu menu = new JMenu(Bundle.getMessage("SelectLevel"));
        ButtonGroup levelGroup = new ButtonGroup();
        JRadioButtonMenuItem button = null;
        for (int i = 0; i < 11; i++) {
            button = new JRadioButtonMenuItem(Bundle.getMessage("selectLevel", "" + i));
            levelGroup.add(button);
            menu.add(button);
            button.addActionListener(new ActionListener() {
                int j;

                ActionListener init(int k) {
                    j = k;
                    return this;
                }

                @Override
                public void actionPerformed(ActionEvent event) {
                    selectLevel(j);
                }
            }.init(i));
        }
        return menu;
    }

    private void selectLevel(int i) {
        _selectionGroup = new ArrayList<Positionable>();
        Iterator<Positionable> it = _contents.iterator();
        while (it.hasNext()) {
            Positionable pos = it.next();
            if (pos.getDisplayLevel() == i) {
                _selectionGroup.add(pos);
            }
        }
        _targetPanel.repaint();
    }

    // *********************** end Menus ************************

    @Override
    public void setAllEditable(boolean edit) {
        if (edit) {
            if (_editorMenu != null) {
                _menuBar.remove(_editorMenu);
            }
            if (_iconMenu == null) {
                makeIconMenu();
            } else {
                _menuBar.add(_iconMenu, 0);
            }
            if (_optionMenu == null) {
                makeOptionMenu();
            } else {
                _menuBar.add(_optionMenu, 0);
            }
            if (_fileMenu == null) {
                makeFileMenu();
            } else {
                _menuBar.add(_fileMenu, 0);
            }
        } else {
            if (_fileMenu != null) {
                _menuBar.remove(_fileMenu);
            }
            if (_optionMenu != null) {
                _menuBar.remove(_optionMenu);
            }
            if (_iconMenu != null) {
                _menuBar.remove(_iconMenu);
            }
            if (_editorMenu == null) {
                _editorMenu = new JMenu(Bundle.getMessage("MenuEdit"));
                _editorMenu.add(new AbstractAction(Bundle.getMessage("OpenEditor")) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        setAllEditable(true);
                    }
                });
            }
            _menuBar.add(_editorMenu, 0);
        }
        super.setAllEditable(edit);
        setTitle();
        _menuBar.revalidate();
    }

    @Override
    public void setUseGlobalFlag(boolean set) {
        //positionableBox.setEnabled(set);
        controllingBox.setEnabled(set);
        super.setUseGlobalFlag(set);
    }

    private void zoomRestore() {
        List<Positionable> contents = getContents();
        for (Positionable p : contents) {
            p.setLocation(p.getX() + _fitX, p.getY() + _fitY);
        }
        setPaintScale(1.0);
    }

    int _fitX = 0;
    int _fitY = 0;

    private void zoomToFit() {
        double minX = 1000.0;
        double maxX = 0.0;
        double minY = 1000.0;
        double maxY = 0.0;
        List<Positionable> contents = getContents();
        for (Positionable p : contents) {
            minX = Math.min(p.getX(), minX);
            minY = Math.min(p.getY(), minY);
            maxX = Math.max(p.getX() + p.getWidth(), maxX);
            maxY = Math.max(p.getY() + p.getHeight(), maxY);
        }
        _fitX = (int) Math.floor(minX);
        _fitY = (int) Math.floor(minY);

        JFrame frame = getTargetFrame();
        Container contentPane = getTargetFrame().getContentPane();
        Dimension dim = contentPane.getSize();
        Dimension d = getTargetPanel().getSize();
        getTargetPanel().setSize((int) Math.ceil(maxX - minX), (int) Math.ceil(maxY - minY));

        JScrollPane scrollPane = getPanelScrollPane();
        scrollPane.getHorizontalScrollBar().setValue(0);
        scrollPane.getVerticalScrollBar().setValue(0);
        JViewport viewPort = scrollPane.getViewport();
        Dimension dv = viewPort.getExtentSize();

        int dX = frame.getWidth() - dv.width;
        int dY = frame.getHeight() - dv.height;
        log.debug("zoomToFit: layoutWidth= {}, layoutHeight= {}\n\tframeWidth= {}, frameHeight= {}, viewWidth= {}, viewHeight= {}\n\tconWidth= {}, conHeight= {}, panelWidth= {}, panelHeight= {}",
                (maxX - minX), (maxY - minY), frame.getWidth(), frame.getHeight(), dv.width, dv.height, dim.width, dim.height, d.width, d.height);
        double ratioX = dv.width / (maxX - minX);
        double ratioY = dv.height / (maxY - minY);
        double ratio = Math.min(ratioX, ratioY);
        /*
         if (ratioX<ratioY) {
         if (ratioX>1.0) {
         ratio = ratioX;
         } else {
         ratio = ratioY;
         }
         } else {
         if (ratioY<1.0) {
         ratio = ratioX;
         } else {
         ratio = ratioY;
         }
         } */
        _fitX = (int) Math.floor(minX);
        _fitY = (int) Math.floor(minY);
        for (Positionable p : contents) {
            p.setLocation(p.getX() - _fitX, p.getY() - _fitY);
        }
        setScroll(SCROLL_BOTH);
        setPaintScale(ratio);
        setScroll(SCROLL_NONE);
        scrollNone.setSelected(true);
        //getTargetPanel().setSize((int)Math.ceil(maxX), (int)Math.ceil(maxY));
        frame.setSize((int) Math.ceil((maxX - minX) * ratio) + dX, (int) Math.ceil((maxY - minY) * ratio) + dY);
        scrollPane.getHorizontalScrollBar().setValue(0);
        scrollPane.getVerticalScrollBar().setValue(0);
        log.debug("zoomToFit: ratio= {}, w= {}, h= {}, frameWidth= {}, frameHeight= {}",
                ratio, (maxX - minX), (maxY - minY), frame.getWidth(), frame.getHeight());
    }

    @Override
    public void setTitle() {
        String name = getName();
        if (name == null || name.length() == 0) {
            name = Bundle.getMessage("SwitchboardDefaultName","");
        }
        if (isEditable()) {
            super.setTitle(name + " " + Bundle.getMessage("LabelEditor"));
        } else {
            super.setTitle(name);
        }
    }

    // all content loaded from file.
    public void loadComplete() {
        log.debug("loadComplete");
    }

    /**
     * After construction, initialize all the widgets to their saved config
     * settings.
     */
    @Override
    public void initView() {
        //positionableBox.setSelected(allPositionable());
        controllingBox.setSelected(allControlling());
        //showCoordinatesBox.setSelected(showCoordinates());
        showTooltipBox.setSelected(showTooltip());
        hiddenBox.setSelected(showHidden());
        switch (_scrollState) {
            case SCROLL_NONE:
                scrollNone.setSelected(true);
                break;
            case SCROLL_BOTH:
                scrollBoth.setSelected(true);
                break;
            case SCROLL_HORIZONTAL:
                scrollHorizontal.setSelected(true);
                break;
            case SCROLL_VERTICAL:
                scrollVertical.setSelected(true);
                break;
        }
        log.debug("InitView done");
    }

    /**
     * set up item(s) to be copied by paste
     *
     */
    @Override
    protected void copyItem(Positionable p) {
    };

    /**
     * ********* KeyListener of Editor ********
     */
    @Override
    public void keyPressed(KeyEvent e) {
        int x = 0;
        int y = 0;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
            case KeyEvent.VK_KP_UP:
            case KeyEvent.VK_NUMPAD8:
                y = -1;
                break;
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_KP_DOWN:
            case KeyEvent.VK_NUMPAD2:
                y = 1;
                break;
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_KP_LEFT:
            case KeyEvent.VK_NUMPAD4:
                x = -1;
                break;
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_KP_RIGHT:
            case KeyEvent.VK_NUMPAD6:
                x = 1;
                break;
            case KeyEvent.VK_D:
            case KeyEvent.VK_DELETE:
            case KeyEvent.VK_MINUS:
                //_shapeDrawer.delete();
                break;
            case KeyEvent.VK_A:
            case KeyEvent.VK_INSERT:
            case KeyEvent.VK_PLUS:
                //_shapeDrawer.add(e.isShiftDown());
                break;
            default:
                return;
        }
        if (e.isShiftDown()) {
            x *= 5;
            y *= 5;
        }
        if (_selectionGroup != null) {
            for (Positionable comp : _selectionGroup) {
                moveItem(comp, x, y);
            }
        }
        repaint();
    }

    /*
 * ********************* Mouse Methods ***********************
 */

    private long _clickTime;

    @Override
    public void mousePressed(MouseEvent event) {};

    @Override
    public void mouseReleased(MouseEvent event) {};

    @Override
    public void mouseClicked(MouseEvent event) {};

    @Override
    public void mouseDragged(MouseEvent event) {};

    @Override
    public void mouseMoved(MouseEvent event) {};

    @Override
    public void mouseEntered(MouseEvent event) {
        _targetPanel.repaint();
    }

    @Override
    public void mouseExited(MouseEvent event) {
        setToolTip(null);
        _targetPanel.repaint();  // needed for ToolTip
    }

    /**
     * ************* implementation of Abstract Editor methods **********
     */
    /**
     * The target window has been requested to close, don't delete it at this
     * time. Deletion must be accomplished via the Delete this panel menu item.
     */
    @Override
    protected void targetWindowClosingEvent(java.awt.event.WindowEvent e) {
        targetWindowClosing(true);
    }

    /**
     * Create sequence of panels, etc. for layout: JFrame contains its
     * ContentPane which contains a JPanel with BoxLayout (p1) which contains a
     * JScollPane (js) which contains the targetPane
     *
     */
    public JmriJFrame makeFrame(String name) {
        JmriJFrame targetFrame = new JmriJFrame(name);
        targetFrame.setVisible(true);

        JMenuBar menuBar = new JMenuBar();
        JMenu editMenu = new JMenu(Bundle.getMessage("MenuEdit"));
        menuBar.add(editMenu);
        editMenu.add(new AbstractAction(Bundle.getMessage("OpenEditor")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(true);
            }
        });
        editMenu.addSeparator();
        editMenu.add(new AbstractAction(Bundle.getMessage("DeletePanel")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (deletePanel()) {
                    dispose(true);
                }
            }
        });
        targetFrame.setJMenuBar(menuBar);
        // add switchboard menu
        JMenu sbMenu = new JMenu(Bundle.getMessage("SwitchboardMenu"));
        sbMenu.add(makeSelectTypeMenu());
        menuBar.add(sbMenu);

        targetFrame.addHelpMenu("package.jmri.jmrit.display.PanelTarget", true);
        return targetFrame;
    }

    protected void setSecondSelectionGroup(ArrayList<Positionable> list) {
        _secondSelectionGroup = list;
    }

    @Override
    protected void paintTargetPanel(Graphics g) {
        // needed to create PositionablePolygon
        //_shapeDrawer.paint(g);
        if (_secondSelectionGroup != null) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setColor(new Color(150, 150, 255));
            g2d.setStroke(new java.awt.BasicStroke(2.0f));
            for (Positionable p : _secondSelectionGroup) {
                if (!(p instanceof jmri.jmrit.display.controlPanelEditor.shape.PositionableShape)) {
                    g.drawRect(p.getX(), p.getY(), p.maxWidth(), p.maxHeight());
                }
            }
        }
    }

    /**
     * Set an object's location when it is created.
     */
    @Override
    public void setNextLocation(Positionable obj) {
        obj.setLocation(0, 0);
    }

    /**
     * Create popup for a Positionable object.
     * <p>
     * Popup items common to all
     * positionable objects are done before and after the items that pertain
     * only to specific Positionable types.
     */
    @Override
    protected void showPopUp(Positionable p, MouseEvent event) {
        if (!((JComponent) p).isVisible()) {
            return;     // component must be showing on the screen to determine its location
        }
        JPopupMenu popup = new JPopupMenu();

        PositionablePopupUtil util = p.getPopupUtility();
        if (p.isEditable()) {
            // items common to all
            if (p.doViemMenu()) {
                popup.add(p.getNameString());
                setPositionableMenu(p, popup);
                if (p.isPositionable()) {
                    setShowCoordinatesMenu(p, popup);
                    setShowAlignmentMenu(p, popup);
                }
                setDisplayLevelMenu(p, popup);
                setHiddenMenu(p, popup);
                popup.addSeparator();
                //setCopyMenu(p, popup);
            }

            // items with defaults or using overrides
            boolean popupSet = false;
//            popupSet |= p.setRotateOrthogonalMenu(popup);
            popupSet |= p.setRotateMenu(popup);
            popupSet |= p.setScaleMenu(popup);
            if (popupSet) {
                popup.addSeparator();
                popupSet = false;
            }
            popupSet = p.setEditItemMenu(popup);
            if (popupSet) {
                popup.addSeparator();
                popupSet = false;
            }
            if (p instanceof PositionableLabel) {
                PositionableLabel pl = (PositionableLabel) p;
                /*                if (pl.isIcon() && "javax.swing.JLabel".equals(pl.getClass().getSuperclass().getName()) ) {
                    popupSet |= setTextAttributes(pl, popup);       // only for plain icons
                }   Add backgrounds & text over icons later */
                if (!pl.isIcon()) {
                    popupSet |= setTextAttributes(pl, popup);
                    if (p instanceof MemoryIcon) {
                        popupSet |= p.setTextEditMenu(popup);
                    }
                } else if (p instanceof SensorIcon) {
                    popup.add(CoordinateEdit.getTextEditAction(p, "OverlayText"));
                    if (pl.isText()) {
                        popupSet |= setTextAttributes(p, popup);
                    }
                } else {
                    popupSet = p.setTextEditMenu(popup);
                }
            } else if (p instanceof PositionableJPanel) {
                popupSet |= setTextAttributes(p, popup);
            }
            if (p instanceof LinkingObject) {
                ((LinkingObject) p).setLinkMenu(popup);
            }
            if (popupSet) {
                popup.addSeparator();
                popupSet = false;
            }
            p.setDisableControlMenu(popup);
            if (util != null) {
                util.setAdditionalEditPopUpMenu(popup);
            }
            // for Positionables with unique settings
            p.showPopUp(popup);

            if (p.doViemMenu()) {
                setShowTooltipMenu(p, popup);
                setRemoveMenu(p, popup);
            }
        } else {
            p.showPopUp(popup);
            if (util != null) {
                util.setAdditionalViewPopUpMenu(popup);
            }
        }
        popup.show((Component) p, p.getWidth() / 2 + (int) ((getPaintScale() - 1.0) * p.getX()),
                p.getHeight() / 2 + (int) ((getPaintScale() - 1.0) * p.getY()));

        _currentSelection = null;
    }

    protected ArrayList<Positionable> getSelectionGroup() {
        return _selectionGroup;
    }

    private final static Logger log = LoggerFactory.getLogger(SwitchboardEditor.class.getName());
}
