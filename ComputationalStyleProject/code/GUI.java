/******
* Princeton University undergraduate senior thesis
* David M Kaplan, 2005-2006
* compilation:
* 		javac GUI.java
*		jar -cvf GUI.jar GUI*.class Dictionary.class Poem.class
* 		jar -cvf GUI.jar GUI*.class Dictionary.class Poem.class snlp_classes
*		jarsigner -keypass 112358 -storepass 112358 -keystore keystore GUI.jar dkapcert
* key generation (for 'dkapcert'):
*		keytool -genkey -alias dkapcert -keypass 112358 -keystore keystore -storepass 112358
* David Kaplan|Computer Science|Princeton University|Princeton|New Jersey|US
**/

import java.util.*;
import java.io.*;
import java.net.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

//for POS tagging
import hepple.postag.POSTagger;

//for PCA
import Jama.Matrix;
import Jama.SingularValueDecomposition;

public class GUI extends JApplet implements ActionListener, AdjustmentListener,
											ChangeListener, MouseListener {
	//globals
	static final String POEM_BREAK_MARKER = "******"; //6 asterisks
	
	static final boolean DEBUG_ON = true; //debugging output
	
	//All Poem objects currently in memory
	static Vector<Poem> poemCollection;
	static Poem selectedPoem;
	
	//main UI components
	static DisplayPane dPane;
	static JScrollPane dPaneScrollPane;
	static DetailsPane detailsPane;
	
	//Menu widgets
	static JMenuItem openPoemsMenuItem, openObjsMenuItem, openWeightsMenuItem, 
		saveMenuItem, saveWeightsMenuItem, outputDistancesMenuItem, clearMenuItem,
		outputValuesMenuItem;
	static JMenuItem flipAxesMenuItem;
	static JMenuItem zoomMoreMenuItem, zoomLessMenuItem ;
	static Vector<JCheckBoxMenuItem> poetMenuItems ;
	static JMenuItem viewNoPoetsMenuItem, viewAllPoetsMenuItem ;
	static JMenuItem viewSelectedDistancesMenuItem ;
	static JMenu poetSubMenu ;
	static JCheckBoxMenuItem viewBoxesMenuItem, taggedTextMenuItem;
	static JCheckBoxMenuItem blackBackgroundMenuItem;
	static JCheckBoxMenuItem calculateStressMenuItem, logDistancesMenuItem;
	static JCheckBoxMenuItem saveTextMenuItem;
	static JMenuItem helpMenuItem, aboutMenuItem ;
	static ButtonGroup fontButtons, zoomButtons, visualizationAlgorithmButtons ;

	//file chooser (for everything)
	static JFileChooser userFileChooser;

	//weights widgets and values
	static JFrame weightsFrame ;
	static JPanel weightsPanel ;
	static JCheckBox autoUpdateWeightsCheckBox;
	static JButton updateWeightsButton, clearWeightsButton, fullWeightsButton ;
	static boolean autoUpdateWeights = true;
	final static int MAX_WEIGHT = 500;
	
	//for use as stand-alone application
	static Hashtable<String,String> appParams ;
	static URL codeBase ;

	//GUI dimension
	static int screenWidth, screenHeight;

	//PCA/MDS, stress
	static boolean calculateStressOption;
	static boolean USE_LOG_DISTANCES = false; 	//fine-tuning parameters inside logNDDist
	static double displayStress = -1 ;
	static String visAlg = "PCA";
	static Thread calculateStressThread, doPCAThread ;
	static boolean calculateStressInProcess = false, stopCalculateStress=false,
					doPCAInProcess=false, stopDoPCA=false ;
	
	//distances from user shift-click
	static JList distancesList;
	static DefaultListModel distancesListModel;
	static JFrame distancesFrame;
	
	//distances from selected poem
	static JList[] selectedDistancesLists;
	static DefaultListModel[] selectedDistancesListModels;
	static JFrame selectedDistancesFrame;
	static JSplitPane selectedDistancesSplitPane ;
	static Vector<Double> selectedTotalDistancesVector;
	final static int selectedDistancePrecision = 6;
	static JScrollPane selectedDistancesJSP1 ;

	//for outputDistancesFile
	static Vector<Double> diffPoetDistances = null;
	static Hashtable<String,Integer> poetNameLookup = null;
	static Vector<String> poetNames = null;
	static ArrayList<ArrayList<Double>> samePoetDistances = null;
	
	//
	static boolean makeAveragePoem = true ;

	/**
	 * Static initializer (run one time only, when class is loaded)
	 */
	static {
		//initialize Thread methods, calculateStress and updateDistancesFromSelected
	}
	
	/**
	 * Constructor.
	 */
	public GUI() {super();}

	/**
	 * Applet Init()
	 */
	public void init() {
		poemCollection = new Vector<Poem>();
		selectedPoem = null;
		
		//if not set by application wrapper
		if (GUI.codeBase==null)	{
			GUI.codeBase = this.getCodeBase();
		}

		//read parameters
		//modified for use inside stand-alone application wrapper
		String dictFile, poemObjFilename, rulesFile, lexiconFile, contractionsFile, poemFilename, weightsFilename ;
		if (appParams==null) {
			dictFile = getParameter("dictionaryFile");
			poemObjFilename = getParameter("PoemObjFilename");
			rulesFile = getParameter("rulesFile");
			lexiconFile = getParameter("lexiconFile");
			contractionsFile = getParameter("contractionsFile");
			poemFilename = getParameter("PoemFilename");
			weightsFilename = getParameter("weightsFile");
		}
		else {
			dictFile = (String)appParams.get("dictionaryFile");
			poemObjFilename = (String)appParams.get("PoemObjFilename");
			rulesFile = (String)appParams.get("rulesFile");
			lexiconFile = (String)appParams.get("lexiconFile");
			contractionsFile = (String)appParams.get("contractionsFile");
			poemFilename = (String)appParams.get("PoemFilename");
			weightsFilename = (String)appParams.get("weightsFile");
		}

		Dictionary.dictFilename = dictFile;

		if (rulesFile==null || rulesFile.trim().length()<=0) {
			System.out.println("ERROR: NO RULES FILE parameter specified in HTML");
		}
		if (lexiconFile==null || lexiconFile.trim().length()<=0) {
			System.out.println("ERROR: NO lexiconFile specified in HTML");
		}
		if (contractionsFile==null || contractionsFile.trim().length()<=0) {
			System.out.println("ERROR: NO contractionsFile specified in HTML");
		}
		URL rulesURL = null, lexiconURL = null, contnURL = null ;
		try {
			Class c = this.getClass();
			rulesURL = c.getResource(rulesFile);
			rulesURL = (rulesURL==null) ? new URL(GUI.codeBase,rulesFile) : rulesURL ;
			lexiconURL = c.getResource(lexiconFile);
			lexiconURL = (lexiconURL==null) ? new URL(GUI.codeBase,lexiconFile) : lexiconURL;
			contnURL = c.getResource(contractionsFile);
			contnURL = (contnURL==null) ? new URL(GUI.codeBase,contractionsFile) : contnURL;
			System.out.println("Using rules file: "+rulesURL.toString());
			System.out.println("Using lexicon: "+lexiconURL.toString());
			System.out.println("Using contractions file: "+contnURL.toString());
			System.out.println("rulesRsrc="+this.getClass().getResource(rulesFile));
		} catch(Exception e) {
			System.out.println("Error loading files for part of speech tagger:");
			e.printStackTrace(System.out);
		}

		Poem.init(this,GUI.codeBase,rulesURL,lexiconURL,contnURL); //load dictionary, etc.
		System.out.println("Initialization complete.");
		
		//create GUI
	    try {
			javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					createGUI();
                }
			});
	    } catch (Exception e) { 
           	System.err.println("createGUI didn't successfully complete");
			e.printStackTrace(System.out);
        }

		repaint();

		//load weights file
		if (weightsFilename!=null && !weightsFilename.equals("")) {
			try {
				File f = null ;
				URL wU = this.getClass().getResource(weightsFilename);
				if (wU!=null) {
					f = new File(new URI(wU.toString()));
				}
				else {
					if (weightsFilename.substring(1,2).equals(":")) {
						weightsFilename = "file:/"+weightsFilename ; //add protocol for absolute URL
					}
					//need to make in URL syntax--look up online for method
					//poemFilename = poemFilename.replace(" ","%20"); //And others?
					System.out.println("weightsFilename:"+new URL(GUI.codeBase,weightsFilename).toString().replace(" ","%20"));
					f = new File(new URI(new URL(GUI.codeBase,weightsFilename).toString().replace(" ","%20")));
				}
				loadWeightsFile(f);
			} catch (Exception e) {
				System.out.println("Error loading weights file specified as Parameter.");
				e.printStackTrace(System.out);
			}
		}
		
		//load poems file
		if (poemFilename!=null && !poemFilename.equals("")) {
			try {
				File f = null ;
				URL fU = this.getClass().getResource(poemFilename);
				if (fU!=null) {
					f = new File(new URI(fU.toString()));
				}
				else {
					if (poemFilename.substring(1,2).equals(":")) {
						poemFilename = "file:/"+poemFilename ; //add protocol for absolute URL
					}
					//need to make in URL syntax--look up online for method
					//poemFilename = poemFilename.replace(" ","%20"); //And others?
					System.out.println("poem filename:"+new URL(GUI.codeBase,poemFilename).toString().replace(" ","%20"));
					f = new File(new URI(new URL(GUI.codeBase,poemFilename).toString().replace(" ","%20")));
				}
				loadUserFile(f);
			} catch (Exception e) {
				System.out.println("Error loading poems file specified as Parameter.");
				e.printStackTrace(System.out);
			}
		}

		//load pre-analyzed objects
		if (poemObjFilename!=null && !poemObjFilename.equals("")) {
			loadPoemObjs(poemObjFilename);
		}

		//PCA
		try {
			endDoPCA(); //unnecessary the first time...but good habit to get into
			doPCAThread = new Thread(new Runnable() {
				public void run() {
					doPCA();
					scalePoems(0);
					//if (DEBUG_ON) {
						//System.out.println("pre-updatePainting in init()");
					//}
					updatePainting();
					dPane.repaint();
					detailsPane.updateLegend();
					detailsPane.jpRight.repaint();
				}
			}, "doPCAThread");
			doPCAThread.start();
		} catch(Exception e) {
			JOptionPane.showMessageDialog(null, 
					"Error performing "+visAlg+"; see standard output for details.", 
					"Error", JOptionPane.ERROR_MESSAGE); 
			e.printStackTrace(System.out);
		}
	}

	private void createGUI() {
		JPanel mainPane = new JPanel(new BorderLayout());
		JMenuBar jmb;
		JMenu fileMenu;
		JMenu viewMenu;
		JMenu visualizationAlgorithmMenu;
		JMenu fontSubMenu, zoomSubMenu ;
		JRadioButtonMenuItem PCAMenuItem, CMDSMenuItem ;
		JRadioButtonMenuItem f8, f10, f12;
		JRadioButtonMenuItem zoom100, zoom200, zoom300;
		JMenu helpMenu ;

		JFrame.setDefaultLookAndFeelDecorated(true);

		//distances frame
		distancesListModel = new DefaultListModel();
		distancesList = new JList(distancesListModel);
        distancesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        //distancesList.setVisibleRowCount(20);
        JScrollPane listScrollPane = new JScrollPane(distancesList);
		JPanel distButtonsPanel = new JPanel();
		JButton deleteButton = new JButton("Delete");
		deleteButton.setActionCommand("deleteDistance");
		deleteButton.addActionListener(this);
		JButton deleteAllButton = new JButton("Delete All");
		deleteAllButton.setActionCommand("deleteAllDistances");
		deleteAllButton.addActionListener(this);
		distancesFrame = new JFrame("True distances");
		distancesFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		distancesFrame.setLayout(new BorderLayout());
		distancesFrame.add(listScrollPane, BorderLayout.CENTER);
		distButtonsPanel.add(deleteButton);
		distButtonsPanel.add(deleteAllButton);
		distancesFrame.add(distButtonsPanel, BorderLayout.SOUTH);
		distancesFrame.pack();
		distancesFrame.setVisible(false);
		
		//distances from selected poem frame
		selectedDistancesListModels = new DefaultListModel[Poem.numMetrics+2]; //name, total
		selectedDistancesLists = new JList[selectedDistancesListModels.length];
		JPanel selJP1 = new JPanel();
		final JPanel selJP2 = new JPanel();
		selJP1.setBackground(Color.white);
		selJP2.setBackground(Color.white);
		selJP1.setLayout(new GridLayout(1,0));
		selJP2.setLayout(new GridLayout(1,0));
		//selJP.setLayout(new GridLayout(1,selectedDistancesLists.length));
		selectedDistancesListModels[0] = new DefaultListModel();
		selectedDistancesListModels[0].add(0,"title/poet");
		for (int i=0;i<selectedDistancesLists.length;i++) {
			selectedDistancesListModels[i] = new DefaultListModel();
			if (i>1) {
				selectedDistancesListModels[i].add(0,Poem.metricNameLookup[i-2]);
				selectedDistancesLists[i]= new JList(selectedDistancesListModels[i]);
				selectedDistancesLists[i].setToolTipText(Poem.metricNameLookup[i-2]);
			}
			else { //i==1
				selectedDistancesListModels[i].add(0,"Total dist");
				selectedDistancesLists[i]= new JList(selectedDistancesListModels[i]);
				selectedDistancesLists[i].setToolTipText("Total distance");
			}

			//selectedDistancesLists[i].setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			//selectedDistancesLists[i].setVisibleRowCount(20); //Arbitrary
			if (i>0) {
				selJP2.add(selectedDistancesLists[i]);
			}
		}
		selJP1.add(selectedDistancesLists[0]);
		final JScrollPane jsp2 = new JScrollPane(selJP2, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
		jsp2.getVerticalScrollBar().addAdjustmentListener(this);
		//selJP1.setMinimumSize(new Dimension(25,0)); //for splitpane
		selectedDistancesJSP1 = new JScrollPane(selJP1, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
		selectedDistancesJSP1.setMinimumSize(new Dimension(1,1));
		selectedDistancesSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
				selectedDistancesJSP1, jsp2);
		selectedDistancesSplitPane.setResizeWeight(0.2);
		//final JScrollPane selectedListScrollPane = new JScrollPane(selectedDistancesSplitPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		selectedDistancesFrame = new JFrame("True distances from poem []");
		selectedDistancesFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		selectedDistancesFrame.setLayout(new BorderLayout());
		selectedDistancesFrame.add(selectedDistancesSplitPane, BorderLayout.CENTER);
		selectedDistancesFrame.pack();
		selectedDistancesFrame.setSize(500,selectedDistancesFrame.getHeight());
		selectedDistancesFrame.addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e) {
				//make sure right panel is OK for scrolling
				if (selectedDistancesSplitPane.getDividerLocation()<0) {
					jsp2.setPreferredSize(new Dimension(200,100));
				}
				else {
					jsp2.setPreferredSize(new Dimension(
						selectedDistancesFrame.getWidth() - selectedDistancesSplitPane.getDividerLocation() - selectedDistancesSplitPane.getDividerSize() - jsp2.getVerticalScrollBar().getWidth() - 8, //insets...guesstimate
						0));//selectedDistancesFrame.getHeight() - jsp2.getHorizontalScrollBar().getHeight() ));
				}
				//selJP2.revalidate();
				selectedDistancesSplitPane.revalidate();
				//jsp2.setPreferredSize(new Dimension(300,200));
				//selJP2.revalidate();
				if (DEBUG_ON) {
					//System.out.println("sdf.gw() - gdl - ds="+selectedDistancesFrame.getWidth()+" - "+selectedDistancesSplitPane.getDividerLocation()+" - "+selectedDistancesSplitPane.getDividerSize());
					//System.out.println("jsp2 size:"+jsp2.getSize());
					//System.out.println("jsp2 prefsize:"+jsp2.getPreferredSize());
				}
			}
		});
		selectedDistancesFrame.setVisible(false);
		selectedDistancesFrame.setBackground(Color.white);
		selectedTotalDistancesVector = new Vector<Double>();

		//display pane for drawing
		
		dPane = new DisplayPane();
		dPane.setOpaque(true);
		dPane.setBackground(Color.white);
		dPaneScrollPane = new JScrollPane(dPane,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		//dPaneScrollPane.getVerticalScrollBar().setBlockIncrement(30);
		//dPaneScrollPane.getHorizontalScrollBar().setBlockIncrement(30);
		mainPane.add(dPaneScrollPane, BorderLayout.CENTER);
		
		detailsPane = new DetailsPane();
		mainPane.add(detailsPane, BorderLayout.SOUTH);
		
		setContentPane(mainPane); //JApplet has only one child; this is it, now
		
		//set up file chooser for user poem file upload
		userFileChooser = new JFileChooser(); //default FILES_ONLY
		userFileChooser.setMultiSelectionEnabled(true); //multiple files at once

		//menu bar
		jmb = new JMenuBar();
		fileMenu = new JMenu("File");
		fileMenu.setMnemonic(KeyEvent.VK_F);
		viewMenu = new JMenu("View");
		viewMenu.setMnemonic(KeyEvent.VK_V);

		//File menu
		
		openPoemsMenuItem = new JMenuItem("Open poems file...", KeyEvent.VK_O);
		openPoemsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK));
		openPoemsMenuItem.addActionListener(this);
		fileMenu.add(openPoemsMenuItem);
		openObjsMenuItem = new JMenuItem("Open poem objects file...", KeyEvent.VK_B);
		openObjsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK | ActionEvent.ALT_MASK));
		openObjsMenuItem.addActionListener(this);
		fileMenu.add(openObjsMenuItem);
		openWeightsMenuItem = new JMenuItem("Open weights file...", KeyEvent.VK_G);
		//openWeightsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK));
		openWeightsMenuItem.addActionListener(this);
		fileMenu.add(openWeightsMenuItem);

		fileMenu.addSeparator();
		
		clearMenuItem = new JMenuItem("Clear all poems");
		clearMenuItem.addActionListener(this);
		fileMenu.add(clearMenuItem);
		
		//these should be in the weights pane!!
		/*clearWeightsMenuItem = new JMenuItem("Clear weights");
		clearWeightsMenuItem.addActionListener(this);
		fileMenu.add(clearWeightsMenuItem);

		fullWeightsMenuItem = new JMenuItem("Full weights");
		fullWeightsMenuItem.addActionListener(this);
		fileMenu.add(fullWeightsMenuItem);*/
		//these (above) should be in the weights frame
		
		fileMenu.addSeparator();

		saveTextMenuItem = new JCheckBoxMenuItem("Save poem text",false);
		saveTextMenuItem.setMnemonic(KeyEvent.VK_T);
		//no listener--just look @ menu item value
		fileMenu.add(saveTextMenuItem);
		
		saveMenuItem = new JMenuItem("Save to objects file...", KeyEvent.VK_S);
		saveMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK));
		saveMenuItem.addActionListener(this);
		fileMenu.add(saveMenuItem);

		saveWeightsMenuItem = new JMenuItem("Save weights to file...", KeyEvent.VK_W);
		saveWeightsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, ActionEvent.CTRL_MASK | ActionEvent.SHIFT_MASK));
		saveWeightsMenuItem.addActionListener(this);
		fileMenu.add(saveWeightsMenuItem);

		outputDistancesMenuItem = new JMenuItem("Output distances to file...", KeyEvent.VK_D);
		outputDistancesMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, ActionEvent.CTRL_MASK | ActionEvent.SHIFT_MASK));
		outputDistancesMenuItem.addActionListener(this);
		fileMenu.add(outputDistancesMenuItem);

		outputValuesMenuItem = new JMenuItem("Output values to file...", KeyEvent.VK_V);
		//outputValuesMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK | ActionEvent.SHIFT_MASK));
		outputValuesMenuItem.addActionListener(this);
		fileMenu.add(outputValuesMenuItem);

		jmb.add(fileMenu);
		
		//View menu
		
		viewBoxesMenuItem = new JCheckBoxMenuItem("View boxes", true); //default true
		viewBoxesMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.CTRL_MASK));
		viewBoxesMenuItem.setMnemonic(KeyEvent.VK_X);
		viewBoxesMenuItem.addChangeListener(this);
		viewMenu.add(viewBoxesMenuItem);

		blackBackgroundMenuItem = new JCheckBoxMenuItem("Black Background",false);
		blackBackgroundMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, ActionEvent.CTRL_MASK));
		blackBackgroundMenuItem.setMnemonic(KeyEvent.VK_B);
		blackBackgroundMenuItem.addChangeListener(this);
		viewMenu.add(blackBackgroundMenuItem);
		
		fontSubMenu = new JMenu("Font size");
		fontSubMenu.setMnemonic(KeyEvent.VK_F);
		f8 = new JRadioButtonMenuItem("8 pt",false);
		f8.setMnemonic(KeyEvent.VK_8);
		f10 = new JRadioButtonMenuItem("10 pt",true);
		f10.setMnemonic(KeyEvent.VK_0);
		f12 = new JRadioButtonMenuItem("12 pt",false);
		f12.setMnemonic(KeyEvent.VK_2);
		fontSubMenu.add(f8);
		fontSubMenu.add(f10);
		fontSubMenu.add(f12);

		fontButtons = new ButtonGroup();
		fontButtons.add(f8);
		fontButtons.add(f10);
		fontButtons.add(f12);

		f8.setActionCommand("8");
		f10.setActionCommand("10");
		f12.setActionCommand("12");

		f8.addActionListener(this);
		f10.addActionListener(this);
		f12.addActionListener(this);

		viewMenu.add(fontSubMenu);

		zoomSubMenu = new JMenu("Zoom");
		zoomSubMenu.setMnemonic(KeyEvent.VK_Z);
		zoom100 = new JRadioButtonMenuItem("100%",true);
		zoom100.setMnemonic(KeyEvent.VK_1);
		zoom200 = new JRadioButtonMenuItem("200%",false);
		zoom200.setMnemonic(KeyEvent.VK_2);
		zoom300 = new JRadioButtonMenuItem("300%",false);
		zoom300.setMnemonic(KeyEvent.VK_3);
		zoomLessMenuItem = new JMenuItem("Out", KeyEvent.VK_L);
		zoomLessMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, ActionEvent.CTRL_MASK));
		zoomMoreMenuItem = new JMenuItem("In", KeyEvent.VK_M);
		zoomMoreMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, ActionEvent.CTRL_MASK | ActionEvent.SHIFT_MASK));
		zoomSubMenu.add(zoomMoreMenuItem);
		zoomSubMenu.add(zoomLessMenuItem);
		zoomSubMenu.addSeparator();
		zoomSubMenu.add(zoom100);
		zoomSubMenu.add(zoom200);
		zoomSubMenu.add(zoom300);

		zoomButtons = new ButtonGroup();
		zoomButtons.add(zoom100);
		zoomButtons.add(zoom200);
		zoomButtons.add(zoom300);

		zoom100.setActionCommand("1");
		zoom200.setActionCommand("2");
		zoom300.setActionCommand("3");

		zoom100.addActionListener(this);
		zoom200.addActionListener(this);
		zoom300.addActionListener(this);
		zoomLessMenuItem.addActionListener(this);
		zoomMoreMenuItem.addActionListener(this);

		viewMenu.add(zoomSubMenu);

		flipAxesMenuItem = new JMenuItem("Flip Orientation 90\u00B0", KeyEvent.VK_O);
		flipAxesMenuItem.addActionListener(this);
		viewMenu.add(flipAxesMenuItem);
		
		taggedTextMenuItem = new JCheckBoxMenuItem("View tagged text", Poem.viewTaggedText); //default true
		taggedTextMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, ActionEvent.CTRL_MASK));
		taggedTextMenuItem.setMnemonic(KeyEvent.VK_T);
		taggedTextMenuItem.addChangeListener(this);
		viewMenu.add(taggedTextMenuItem);
		
		viewMenu.addSeparator();
		
		poetSubMenu = new JMenu("Poets");
		poetSubMenu.setMnemonic(KeyEvent.VK_P);
		poetMenuItems = new Vector<JCheckBoxMenuItem>();
		//add None/All options - deselect or select all checkboxes
		viewNoPoetsMenuItem = new JMenuItem("None", KeyEvent.VK_N);
		viewNoPoetsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, ActionEvent.CTRL_MASK | ActionEvent.SHIFT_MASK));
		viewAllPoetsMenuItem = new JMenuItem("All", KeyEvent.VK_A);
		viewAllPoetsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, ActionEvent.CTRL_MASK | ActionEvent.SHIFT_MASK));
		viewNoPoetsMenuItem.addActionListener(this);
		viewAllPoetsMenuItem.addActionListener(this);
		poetSubMenu.add(viewNoPoetsMenuItem);
		poetSubMenu.add(viewAllPoetsMenuItem);
		poetSubMenu.addSeparator();
		viewMenu.add(poetSubMenu);
		
		viewMenu.addSeparator();
		
		visualizationAlgorithmMenu = new JMenu("Visualization algorithm");
		visualizationAlgorithmMenu.setMnemonic(KeyEvent.VK_V);
		PCAMenuItem = new JRadioButtonMenuItem("PCA",true);
		CMDSMenuItem = new JRadioButtonMenuItem("CMDS",false);
		visualizationAlgorithmButtons = new ButtonGroup();
		visualizationAlgorithmButtons.add(PCAMenuItem);
		visualizationAlgorithmButtons.add(CMDSMenuItem);
		PCAMenuItem.setMnemonic(KeyEvent.VK_P);
		CMDSMenuItem.setMnemonic(KeyEvent.VK_C);
		PCAMenuItem.setActionCommand("PCA");
		CMDSMenuItem.setActionCommand("CMDS");
		PCAMenuItem.addActionListener(this);
		CMDSMenuItem.addActionListener(this);
		visualizationAlgorithmMenu.add(PCAMenuItem);
		visualizationAlgorithmMenu.add(CMDSMenuItem);
		viewMenu.add(visualizationAlgorithmMenu);

		calculateStressMenuItem = new JCheckBoxMenuItem("Calculate stress", false); //default false
		calculateStressMenuItem.addChangeListener(this);
		viewMenu.add(calculateStressMenuItem);
		
		viewMenu.addSeparator();
		
		logDistancesMenuItem = new JCheckBoxMenuItem("Use log distances", GUI.USE_LOG_DISTANCES); //default false
		logDistancesMenuItem.addChangeListener(this);
		//PSYCH!! yeah let's not use this. All the code is still in place though. viewMenu.add(logDistancesMenuItem);
		
		viewSelectedDistancesMenuItem = new JMenuItem("Distances from selected",KeyEvent.VK_S);
		viewSelectedDistancesMenuItem.addActionListener(this);
		viewMenu.add(viewSelectedDistancesMenuItem);

		jmb.add(viewMenu);
		
		helpMenu = new JMenu("Help");
		helpMenu.setMnemonic(KeyEvent.VK_H);
		helpMenuItem = new JMenuItem("Help",KeyEvent.VK_H);
		//ctl+?
		helpMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH,ActionEvent.CTRL_MASK | ActionEvent.SHIFT_MASK));
		aboutMenuItem = new JMenuItem("About",KeyEvent.VK_A);
		helpMenuItem.addActionListener(this);
		aboutMenuItem.addActionListener(this);
		helpMenu.add(helpMenuItem);
		helpMenu.add(aboutMenuItem);
		
		jmb.add(helpMenu);
		
		this.setJMenuBar(jmb);

		//FOR NOW: pop up weights frame
		weightsFrame = new JFrame("Weights");
		JPanel jp = new JPanel();
		jp.setLayout(new GridLayout(Poem.numMetrics+2,2));
		clearWeightsButton = new JButton("Clear/zero");
		clearWeightsButton.addActionListener(this);
		jp.add(clearWeightsButton);
		fullWeightsButton = new JButton("Full/100%");
		fullWeightsButton.addActionListener(this);
		jp.add(fullWeightsButton);
		updateWeightsButton = new JButton("Update");
		autoUpdateWeightsCheckBox = new JCheckBox("Automatically redraw",true);
		updateWeightsButton.setEnabled(!GUI.autoUpdateWeights);
		updateWeightsButton.addActionListener(this);
		autoUpdateWeightsCheckBox.addActionListener(this);
		jp.add(updateWeightsButton);
		jp.add(autoUpdateWeightsCheckBox);
		for (int i=0;i<Poem.numMetrics;i++) {
			jp.add(new JLabel(Poem.metricNameLookup[i]));
			JSlider js = new JSlider(SwingConstants.HORIZONTAL,0,MAX_WEIGHT,(int)Math.max(1,Math.min(MAX_WEIGHT,Math.round(MAX_WEIGHT*Poem.metricWeights[i])))); //when it goes to zero, PCA often flips orientation
			js.setMinorTickSpacing(1);
			js.setMajorTickSpacing(10);
			js.setToolTipText(Poem.metricNameLookup[i]);
			js.addChangeListener(this);
			jp.add(js);
		}
		JScrollPane scrollp = new JScrollPane(jp);
		jp.setPreferredSize(new Dimension(200,(int)(20*(Poem.numMetrics+1))));
		weightsFrame.setLayout(new BorderLayout());
		weightsFrame.setSize(300,500);
		weightsFrame.setPreferredSize(new Dimension(300,500));
		weightsFrame.add(scrollp, BorderLayout.CENTER);
		weightsFrame.setLocation(700,10);
		weightsFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		weightsFrame.setVisible(true);
		weightsPanel = jp; //set pointer

		//update
		//updatePainting();
		repaint();
	}
	
	public void start() {
		//don't need to "start" anything, just init()
	}

	public void stop() {
		//not really any "ongoing" execution that needs to be stopped
	}
	
	public void destroy() {
		//free resources
		clearAll();
		Dictionary.clear();
		weightsFrame.setVisible(false);
		weightsFrame = null ;
	}

	public String getAppletInfo() {
		return "Copyright (c) David Kaplan 2006. Created for senior thesis at Princeton.";
	}

	/**
	 * saveFile writes out the computed Poem metric objects to a file (f)
	 * if selected, also includes poem text
	 * only saves poems currently visible to user
	 */
	public void saveFile(File f) throws FileNotFoundException, IOException {
		BufferedWriter bw = null;
		//try {
			bw = new BufferedWriter(new FileWriter(f));
		//} catch (FileNotFoundException fnfe) {
		//	fnfe.printStackTrace(System.out);
		//}

		//First, write the header
		bw.write("Author|Title");
		for (int i=0;i<Poem.metricNameLookup.length;i++) {
			bw.write("|"+Poem.metricNameLookup[i]);
		}
		bw.newLine();

		Poem p;
		String tmpLine;
		for (int i=0;i<poemCollection.size();i++) {
			p = (Poem)poemCollection.elementAt(i);
			if (!p.isVisible()) {
				continue;
			}
			tmpLine = p.toString();
			bw.write(tmpLine);
			bw.newLine();
			//write text, if applicable
			if (saveTextMenuItem.getState() && p.text!=null) {
				for (int j=0;j<p.text.length;j++) {
					bw.write(p.text[j]);
					bw.newLine();
				}
			}
			bw.write(POEM_BREAK_MARKER);
			bw.newLine();
		}
		//close up
		bw.close();
	}
	
	/**
	 * saveWeightsFile writes out the metric weights to a file (f)
	 */
	public void saveWeightsFile(File f) throws FileNotFoundException, IOException {
		BufferedWriter bw = null;
		bw = new BufferedWriter(new FileWriter(f));

		double val;
		String name;
		for (int i=0;i<Poem.metricWeights.length;i++) {
			name = Poem.metricNameLookup[i];
			val = Poem.metricWeights[i];
			bw.write(name+"="+val);
			bw.newLine();
		}
		//close up
		bw.close();
	}
	
	public void loadUserFile(File f) {
		if (GUI.doPCAInProcess && !GUI.stopDoPCA) { //it's running AND not trying to be stopped
			//stop prev thread, start new one
			//NOTWORKINGGUI.calculateStressThread.interrupt();
			stopDoPCA = true; //checked in loops in run()
		}

		System.out.println("Loading poems file: "+f.toString());
		long t1 = System.currentTimeMillis();
		//read file, parse & store
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(f));
		} catch (FileNotFoundException fnfe) {
			System.out.println("File not found!");
			fnfe.printStackTrace(System.out);
		}
		String line = null;
		String[] poemArr ;
		Vector<String[]> collection = new Vector<String[]>();
		Vector<String> poem = new Vector<String>();
		try {
			line = br.readLine(); //title
			while ( line != null ) {
				poem = new Vector<String>();
				poem.add(line);
				line = br.readLine(); //author
				poem.add(line);
				line = br.readLine();
				while (line!=null && !line.equals(POEM_BREAK_MARKER)) {
					poem.add(line);
					line = br.readLine();
				}

				poem.trimToSize();
				poemArr = new String[poem.size()];
				poem.copyInto(poemArr);
				collection.add(poemArr);

				// line==null || line==POEM_BREAK_MARKER
				if (line != null) {
					line = br.readLine(); //title
				}
			}
			collection.trimToSize();
			br.close();
		} catch(IOException ioe) {
			System.out.println("I/O error trying to load poems file:");
			ioe.printStackTrace(System.out);
		} catch(Exception e) {
			System.out.println("Non-I/O error trying to load poems file:");
			e.printStackTrace(System.out);
		}
		//analyze poems-->objects-->store in poemCollection
		ListIterator li = collection.listIterator();
		while (li.hasNext()) {
			poemArr = (String[])li.next();
			Poem p = new Poem(poemArr);
			p.runMetrics();
			addPoemToCollection(p);
			//System.out.print("|"+poemCollection.size());
			//poemCollection.add(p);
			//dPane.add(p);
		}
		poemCollection.trimToSize();
		
		//create AVERAGE poem foreach poet:
		if (GUI.makeAveragePoem) {
			//weighted average by wordCount for (most) Freq
			//weighted avg by numLines for all Rhyme
			//straight average for wordCount, numStanzas, numLines, avgLineLength, avgLinesPerStanza
			//one double[] of values foreach poet in collection (get from Poem Colors?)
			//iterate through collection, summing weighted values AND wc/numLines/numPoems
			//divide through by (total wc/total numLines/total numPoems)
		}
		
		if (DEBUG_ON) {
			System.out.println("loadUserFile took "+(System.currentTimeMillis()-t1)/1000+" seconds (now: PCA etc)");
			System.out.println(collection.size()+" (or fewer) poems added for a total of "+poemCollection.size());
		}
	}

	/**
	 * Load pre-calculated Poem objects from a text file, specified in HTML <PARAM>
	 */
	private void loadPoemObjs(String filename) {
		URL poemInputURL = null;
		BufferedReader br;
		try {
			poemInputURL = this.getClass().getResource(filename);
			poemInputURL = (poemInputURL==null) ? new URL(GUI.codeBase+filename) : poemInputURL ;
			URLConnection conn = poemInputURL.openConnection();
			br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		} catch(MalformedURLException mue) {
			System.out.println("Malformed URL when trying to load poem objects file:");
			mue.printStackTrace(System.out);
			return;
		} catch(IOException ioe) {
			System.out.println("I/O error while trying to load poem objects file:");
			ioe.printStackTrace(System.out);
			return;
		}
		loadPoemObjs(br);
	}
	
	private void loadPoemObjs(File f) {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(f));
		} catch (FileNotFoundException fnfe) {
			System.out.println("File not found!");
			fnfe.printStackTrace(System.out);
			return;
		}
		loadPoemObjs(br);
	}
	
	private void loadPoemObjs(BufferedReader br) {
		if (GUI.doPCAInProcess && !GUI.stopDoPCA) { //it's running AND not trying to be stopped
			//stop prev thread, start new one
			//NOTWORKINGGUI.calculateStressThread.interrupt();
			stopDoPCA = true; //checked in loops in run()
		}

		long t1 = System.currentTimeMillis();
		String line = null;
		String[] lineTokens = null;
		Poem p = null;
		double[] metricValues;
		try {
			if ( (line=br.readLine()) == null) {
				return; //header line (no data) is null
			}
			else {
				//System.out.println("Header:"+line);
			}
			while ( (line=br.readLine()) != null) {
				lineTokens = line.split("\\|");
				//System.out.println("PoemObj:"+line);
				metricValues = new double[lineTokens.length-2];
				for (int i=2;i<lineTokens.length;i++) {
					//parse String into double
					try {
						metricValues[i-2] = Double.parseDouble(lineTokens[i]);
					} catch(Exception e) {System.err.println("Error parsing poem objects file double values.");}
				}
				p = new Poem(lineTokens[0], lineTokens[1], metricValues); //author, title
				//get text, if any
				Vector<String> v = new Vector<String>();
				while ( (line=br.readLine())!=null && !line.equals(POEM_BREAK_MARKER) ) {
					v.add(line);
				}
				if (v.size()<=0) {
					p.text = null;
				}
				else {
					p.text = new String[v.size()];
					v.copyInto(p.text);
				}
				addPoemToCollection(p);
				//poemCollection.add(p);
				//dPane.add(p);
				//System.out.println("Poem loaded:"+p.getTitle());
			}
			//selectedPoem = poemCollection.elementAt(0);
			//detailsPane.updatePoem(selectedPoem);
			poemCollection.trimToSize();
			br.close();
		} catch(IOException ioe) {
			System.out.println("I/O error trying to load poem objects file:");
			ioe.printStackTrace(System.out);
		} catch(Exception e) {
			//array
			System.out.println("Non-I/O error trying to load poem objects file:");
			e.printStackTrace(System.out);
		}
		if (DEBUG_ON) {
			System.out.println("loadPoemObjs took "+(System.currentTimeMillis()-t1)/1000+" seconds (now: PCA etc)");
		}
	}
	
	/**
	 * Load weights; set weights; set JSlider values
	 */
	private void loadWeightsFile(File f) {
		if (GUI.doPCAInProcess && !GUI.stopDoPCA) { //it's running AND not trying to be stopped
			//stop prev thread, start new one
			//NOTWORKINGGUI.calculateStressThread.interrupt();
			stopDoPCA = true; //checked in loops in run()
		}

		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(f));
			String ln = null, name=null, valStr=null;
			while ((ln=br.readLine()) != null) {
				try {
					name = ln.substring(0,ln.indexOf("="));
					valStr = ln.substring(ln.indexOf("=")+1);
					Poem.metricWeights[Poem.getMetricIndex(name)] = Double.parseDouble(valStr);
				} catch(Exception e1) {
					//do nothing?
					if (DEBUG_ON) {
						e1.printStackTrace(System.out);
					}
				}
			}
			br.close();

			//set Sliders
			Component[] cArr = weightsPanel.getComponents();
			boolean oldAutoUpdate = GUI.autoUpdateWeights;
			GUI.autoUpdateWeights = false;
			for (int i=0;i<cArr.length;i++) {
				if (cArr[i] instanceof JSlider) {
					JSlider js = (JSlider)cArr[i];
					String metricName = js.getToolTipText();
					js.setValue((int)(Math.round(Poem.metricWeights[Poem.getMetricIndex(metricName)]*(double)js.getMaximum())));
				}
			}
			GUI.autoUpdateWeights = oldAutoUpdate;
		} catch (FileNotFoundException fnfe) {
			System.out.println("File not found!");
			fnfe.printStackTrace(System.out);
			return;
		} catch(Exception e) {
			System.out.println("Error while loading weights file:");
			e.printStackTrace(System.out);
			return;
		}
	}

	/**
	 * Add p to the collection, DELETING any existing copy with identical author/title
	 * also add to dPane
	 * Could make this more efficient (keep hashtable lookup by title+author), but this won't be a bottleneck
	 */
	private void addPoemToCollection(Poem p) {
		//loop through, delete if identical
		Poem p2;
		String a1 = p.getAuthor();
		String t1 = p.getTitle();
		//CAN'T use if delete anything...ListIterator li = poemCollection.listIterator();
		//while (li.hasNext()) {
		boolean makeNewMenuItemFlag = true ; //for View menu
		for (int i=0;i<poemCollection.size();i++) {
			p2 = (Poem)poemCollection.elementAt(i);
			//p2 = (Poem)li.next();
			//System.out.println("checking "+a1+"'s "+t1+" against "+p2.getTitle()+" by "+p2.getAuthor());
			if (p2!=null && a1.equals(p2.getAuthor())) {
				makeNewMenuItemFlag = false ;
				if (t1.equals(p2.getTitle())) {
					//poemCollection.remove(p2);
					poemCollection.removeElementAt(i);
					i--; //since everything shifts, don't want to skip any
					dPane.remove(p2);
					//System.out.println("deleted "+p2.getTitle()+" by "+p2.getAuthor());
				}
			}
		}
		poemCollection.trimToSize();
		
		if (makeNewMenuItemFlag) {
			JCheckBoxMenuItem jcb = new JCheckBoxMenuItem(a1,true);
			jcb.addActionListener(this);
			poetMenuItems.add(jcb); //for View menu
			poetSubMenu.add(jcb);
		}
		
		//add p
		poemCollection.add(p);
		dPane.add(p);
	}
	
	/**
	 * Find Max of array
	 */
	public static double findMax(double[] dArr) {
		if (dArr==null || dArr.length<=0) return 0; //should throw exception...
		double ret = dArr[0];
		for (int i=1;i<dArr.length;i++) {
			if (dArr[i]>ret) {
				ret = dArr[i];
			}
		}
		return ret;
	}

	/**
	 * Find Min of array
	 */
	public static double findMin(double[] dArr) {
		if (dArr==null || dArr.length<=0) return 0; //should throw exception...
		double ret = dArr[0];
		for (int i=1;i<dArr.length;i++) {
			if (dArr[i]<ret) {
				ret = dArr[i];
			}
		}
		return ret;
	}

	/**
	 * Does classical MDS analysis of poem objects in poemCollection
	 * Stores xDraw and yDraw raw values
	 * JMathTools.sourceforge.net -or- http://www.cs.princeton.edu/introcs/95linear/SVD.java.html using JAMA (from NIST)
	 */
	private void doCMDS() {
		/**TEMP: TEST SVD TIMES for PCA
		Matrix A;
		SingularValueDecomposition tmpSVD;
		long tmpt1 ;
		for (int k=1;k==k;k*=10) {
			A = Matrix.random(k,Poem.numMetrics);
			try {
				tmpt1 = System.currentTimeMillis();
				tmpSVD = A.svd();
				System.out.println(k+"	"+(System.currentTimeMillis()-tmpt1));
			} catch(Exception e) {e.printStackTrace(System.out);}
		}*/
		//D is distance matrix; dii=0 and dij+djk>=dik
		//construct D from metric values
		//get Matrix of all values; do nDDist on rows
		int numPoems = poemCollection.size();
		int i, j;
		Poem p;

		/*for HISTOGRAM data output*/
		String[] poetsArr = new String[numPoems];
		GUI.diffPoetDistances = new Vector<Double>();
		GUI.poetNameLookup = new Hashtable<String,Integer>();
		GUI.poetNames = new Vector<String>();
		/* */

		if (numPoems<=0) {
			return;
		}

		//memory profiling
		Runtime rt = Runtime.getRuntime();
		//rt.gc();
		//System.out.println("0before CMDS, freeMemory="+rt.freeMemory()+"; maxMem="+rt.maxMemory()+"; totMem="+rt.totalMemory());
		
		long t1 = System.currentTimeMillis();

		Matrix D ; //the distances
		double[][] Dvals = new double[numPoems][numPoems] ;

		//concatenate all poems together
		double[][] values = new double[numPoems][Poem.numMetrics];
		for (i=0;i<numPoems && !GUI.stopDoPCA;i++) {
			p = (Poem)poemCollection.elementAt(i);
			System.arraycopy(p.metricValues, 0,
				values[i], 0, Poem.numMetrics);
				
			/*for HISTOGRAM DATA*/
			poetsArr[i] = p.getAuthor();
			if (!poetNameLookup.containsKey(poetsArr[i])) {
				//add, with new index
				poetNameLookup.put(poetsArr[i],new Integer(poetNameLookup.size()));
				poetNames.add(poetsArr[i]);
				if (DEBUG_ON) {
					System.out.println("added "+poetsArr[i]+";size="+poetNameLookup.size());
				}
			}
		}
		if (GUI.stopDoPCA) {
			GUI.stopDoPCA = false;
			GUI.doPCAInProcess = false;
			return;
		}

		/*for HISTOGRAM DATA*/
		samePoetDistances = new ArrayList<ArrayList<Double>>(poetNameLookup.size());
		for (i=0;i<poetNameLookup.size();i++) {
			samePoetDistances.add(new ArrayList<Double>());
		}
		/* */
		
		//rt.gc();
		//System.out.println("1during CMDS, freeMemory="+rt.freeMemory()+"; maxMem="+rt.maxMemory()+"; totMem="+rt.totalMemory());

		for (i=0;i<numPoems && !GUI.stopDoPCA;i++) {
			for (j=0;j<=i;j++) {
				if (i==j) {
					Dvals[i][j] = 0;
				}
				else {
					//actual weighted (Euclidean) distance btwn Poem i & j
					Dvals[i][j] = (USE_LOG_DISTANCES) ? logNDDist(values[i],values[j],Poem.metricWeights) : nDDist(values[i],values[j],Poem.metricWeights) ;
					
					/*for HISTOGRAM DATA*/
					if (poetsArr[i].equals(poetsArr[j])) {
						samePoetDistances.get(((Integer)poetNameLookup.get(poetsArr[i])).intValue()).add(
								new Double(Dvals[i][j]));
					}
					else {
						diffPoetDistances.add(new Double(Dvals[i][j]));
					}
					/* */
				}
			}
		}
		if (GUI.stopDoPCA) {
			GUI.stopDoPCA = false;
			GUI.doPCAInProcess = false;
			return;
		}

		/*values[][] free*/
		values = null;

		//fill in second half
		for (i=0;i<numPoems;i++) {
			for (j=i+1;j<numPoems;j++) {
				Dvals[i][j] = Dvals[j][i] ;
			}
		}
		
		//rt.gc();
		//System.out.println("2during CMDS, freeMemory="+rt.freeMemory()+"; maxMem="+rt.maxMemory()+"; totMem="+rt.totalMemory());

		//instantiate D
		D = new Matrix(Dvals);

		/*Dvals[][] free*/
		Dvals = null ;

		double[][] iMatVals = new double[numPoems][1] ; //col vector
		for (i=0;i<numPoems;i++) {
			iMatVals[i][0] = 1 ;
		}
		Matrix iMat = new Matrix(iMatVals);

		/*iMatVals[][] free*/
		iMatVals = null;

		rt.gc();
		if (DEBUG_ON) {
			System.out.println("3during CMDS, freeMemory="+rt.freeMemory()+"; maxMem="+rt.maxMemory()+"; totMem="+rt.totalMemory());
		}

		if (GUI.stopDoPCA) {
			GUI.stopDoPCA = false;
			GUI.doPCAInProcess = false;
			return;
		}

		//B = -1/2*[I - 1/n*i*i']*D^2*[I-1/n*i*i']
		//I is nxn identity; i is "unity vector length n"
		//note: n=number of objects; m=#dimensions
		Matrix B = Matrix.identity(numPoems,numPoems).minus(iMat.times(iMat.transpose()).times(1/(double)numPoems)).times(D.arrayTimesEquals(D)).times(Matrix.identity(numPoems,numPoems).minus(iMat.times(iMat.transpose()).times(1/(double)numPoems))).times(-0.5);

		/*D, iMat free*/
		D = null;
		iMat = null;

		//rt.gc();
		//System.out.println("3.5during CMDS, freeMemory="+rt.freeMemory()+"; maxMem="+rt.maxMemory()+"; totMem="+rt.totalMemory());
		long t2 = System.currentTimeMillis();

		if (GUI.stopDoPCA) {
			GUI.stopDoPCA = false;
			GUI.doPCAInProcess = false;
			return;
		}

		//decompose: SVD(B) = VUV' = XX'
		SingularValueDecomposition svd = B.svd(); //=USV'

		if (GUI.stopDoPCA) {
			GUI.stopDoPCA = false;
			GUI.doPCAInProcess = false;
			return;
		}
		
		if (DEBUG_ON) {
			System.out.println("SVD (alone) took "+(System.currentTimeMillis()-t2)+" ms for "+numPoems+" poems");
		}
			
		/*B free*/
		B = null;

		//rt.gc();
		//System.out.println("4during CMDS, freeMemory="+rt.freeMemory()+"; maxMem="+rt.maxMemory()+"; totMem="+rt.totalMemory());

		Matrix U = svd.getU();
		Matrix S = svd.getS();

		/*svd free*/
		svd = null ;

		//rt.gc();
		//System.out.println("5during CMDS, freeMemory="+rt.freeMemory()+"; maxMem="+rt.maxMemory()+"; totMem="+rt.totalMemory());

		//X = U*S^1/2
		for (i=0;i<S.getRowDimension();i++) {
			for (j=0;j<S.getColumnDimension();j++) {
				S.set(i,j,Math.sqrt(S.get(i,j)));
			}
		}
		Matrix X = U.times(S);

		if (GUI.stopDoPCA) {
			GUI.stopDoPCA = false;
			GUI.doPCAInProcess = false;
			return;
		}

		/*U, S free*/
		U = null;
		S = null;

		rt.gc();
		if (DEBUG_ON) {
			System.out.println("6during CMDS, freeMemory="+rt.freeMemory()+"; maxMem="+rt.maxMemory()+"; totMem="+rt.totalMemory());
		}
		
		//keep first 2 columns of X for coordinates
		for (i=0;i<numPoems;i++) {
			p = (Poem)poemCollection.elementAt(i);
			p.setXPosRaw(X.get(i,0));
			p.setYPosRaw(X.get(i,1));
		}

		//rt.gc();
		//System.out.println("4after CMDS, freeMemory="+rt.freeMemory()+"; maxMem="+rt.maxMemory()+"; totMem="+rt.totalMemory());
		if (DEBUG_ON) {
			System.out.println("doCMDS took " + ((System.currentTimeMillis()-t1)/1000)+" seconds");
		}
	}

	/**
	 * Write all poem-poem distances to file, for statistical analysis
	 * Needs a File to write to, plus that the distances have been calculated by CMDS
	 */
	public void outputDistancesFile(File f) {
		int i, j;
		if (GUI.diffPoetDistances==null || GUI.samePoetDistances==null || GUI.poetNameLookup==null) {
			//error box
			JOptionPane.showMessageDialog(null, 
				"CMDS must be used to output distances (not PCA)", 
				"Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		/*HISTOGRAM DATA output*/
		if (DEBUG_ON) {
			System.out.println("numPoets="+poetNameLookup.size()+";dPDs.size="+diffPoetDistances.size());
		}
		int numPoems = poemCollection.size();
		//to file, since many numbers
		BufferedWriter bw=null;
		try {
			if (f==null) {
				f = new File("dists"+System.currentTimeMillis()+".txt");
			}
			bw = new BufferedWriter(new FileWriter(f));
			bw.write(numPoems*(numPoems-1)/2+" total distances; order of display is:");
			bw.newLine();
			int matlab_index_old=1, matlab_index_new;
			for (i=0;i<samePoetDistances.size();i++) {
				matlab_index_new = matlab_index_old+samePoetDistances.get(i).size() - 1;
				bw.write(poetNames.elementAt(i)+" ("+samePoetDistances.get(i).size()+") "
						+ poetNames.elementAt(i).substring(poetNames.elementAt(i).lastIndexOf(" ")+1).toLowerCase()
						+ " = data("+matlab_index_old+":"+matlab_index_new+");");
				bw.newLine();
				matlab_index_old = matlab_index_new + 1;
			}
			matlab_index_new = matlab_index_old + diffPoetDistances.size()-1;
			bw.write("Inter-poet distances ("+diffPoetDistances.size()+") ("+matlab_index_old+":"+matlab_index_new+");");
			bw.newLine();
			//first by poet, then diff
			//CAN'T WRITE AUTHORS OR ANYTHING BETWEEN NUMBERS IF MATLAB CAN OPEN IT
			ArrayList<Double> v;
			for (i=0;i<samePoetDistances.size();i++) {
				v = (ArrayList<Double>)samePoetDistances.get(i);
				if (DEBUG_ON) {
					System.out.println(v.size()+" dists for "+poetNames.elementAt(i));
				}
				for (j=0;j<v.size();j++) {
					bw.write(v.get(j).toString());
					bw.newLine();
				}
				//bw.newLine();
			}
			for (i=0;i<diffPoetDistances.size();i++) {
				bw.write(diffPoetDistances.elementAt(i).toString());
				bw.newLine();
			}
			bw.close();
		} catch(Exception e) {
			if (bw!=null) {
				try {
					bw.close();
				} catch(Exception e2) {
					if (DEBUG_ON) {
						e2.printStackTrace(System.out);
					}
				}
			}
			System.out.println("Error trying to output distances file:");
			e.printStackTrace(System.out);
		}
	}
		
	/**
	 * Write all poem metric values to Tab-separated file, for statistical analysis
	 * Needs a File to write to
	 */
	public void outputValuesFile(File f) {
		int i;
		BufferedWriter bw=null;
		try {
			if (f==null) {
				f = new File("vals"+System.currentTimeMillis()+".txt");
			}
			bw = new BufferedWriter(new FileWriter(f));

			//write headers
			bw.write("Poem/poet");
			for (i=0;i<Poem.numMetrics;i++) {
				bw.write("\t"+Poem.metricNameLookup[i]);
			}
			bw.newLine();
			
			//write values, preceded by no-space poem/poet
			Poem p;
			String s;
			double d;
			for (i=0;i<poemCollection.size();i++) {
				p = (Poem)poemCollection.elementAt(i);
				bw.write(p.getTitle().replace(" ","")+"/"+p.getAuthor().replace(" ",""));
				for (int j=0;j<Poem.numMetrics;j++) {
					d = p.metricValues[j];
					if (d > 0.001) {
						s = new Double(p.metricValues[j]).toString();
						bw.write("\t"+s.substring(0,Math.min(6,s.length())));
					}
					else { //will be scientific notations: E-9 etc
						bw.write("\t");
						bw.write("0.000");//at least
						d *= 10000; //10^4
						if (d<1 && d>=0.5) {
							bw.write("1");
						}
						else if (d<0.5) {
							bw.write("0");
						}
						else {
							bw.write(new Double(d).toString().substring(0,1));
						}
					}
				}
				bw.newLine();
			}

			bw.close();
		} catch(Exception e) {
			if (bw!=null) {
				try {
					bw.close();
				} catch(Exception e2) {
					if (DEBUG_ON) {
						e2.printStackTrace();
					}
				}
			}
			System.out.println("Error trying to output values file:");
			e.printStackTrace(System.out);
		}
	}

	/**
	 * Stops the current doPCAThread, if there is one, and then returns.
	 */
	private void endDoPCA() {
		if (GUI.doPCAInProcess && !stopDoPCA) { //it's running AND not trying to be stopped
			//stop prev thread, start new one
			//NOTWORKINGGUI.calculateStressThread.interrupt();
			stopDoPCA = true; //checked in loops in run()
		}

		//wait for old one to end--there is a Thread waiting, so wait for it
		while (GUI.doPCAInProcess) {
			if (DEBUG_ON) {
				System.out.println("sleeping in endDoPCA() until old one stops");
			}
			try {
				Thread.sleep(333);
			} catch(Exception e) {
				if (DEBUG_ON) {
					e.printStackTrace(System.out);
				}
			}
		}
	}
	
	/**
	 * Does PCA analysis of poem objects in poemCollection
	 * Stores xDraw and yDraw from projection onto PCA axes
	 * JMathTools.sourceforge.net -or- http://www.cs.princeton.edu/introcs/95linear/SVD.java.html using JAMA (from NIST)
	 */
	private void doPCA() {
		if (GUI.calculateStressInProcess && !stopCalculateStress) {
			//stop prev thread, start new one
			//NOTWORKINGGUI.calculateStressThread.interrupt();
			stopCalculateStress = true; //checked in loops in run()
			/*new Thread(new Runnable() {
				public void run() {
					while (GUI.stopCalculateStress && GUI.calculateStressInProcess) {
						System.out.println("Sleeping in Thread at top of doPCA()");
						try {
							Thread.sleep(333);
						} catch(Exception e) {
							e.printStackTrace(System.out);
						}
					}
					GUI.calculateStressInProcess = false;
				}
			}).start();
			*/ //why did I do that?? calcStrInProc is set to false when stopCalc is caught
		}
		
		if (poemCollection.size()<=0) {
			return;
		}
		GUI.doPCAInProcess = true ;
		if (visAlg.equals("CMDS")) {
			doCMDS(); //for comparison, for now
		}
		else {
			//System.out.println("Starting doPCA()");
			GUI.diffPoetDistances = null;
			GUI.samePoetDistances = null;
			GUI.poetNameLookup = null;
			GUI.poetNames = null;
			long t1 = System.currentTimeMillis();
			if (poemCollection.size()<=0) {
				return;
			}
			//concatenate all poems together
			double[][] values = new double[poemCollection.size()][Poem.numMetrics];
			//System.out.println("values[][] created");
			for (int i=0;i<poemCollection.size() && !GUI.stopDoPCA;i++) {
				System.arraycopy(((Poem)poemCollection.elementAt(i)).metricValues, 0,
					values[i], 0, Poem.numMetrics);
			}
			
			//get averages
			double[] valAverages = new double[Poem.numMetrics];
			//zero array
			for (int i=0;i<Poem.numMetrics;i++) {
				valAverages[i]=0;
			}
			//sum all values
			for (int i=0;i<poemCollection.size() && !GUI.stopDoPCA;i++) {
				for (int j=0;j<Poem.numMetrics;j++) {
					valAverages[j] += values[i][j];
				}
			}
			//divide by number of poems to get average value foreach metric
			for (int i=0;i<Poem.numMetrics;i++) {
				valAverages[i] /= poemCollection.size();
			}
			//subtract averages from values[][]
			for (int i=0;i<poemCollection.size() && !GUI.stopDoPCA;i++) {
				for (int j=0;j<Poem.numMetrics;j++) {
					values[i][j] -= valAverages[j];
				}
			}

			if (GUI.stopDoPCA) {
				GUI.stopDoPCA = false;
				GUI.doPCAInProcess = false;
				return;
			}
			//System.out.println("values[][] filled, "+((System.currentTimeMillis()-t1)/1000)+" seconds into method");
	
			//set up matrix
			Matrix A = new Matrix(values);
			//PROFILING NOTE: takes long time btwn "topVerbFreq" and the following line--I think
			//System.out.println("A (all poems):\n"+GUI.toString(A)+"\n"+((System.currentTimeMillis()-t1)/1000)+" seconds into method");
			//System.out.println("A (all poems), "+((System.currentTimeMillis()-t1)/1000)+" seconds into method");
	
			//THIS IS OLD--USING BINS NOW//normalize wordCount etc. to [0..1]
			//first get max WC, numLines, etc.
			//double maxwc = 0, maxnl = 0, maxavgll = 0;
			//int wcIndex = Poem.getMetricIndex("wordCount");
			//int nlIndex = Poem.getMetricIndex("numLines");
			//int avgllIndex = Poem.getMetricIndex("avgLineLength");
			//for (int i=0;i<poemCollection.size();i++) {
			//	maxwc = Math.max(maxwc,values[i][wcIndex]);
			//	maxnl = Math.max(maxnl,values[i][nlIndex]);
			//	maxavgll = Math.max(maxavgll,values[i][avgllIndex]);
			//}
			Matrix weightsMatrix = Matrix.identity(Poem.numMetrics,Poem.numMetrics);
			//multiply by weights
			//System.out.print("weights:, "+((System.currentTimeMillis()-t1)/1000)+" seconds into method");
			for (int i=0;i<Poem.numMetrics;i++) {
			//	System.out.print(" "+Poem.metricWeights[i]);
				weightsMatrix.set(i,i,Poem.metricWeights[i]);
			}
			//System.out.println();
			//weightsMatrix.set(wcIndex,wcIndex,weightsMatrix.get(wcIndex,wcIndex)/maxwc);
			//weightsMatrix.set(nlIndex,nlIndex,weightsMatrix.get(nlIndex,nlIndex)/maxnl);
			//weightsMatrix.set(avgllIndex,avgllIndex,weightsMatrix.get(avgllIndex,avgllIndex)/maxavgll);
			A = A.times(weightsMatrix); //A = A*W
			//System.out.println("maxwc/nl/avgll="+maxwc+"/"+maxnl+"/"+maxavgll);
			
			//DEBUGGING: finding good weights
			if (DEBUG_ON) {
				System.out.println("min/max/range foreach metric (x1000), "+((System.currentTimeMillis()-t1)/1000)+" seconds into method");
				//show names
				String metname ;
				for (int i=0;i<Poem.numMetrics;i++) {
					metname = Poem.metricNameLookup[i];
					System.out.print(metname.substring(0,Math.min(7,metname.length()))+" ");
					if (metname.length()<7) {
						for (int j=metname.length();j<7;j++) {
							System.out.print("*");
						}
					}
				}
				System.out.println();
				if (GUI.stopDoPCA) {
					GUI.stopDoPCA = false;
					GUI.doPCAInProcess = false;
					return;
				}
				double[][] minmax = new double[3][Poem.numMetrics];
				double[] tmpdarr ;
				for (int i=0;i<Poem.numMetrics;i++) {
					tmpdarr = A.getMatrix(0,A.getRowDimension()-1,i,i).getColumnPackedCopy();
					minmax[0][i] = GUI.findMin(tmpdarr);
					minmax[1][i] = GUI.findMax(tmpdarr);
					minmax[2][i] = minmax[1][i] - minmax[0][i] ; //diff
				}
				Matrix debugminmax = new Matrix(minmax);
				System.out.println(GUI.toString(debugminmax.times(1000)));
			}
			//END DEBUGGING
	
			if (poemCollection.size()<Poem.numMetrics) {
				//need A to be mxn where m>=n
				A = A.transpose();
			}
			if (DEBUG_ON && poemCollection.size()<100) {
				//memory profiling
				Runtime rt = Runtime.getRuntime();
				System.out.println("0before SVD, freeMemory="+rt.freeMemory()+"; maxMem="+rt.maxMemory()+"; totMem="+rt.totalMemory());
				rt.gc();
				System.out.println("0after GC, freeMemory="+rt.freeMemory()+"; maxMem="+rt.maxMemory()+"; totMem="+rt.totalMemory());

				//System.out.println("weighted 1000*A:\n"+GUI.toString(A.times(1000)));
				//System.out.println("weighted 1000*A: "+((System.currentTimeMillis()-t1)/1000)+" seconds into method");
			}

			//run SVD
			if (DEBUG_ON) {
				System.out.println("before SVD, "+((System.currentTimeMillis()-t1)/1000)+" seconds into method; row/col="+A.getRowDimension()+"/"+A.getColumnDimension());
			}
			if (GUI.stopDoPCA) {
				GUI.stopDoPCA = false;
				GUI.doPCAInProcess = false;
				return;
			}

			SingularValueDecomposition s = A.svd();

			if (GUI.stopDoPCA) {
				GUI.stopDoPCA = false;
				GUI.doPCAInProcess = false;
				return;
			}
			if (DEBUG_ON) {
				System.out.println("after SVD, "+((System.currentTimeMillis()-t1)/1000)+" seconds into method");
			}
			Matrix V = s.getV();
			//System.out.println("weighted 1000*V:\n"+GUI.toString(V.times(1000)));
			Matrix U = s.getU();
			//System.out.println("U:"+U.getRowDimension()+" x "+U.getColumnDimension());
			//System.out.println("weighted U:\n"+GUI.toString(U));
			//System.out.println("after SVD, "+((System.currentTimeMillis()-t1)/1000)+" seconds into method");
	
			//take first two (col?), turn into row vectors
			Matrix axis1, axis2, axesMatrix;
			if (poemCollection.size()<Poem.numMetrics) {
				axis1 = (U.getMatrix(0,Poem.numMetrics-1,0,0)).transpose(); //row0,row1,col0,col1
				if (poemCollection.size()==1) {
					axis2 = axis1;//otherwise array index error for 1's
				}
				else {
					axis2 = (U.getMatrix(0,Poem.numMetrics-1,1,1)).transpose(); //row0,row1,col0,col1
				}
			}
			else {
				axis1 = (V.getMatrix(0,Poem.numMetrics-1,0,0)).transpose(); //row0,row1,col0,col1
				axis2 = (V.getMatrix(0,Poem.numMetrics-1,1,1)).transpose(); //row0,row1,col0,col1
			}
			if (DEBUG_ON) {
				System.out.println("Axes=\n"+GUI.toString(axis1)+GUI.toString(axis2));
			}
			
			if (GUI.stopDoPCA) {
				GUI.stopDoPCA = false;
				GUI.doPCAInProcess = false;
				return;
			}

			double axis1Mag = Math.sqrt(axis1.times(axis1.transpose()).get(0,0));
			double axis2Mag = Math.sqrt(axis2.times(axis2.transpose()).get(0,0));
	
			if (poemCollection.size()<Poem.numMetrics) {
				//revert back for below, so each row is one poem
				A = A.transpose();
			}
	
			//to get new values--simply multiply axes matrix' x adjusteddatamatrix'
			axesMatrix = new Matrix(2,Poem.numMetrics);
			axesMatrix.setMatrix(0,0,0,Poem.numMetrics-1,axis1);
			axesMatrix.setMatrix(1,1,0,Poem.numMetrics-1,axis2);
			Matrix newXYMatrix = axesMatrix.times(A.transpose());
			for (int i=0;i<poemCollection.size() && !GUI.stopDoPCA;i++) {
				Poem poemPtr = (Poem)poemCollection.elementAt(i);
				//double coord1, coord2;
				poemPtr.setXPosRaw(newXYMatrix.get(0,i));
				poemPtr.setYPosRaw(newXYMatrix.get(1,i));
			}
			
			//System.out.println("before projection onto axes, "+((System.currentTimeMillis()-t1)/1000)+" seconds into method");
			//project onto new axes and set raw coords
			/*Matrix poemMetricValues;
			for (int i=0;i<poemCollection.size() && !GUI.stopDoPCA;i++) {
				Poem poemPtr = (Poem)poemCollection.elementAt(i);
				double coord1, coord2;
				//projection: A onto B = (dotP(A,B)/|B|)*(B/|B|)
				//"packed" array, one row; or Poem.numMetrics rows to get col vector
				//OLDMatrix poemMetricValues = new Matrix(poemPtr.metricValues,1);
				//needs to be weighted...duh
				poemMetricValues = A.getMatrix(i,i,0,Poem.numMetrics-1);
	
				Matrix proj1 = poemMetricValues.times(axis1.transpose()); //dotP
				proj1 = proj1.times(1/axis1Mag); //leaves A*cos(theta)
				coord1 = proj1.get(0,0);
				proj1 = axis1.times(proj1.get(0,0)/axis1Mag); //dotP(Axis,X)/|Axis|*(Axis/|Axis|)
	
				Matrix proj2 = poemMetricValues.times(axis2.transpose()); //dotP
				proj2 = proj2.times(1/axis2Mag); //leaves A*cos(theta)
				coord2 = proj2.get(0,0);
				proj2 = axis2.times(proj2.get(0,0)/axis2Mag); //dotP(Axis,X)/|Axis|*(Axis/|Axis|)
	
				//set xDraw and yDraw accordingly: |proj1|, |proj2|
				double xPosRaw = coord1; //Math.sqrt(proj1.times(proj1.transpose()).get(0,0));
				double yPosRaw = coord2; //Math.sqrt(proj2.times(proj2.transpose()).get(0,0));
				System.out.println("OLDx/OLDy|NEWx/NEWy="+poemPtr.getXPosRaw()+"/"+poemPtr.getYPosRaw()+"|"+xPosRaw+"/"+yPosRaw);
				poemPtr.setXPosRaw(xPosRaw);
				poemPtr.setYPosRaw(yPosRaw);
			}
			if (GUI.stopDoPCA) {
				GUI.stopDoPCA = false;
				GUI.doPCAInProcess = false;
				return;
			}*/
			if (DEBUG_ON) {
				System.out.println("end of PCA, "+((System.currentTimeMillis()-t1)/1000)+" seconds into method");
			}
			//System.out.println("Calculating stress");
		}

		GUI.doPCAInProcess = false;
		GUI.stopDoPCA = false;
		if (DEBUG_ON) {
			System.out.println("doPCA() finished.");
		}
		calculateStress();
	}
	
	/**
	 * For now, for "debugging"; calculate the "stress" of the 2D display (raw values), basically how inaccurate it is
	 * Also do second method: compare order of (sorted) real distances, display dist's
	 */
	private void calculateStress() {
		//don't do if option not checked by user
		if (!GUI.calculateStressOption) {
			return;
		}
		
		//wait for old one to end--there is a Thread waiting, so wait for it
		while (GUI.calculateStressInProcess) {
			if (DEBUG_ON) {
				System.out.println("sleeping at top of calculateStress()");
			}
			try {
				Thread.sleep(667);
			} catch(Exception e) {
				if (DEBUG_ON) {
					e.printStackTrace(System.out);
				}
			}
		}
		
		GUI.calculateStressInProcess = true ;
		GUI.calculateStressThread = new Thread(new Runnable() {
		public void run() {
			if (DEBUG_ON) {
				System.out.println("in calculateStressThread.run()");
			}
			//loop through all poem pairs, and sqrt sum squared differences of true distance and 2D dist, scaled by true dist^2
			double numerator=0, n2=0, denominator=0, dispDist, trueDist ;
			Poem A, B;
			//get maxs for scaling
			double trueMax=-1, dispMax=-1 ;
			long t1 = System.currentTimeMillis();
			Vector<Double> trueDistVector = new Vector<Double>();
			Vector<Double> dispDistVector = new Vector<Double>();
			//System.out.println("truedist   dispdist");
			for (int i=0;i<poemCollection.size() && !GUI.stopCalculateStress;i++) {
				for (int j=i+1;j<poemCollection.size();j++) {
					A = (Poem)poemCollection.elementAt(i);
					B = (Poem)poemCollection.elementAt(j);
					trueDist = (USE_LOG_DISTANCES) ? logNDDist(A.metricValues,B.metricValues,Poem.metricWeights) : nDDist(A.metricValues,B.metricValues,Poem.metricWeights) ;
					dispDist = Math.sqrt(Math.pow(A.getXPosRaw()-B.getXPosRaw(),2)+Math.pow(A.getYPosRaw()-B.getYPosRaw(),2));
					trueDistVector.add(new Double(trueDist));
					dispDistVector.add(new Double(dispDist));
					//System.out.println((Math.round(1000*trueDist))+"   "+(Math.round(1000*dispDist)));
					if (trueDist > trueMax) {
						trueMax = trueDist ;
					}
					if (dispDist > dispMax) {
						dispMax = dispDist ;
					}
				}
			}
			if (GUI.stopCalculateStress) {
				GUI.stopCalculateStress = false;
				GUI.calculateStressInProcess = false;
				return;
			}
			double scale = trueMax / dispMax ;
			long t2 = System.currentTimeMillis();
			if (DEBUG_ON) {
				System.out.println("calcStress getscale took "+(t2-t1)/1000+" seconds");
			}
			for (int i=0;i<poemCollection.size() && !GUI.stopCalculateStress;i++) {
				for (int j=i+1;j<poemCollection.size();j++) {
					A = (Poem)poemCollection.elementAt(i);
					B = (Poem)poemCollection.elementAt(j);
					dispDist = Math.sqrt(Math.pow(A.getXPosRaw()-B.getXPosRaw(),2)+Math.pow(A.getYPosRaw()-B.getYPosRaw(),2));
					trueDist =  (USE_LOG_DISTANCES) ? logNDDist(A.metricValues,B.metricValues,Poem.metricWeights) : nDDist(A.metricValues,B.metricValues,Poem.metricWeights) ;
					numerator += Math.pow( trueDist - scale*dispDist ,2);
					n2 += Math.pow( trueDist - dispDist ,2);
					denominator += Math.pow(trueDist,2);
					if (DEBUG_ON) {
						if (i==0 && j<10) {
							System.out.println(""+trueDist+" disp as "+(scale*dispDist)+" (orig: "+dispDist+")");
						}
					}
				}
			}
			if (GUI.stopCalculateStress) {
				GUI.stopCalculateStress = false;
				GUI.calculateStressInProcess = false;
				return;
			}
			GUI.displayStress = Math.sqrt(numerator / denominator);
			if (DEBUG_ON) {
				System.out.println("Stress = "+GUI.displayStress+" (unscaled="+Math.sqrt(n2/denominator)+"); took "+(System.currentTimeMillis()-t2)/1000+" seconds (since getscale)"); 
			}
			long t3 = System.currentTimeMillis();
			
			//calculate second method
			if (trueDistVector.size()<100) {
				Vector<Double> trueDistSorted=new Vector<Double>(trueDistVector), dispDistSorted = new Vector<Double>(dispDistVector);
				double[] trueDistArrIndices=new double[trueDistVector.size()], dispDistArrIndices=new double[dispDistVector.size()];
				/*for (int i=0;i<trueDistArr.length;i++) {
					trueDistArr[i] = ((Double)trueDistVector.elementAt(i)).doubleValue();
					dispDistArr[i] = ((Double)dispDistVector.elementAt(i)).doubleValue();
				}*/
				//sort, and get sorted INDEX matrices
				//Arrays.sort(trueDistArr);
				//Arrays.sort(dispDistArr);
				Collections.sort(trueDistSorted);
				Collections.sort(dispDistSorted);
				double[] trueDistSortedArr = new double[trueDistSorted.size()], dispDistSortedArr=new double[dispDistSorted.size()];
				for (int i=0;i<trueDistSortedArr.length && !GUI.stopCalculateStress;i++) {
					trueDistSortedArr[i] = ((Double)trueDistSorted.elementAt(i)).doubleValue();
					dispDistSortedArr[i] = ((Double)dispDistSorted.elementAt(i)).doubleValue();
				}
				if (GUI.stopCalculateStress) {
					GUI.stopCalculateStress = false;
					GUI.calculateStressInProcess = false;
					return;
				}
				if (DEBUG_ON) {
					System.out.println("sorted in calcstress ("+(System.currentTimeMillis()-t3)/1000+"sec since Stress =)");
				}
				double curTrueVal=-2, curDispVal=-2, prevTrueVal=-1, prevDispVal=-1;
				int prevTrueInd=0, prevDispInd=0, tmpInd;
				for (int i=0;i<trueDistVector.size() && !GUI.stopCalculateStress;i++) {
					if (DEBUG_ON) {
						System.out.print(";"+i);
					}
					//give encouragement...
					if (i % trueDistVector.size()/10 == 0) {
						System.out.println(10*i/trueDistVector.size()+"% done calculating stress");
					}
					curTrueVal = trueDistSortedArr[i];
					curDispVal = dispDistSortedArr[i];
					
					//System.out.println("vals:"+curTrueVal+";"+curDispVal);
					//true
					if (curTrueVal==prevTrueVal) {
						tmpInd = trueDistVector.indexOf(new Double(curTrueVal),prevTrueInd);
					}
					else {
						tmpInd = trueDistVector.indexOf(new Double(curTrueVal));
					}
					trueDistArrIndices[tmpInd] = i;
					prevTrueInd = tmpInd + 1;
					prevTrueVal = curTrueVal ;
		
					//disp
					if (curDispVal==prevDispVal) {
						tmpInd = dispDistVector.indexOf(new Double(curDispVal),prevDispInd);
					}
					else {
						tmpInd = dispDistVector.indexOf(new Double(curDispVal));
					}
					dispDistArrIndices[tmpInd] = i;
					prevDispInd = tmpInd + 1;
					prevDispVal = curDispVal ;
				}
				if (GUI.stopCalculateStress) {
					GUI.stopCalculateStress = false;
					GUI.calculateStressInProcess = false;
					return;
				}
				if (DEBUG_ON) {
					System.out.println("initd ("+(System.currentTimeMillis()-t3)/1000+"sec since Stress =)");
				}
				//compare: take abs diff
				double sum = 0;
				for (int i=0;i<trueDistArrIndices.length && !GUI.stopCalculateStress;i++) {
					sum += Math.abs(trueDistArrIndices[i] - dispDistArrIndices[i]);
				}
				if (GUI.stopCalculateStress) {
					GUI.stopCalculateStress = false;
					GUI.calculateStressInProcess = false;
					return;
				}
				//scale by length*length/2
				if (DEBUG_ON) {
					System.out.print("2ndary stress: "+sum+" (raw); ");
				}
				sum /= trueDistArrIndices.length*trueDistArrIndices.length/2;
				if (DEBUG_ON) {
					System.out.println(sum+" (scaled) ("+(System.currentTimeMillis()-t3)/1000+"sec since Stress =)");
				}
			}
			
			GUI.calculateStressInProcess = false;
			GUI.stopCalculateStress = false;
			if (DEBUG_ON) {
				System.out.println("calculateStress() finished.");
			}
			dPane.repaint();
		}}, "calculateStressThread");
		GUI.calculateStressThread.start();
	}
	
	/**
	 * Calculate n-dimensional Euclidean distance
	 * sqrt((x1-y1)^2+...+(xn-yn)^2)
	 * assumes equal lengths (else, IndexOutOfBoundsException)
	 */
	public static double nDDist(double[] a, double[] b) {
		double ret = 0 ;
		for (int i=0;i<a.length;i++) {
			ret += Math.pow(a[i] - b[i], 2);
		}
		return Math.sqrt(ret);
	}
	public static double nDDist(double[] a, double[] b, double[] weights) {
		double ret = 0 ;
		for (int i=0;i<a.length;i++) {
			ret += Math.pow(weights[i]*(a[i] - b[i]), 2);
		}
		return Math.sqrt(ret);
	}
	public static double logNDDist(double[] a, double[] b, double[] weights) {
		double ret = 0 ;
		double offset1 = 0.002; //else log(0)-->-inf
		double offset2 = Math.log(offset1); //"best" distance is 0 (otherwise, -1^2=1>0^2)
		for (int i=0;i<a.length;i++) {
			ret += Math.log(Math.pow(weights[i]*(a[i] - b[i]), 2) + offset1) - offset2 ;
		}
		return Math.sqrt(ret);
	}

	/**
	 * Display a Matrix as a String
	 */
	protected static String toString(Matrix A) {
		String ret = new String("");
		String tmp, suffix;
		double dTmp;
		int tmpInd;
		for (int i=0;i<A.getRowDimension();i++) {
			for (int j=0;j<A.getColumnDimension();j++) {
				tmp = "";
				dTmp = A.get(i,j);
				tmp = Double.toString(dTmp);
				if (tmp.indexOf("E")>=0) { //String would be scientific notation
					suffix = tmp.substring(tmp.indexOf("E"));
					tmp = tmp.substring(0,tmp.indexOf("E"));
					tmpInd = tmp.indexOf(".");
					tmpInd = (tmpInd<0)?tmp.length():tmpInd;
					tmp = tmp.substring(0,tmpInd);
					tmp = tmp+suffix;
				}
				else {
					tmp = tmp.substring(0,Math.min(tmp.indexOf(".")+4,tmp.length()));
				}
				while (tmp.length() < 8) {
					tmp += "*";
				}
				ret += tmp;
			}
			ret += "\n";
		}
		return ret;
	}

	/**
	 * Scale poems to fit on dPane properly.
	 * Parameter>0 means axes are NORMAL;
	 * ==0 means put more variation on x-axis (better fit)
	 * <0 means they are FLIPPED
	 */
	public void scalePoems(int flipParam) {
		if (poemCollection.size()<=0) {
			return;
		}
		double xMin, xMax, yMin, yMax, dTmp; //keep track
		Poem poemPtr = (Poem)poemCollection.elementAt(0);
		xMin = poemPtr.getXPosRaw();
		xMax = poemPtr.getXPosRaw();
		yMin = poemPtr.getYPosRaw();
		yMax = poemPtr.getYPosRaw();
		for (int i=1;i<poemCollection.size();i++) {
			poemPtr = (Poem)poemCollection.elementAt(i);
			dTmp = poemPtr.getXPosRaw();
			xMin = Math.min(dTmp,xMin);
			xMax = Math.max(dTmp,xMax);
			dTmp = poemPtr.getYPosRaw();
			yMin = Math.min(dTmp,yMin);
			yMax = Math.max(dTmp,yMax);
		}
		
		//want more variation on x-axis (more natural)
		//could flip in the middle of changing weights=bad?
		//COULD set ONCE--if not set, set, else do nothing
		if (flipParam<0 || (flipParam==0 && xMax-xMin<yMax-yMin)) {
			Poem.flipAxes = true;
			double tmp1 = xMax ;
			double tmp2 = xMin ;
			xMax = yMax;
			xMin = yMin;
			yMax = tmp1;
			yMin = tmp2;
		}
		else {
			Poem.flipAxes = false;
		}
		
		dTmp = xMax - xMin;
		Poem.xScale = (dTmp==0) ? Double.POSITIVE_INFINITY : (double)(GUI.screenWidth - Poem.drawWidth - 10)/dTmp;
		dTmp = yMax - yMin;
		Poem.yScale = (dTmp==0) ? Double.POSITIVE_INFINITY : (double)(GUI.screenHeight - Poem.drawHeight - 10)/dTmp;
		//Need xScale==yScale to preserve relative distances on screen
		int xSgn = (Poem.xScale < 0) ? -1 : 1 ;
		int ySgn = (Poem.yScale < 0) ? -1 : 1 ;
		//System.out.println("xScale="+Poem.xScale+";yScale="+Poem.yScale+";xSgn="+xSgn);
		Poem.xScale = Math.min(Math.abs(Poem.xScale),Math.abs(Poem.yScale));
		Poem.yScale = Poem.xScale;
		//System.out.println("GUI.sw="+GUI.screenWidth);
		Poem.xScale = Poem.xScale * xSgn;
		Poem.yScale = Poem.yScale * ySgn;
		//5 pixel buffer on L & R, top/bottom; account for + and - Scale values
		Poem.xAdd = 5 - Math.min(xMin*Poem.xScale,xMax*Poem.xScale); 
		Poem.yAdd = 5 - Math.min(yMin*Poem.yScale,yMax*Poem.yScale);
		if (DEBUG_ON) {
			System.out.println("xScale="+Poem.xScale+";yScale="+Poem.yScale+";xAdd="+Poem.xAdd+";yAdd="+Poem.yAdd);
		}
	}

	/**
	 * Calculate TRUE distances to the selected poem from all others
	 * Implement as thread to run in background?
	 */
	public static void updateDistancesFromSelected() {
		long t1 = System.currentTimeMillis();
		Poem pSel = GUI.selectedPoem, p = null;
		if (pSel==null) {
			return;
		}
		selectedDistancesFrame.setTitle("True distances from "+pSel.getTitle()+" by "+pSel.getAuthor());
		//clear old one
		selectedTotalDistancesVector.clear();
		for (int i=0;i<selectedDistancesListModels.length;i++) {
			selectedDistancesListModels[i].clear();
		}
		//add titles back in
		selectedDistancesListModels[0].addElement("title/poet");
		selectedDistancesListModels[1].addElement("Total dist");
		for (int i=0;i<Poem.metricNameLookup.length;i++) {
			selectedDistancesListModels[i+2].addElement(Poem.metricNameLookup[i]);
		}
		double dist;
		Iterator iter = GUI.poemCollection.iterator();
		int left, right, ind;
		while (iter.hasNext()) {
			p = (Poem)iter.next();
			//calculate distance using metricValues, weights
			dist = (USE_LOG_DISTANCES) ? logNDDist(pSel.metricValues, p.metricValues, Poem.metricWeights) : nDDist(pSel.metricValues, p.metricValues, Poem.metricWeights);
			dist = Math.round(dist*Math.pow(10,GUI.selectedDistancePrecision)); //whatever precision (repeated elsewhere)
			//Double distDouble = new Double(dist);
			//binary search for index
			left = 0;
			right = selectedTotalDistancesVector.size();
			while (left<right) {
				ind = (left+right)/2;
				if (((Double)selectedTotalDistancesVector.elementAt(ind)).doubleValue() < dist ) {
					left = ind+1;
				}
				else {
					right = ind;
				}
			}
			ind = left + 1 ; //(+1 for header) and left==right
			selectedTotalDistancesVector.add(ind-1,new Double(dist));
			selectedDistancesListModels[0].add(ind,new String(p.getTitle()+"/"+p.getAuthor()));
			String distStr = new Double(dist/Math.pow(10,GUI.selectedDistancePrecision/2)).toString();
			selectedDistancesListModels[1].add(ind,distStr.substring(0,Math.min(distStr.length(),GUI.selectedDistancePrecision)));
			//calculate individual metric diffs
			for (int i=0;i<Poem.numMetrics;i++) {
				//calculate and add
				distStr = new Double(Poem.metricWeights[i]*Math.round(Math.pow(10,GUI.selectedDistancePrecision/2)*(p.metricValues[i]-pSel.metricValues[i]))).toString();
				selectedDistancesListModels[i+2].add(ind,distStr.substring(0,Math.min(distStr.length(),GUI.selectedDistancePrecision)));
			}
		}
		selectedDistancesFrame.pack();
		selectedDistancesFrame.setSize((int)Math.min(900,selectedDistancesFrame.getPreferredSize().getWidth()),(int)Math.min(700,selectedDistancesFrame.getPreferredSize().getHeight()));
		if (DEBUG_ON) {
			System.out.println("updateDistancesFromSelected() took "+(System.currentTimeMillis()-t1)/1000+" seconds");
		}
	}
	/**
	 * Updates colors and such
	 */
	public void updatePainting() {
		Poem p = null;
		ListIterator li = poemCollection.listIterator();
		//iterate through poems, call Poem.draw
		Poem.clearColors();
		while (li.hasNext()) {
			p = (Poem)li.next();
			p.updateScale();
			p.updateObjFont();
			//old: IF on screen, setBG, increment colors (implicit)
			//new - update even if off screen, otherwise would have to update every scroll event
			//if (p.getXDraw()<GUI.screenWidth && p.getYDraw()<GUI.screenHeight && p.getXDraw()>=0 && p.getYDraw()>=0) {
			p.calculateAndSetColor();
		}
	
		//update for JScrollPane
		if (DEBUG_ON) {
			System.out.print("updatePainting: updating dPane pSz from "+dPane.getPreferredSize()+" to ");
		}
		
		this.dPane.setPreferredSize(new Dimension(
			(int)Math.round(GUI.screenWidth*Poem.zoomFactor),
			(int)Math.round(GUI.screenHeight*Poem.zoomFactor)
			));

		if (DEBUG_ON) {
			System.out.println(dPane.getPreferredSize());
		}
		//made smaller-->resize smaller
		//need to add in INSET SIZE??
		if (DEBUG_ON && (dPaneScrollPane.getViewport().getViewSize().getWidth()<GUI.screenWidth || dPaneScrollPane.getViewport().getViewSize().getHeight()<GUI.screenHeight)) {
			System.out.println("viewSize = "+dPaneScrollPane.getViewport().getViewSize()+";GUI="+GUI.screenWidth+"x"+GUI.screenHeight);
		}

		this.dPane.revalidate();
	}

	/**
	 * Update weights to reflect (changed) value of updateSlider. Use null to update ALL weights
	 */
	public void updateWeights(JSlider updateSlider) {
		if (DEBUG_ON) {
			System.out.println("top of updateWeights()");
		}
		//STOP old doPCA Thread if it's running
		if (GUI.doPCAInProcess && !stopDoPCA) { //it's running AND not trying to be stopped
			//NOTWORKINGGUI.calculateStressThread.interrupt();
			stopDoPCA = true; //checked in loops in run()
			/*new Thread(new Runnable() {
				public void run() {
					while (GUI.stopDoPCA && GUI.doPCAInProcess) {
						System.out.println("Sleeping in Thread(2) at top of doPCA()");
						try {
							Thread.sleep(333);
						} catch(Exception e) {
							e.printStackTrace(System.out);
						}
					}
					GUI.doPCAInProcess = false;
				}
			}).start();
			*/ //why...(see top of doPCA)
		}

		if (updateSlider==null) {
			Component[] cArr = weightsPanel.getComponents();
			//update weights
			for (int i=0;i<cArr.length;i++) {
				if (cArr[i] instanceof JSlider) {
					JSlider js = (JSlider)cArr[i];
					String metricName = js.getToolTipText();
					Poem.metricWeights[Poem.getMetricIndex(metricName)] = (double)js.getValue()/(double)js.getMaximum();
				}
			}
		}
		else {
			String metricName = updateSlider.getToolTipText();
			Poem.metricWeights[Poem.getMetricIndex(metricName)] = (double)updateSlider.getValue()/(double)updateSlider.getMaximum();
		}

		//also update selectedDistances numbers
		GUI.updateDistancesFromSelected();

		try {
			endDoPCA();
			doPCAThread = new Thread(new Runnable() {
				public void run() {
					doPCA(); //recalculate axes
					scalePoems(0); //rescale
					updatePainting(); //effect changes
					repaint(); //redraw
				}
			}, "doPCAThread");
			doPCAThread.start();
		} catch(Exception e2) {
			JOptionPane.showMessageDialog(null, 
					"Error performing "+visAlg+"; see standard output for details.", 
					"Error", JOptionPane.ERROR_MESSAGE);
			System.out.println("Error performing "+visAlg);
			e2.printStackTrace(System.out);
		}
	}

	/**
	 * Clears all poems and poem objects from memory
	 */
	public void clearAll() {
		//dPane.removeAll();
		Poem p = null;
		ListIterator li = poemCollection.listIterator();
		while (li.hasNext()) {
			p = (Poem)li.next();
			dPane.remove(p);
		}		

		poemCollection.clear();

		JCheckBoxMenuItem jcb = null ;
		li = poetMenuItems.listIterator();
		while (li.hasNext()) {
			jcb = (JCheckBoxMenuItem)li.next();
			poetSubMenu.remove(jcb);
		}		
		poetMenuItems.clear();

		//clear bottom details pane
		GUI.detailsPane.updatePoem(null);
		Poem.colorTable.clear() ;
		detailsPane.updateLegend();
		//that's all for now
		repaint();
	}
	
	/**
	 * To sync positions of left side of split pane and right side for viewing true distances
	 */
	public void adjustmentValueChanged(AdjustmentEvent e) {
		//scrollbar
		if (e.getAdjustable() instanceof JScrollBar) {
			JScrollBar jsb = (JScrollBar)e.getAdjustable();
			int pos = e.getValue();
			GUI.selectedDistancesJSP1.getVerticalScrollBar().setValue(pos); //auto-update JViewport?
		}
	}

	/**
	 * Handles menu selections except view full boxes on/off
	 */
	public void actionPerformed(ActionEvent ae) {
		Object src = ae.getSource();
		if (src == openPoemsMenuItem) {
			userFileChooser.setDialogTitle("Open poem file(s)");
			int returnVal = userFileChooser.showOpenDialog(this);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File[] fileArr = userFileChooser.getSelectedFiles();
				for (int i=0;i<fileArr.length;i++) {
					loadUserFile(fileArr[i]);
				}
				//PCA
				try {
					endDoPCA();
					Poem.resetZoom();
					updatePainting();
					if (DEBUG_ON) {
						System.out.println("zoom="+Poem.zoomFactor);
					}
					GUI.doPCAThread = new Thread(new Runnable() {
						public void run() {
							doPCA();
							scalePoems(0);
							updatePainting();
							detailsPane.updateLegend();
							dPane.repaint();
						}
					}, "doPCAThread");
					GUI.doPCAThread.start();
				} catch(Exception e) {
					JOptionPane.showMessageDialog(null, 
							"Error performing "+visAlg+"; see standard output for details.", 
							"Error", JOptionPane.ERROR_MESSAGE);
					System.out.println("Error performing "+visAlg);
					e.printStackTrace(System.out);
				}
			}
		}
		else if (src == openObjsMenuItem) {
			userFileChooser.setDialogTitle("Open poem object file(s)");
			int returnVal = userFileChooser.showOpenDialog(this);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File[] fileArr = userFileChooser.getSelectedFiles();
				for (int i=0;i<fileArr.length;i++) {
					loadPoemObjs(fileArr[i]);
				}
				//PCA
				try {
					endDoPCA();
					Poem.resetZoom();
					updatePainting();
					doPCAThread = new Thread(new Runnable() {
						public void run() {
							doPCA();
							scalePoems(0);
							updatePainting();
							detailsPane.updateLegend();
							dPane.repaint();
						}
					}, "doPCAThread");
					doPCAThread.start();
				} catch(Exception e) {
					JOptionPane.showMessageDialog(null, 
							"Error performing "+visAlg+"; see standard output for details.", 
							"Error", JOptionPane.ERROR_MESSAGE); 
					System.out.println("Error performing "+visAlg);
					e.printStackTrace(System.out);
				}
			}
		}
		else if (src == openWeightsMenuItem) {
			userFileChooser.setDialogTitle("Open weights file");
			int returnVal = userFileChooser.showOpenDialog(this);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File[] fileArr = userFileChooser.getSelectedFiles();
				if (fileArr!=null && fileArr.length>0) {
					loadWeightsFile(fileArr[0]);
				}
				//PCA
				try {
					endDoPCA();
					doPCAThread = new Thread(new Runnable() {
						public void run() {
							doPCA();
							scalePoems(0);
							updatePainting();
							detailsPane.updateLegend();
							dPane.repaint();
						}
					}, "doPCAThread");
					doPCAThread.start();
				} catch(Exception e) {
					JOptionPane.showMessageDialog(null, 
							"Error performing "+visAlg+"; see standard output for details.", 
							"Error", JOptionPane.ERROR_MESSAGE); 
					System.out.println("Error performing "+visAlg);
					e.printStackTrace(System.out);
				}
			}
		}
		else if (src == clearMenuItem) {
			this.clearAll();
			Poem.resetZoom();
			updatePainting();
		}
		else if (src == saveMenuItem) {
			userFileChooser.setDialogTitle("Save to object file");
			int returnVal = userFileChooser.showSaveDialog(this);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File file = userFileChooser.getSelectedFile();
				try {
					saveFile(file);
				} catch(Exception ex) {
					System.out.println("Error saving file:");
					ex.printStackTrace(System.out);
				}
			}
		}
		else if (src == saveWeightsMenuItem) {
			userFileChooser.setDialogTitle("Save weights to file");
			int returnVal = userFileChooser.showSaveDialog(this);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File file = userFileChooser.getSelectedFile();
				try {
					saveWeightsFile(file);
				} catch(Exception ex) {
					System.out.println("Error saving weights file:");
					ex.printStackTrace(System.out);
				}
			}
		}
		else if (src == outputDistancesMenuItem) {
			if (GUI.diffPoetDistances==null || GUI.samePoetDistances==null || GUI.poetNameLookup==null) {
				//error box
				JOptionPane.showMessageDialog(null, 
					"CMDS must be used to output distances (not PCA)", 
					"Error", JOptionPane.ERROR_MESSAGE);
			}
			else {
				userFileChooser.setDialogTitle("Output distances to file");
				int returnVal = userFileChooser.showSaveDialog(this);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					File file = userFileChooser.getSelectedFile();
					try {
						outputDistancesFile(file);
					} catch(Exception ex) {
						System.out.println("Error saving distances file:");
						ex.printStackTrace(System.out);
					}
				}
			}
		}
		else if (src == outputValuesMenuItem) {
			userFileChooser.setDialogTitle("Output values to file");
			int returnVal = userFileChooser.showSaveDialog(this);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File file = userFileChooser.getSelectedFile();
				try {
					outputValuesFile(file);
				} catch(Exception ex) {
					System.out.println("Error saving values file:");
					ex.printStackTrace(System.out);
				}
			}
		}
		else if (src instanceof JRadioButtonMenuItem) { //font size or zoom or vis algorithm
			ButtonModel bm = ((JRadioButtonMenuItem)src).getModel();
			if (fontButtons.isSelected(bm)) { //font
				String size = bm.getActionCommand();
				try {
					int fsize = Integer.parseInt(size);
					Poem.fontSize = fsize;
					Poem.updateFont();
					updatePainting();
					repaint();
				} catch(Exception e) {
					e.printStackTrace(System.out);
				}
			}
			else if (zoomButtons.isSelected(bm)) { //zoom
				String zoom = bm.getActionCommand();
				try {
					double zoomFactor = Double.parseDouble(zoom);
					Poem.zoomFactor = zoomFactor;
					//System.out.println("zoomFactor changed to "+Poem.zoomFactor);
					updatePainting();
					//System.out.println("Painting update'd");
					repaint();
					//System.out.println("repaint'd");
				} catch(Exception e) {
					e.printStackTrace(System.out);
				}
			}
			else if (visualizationAlgorithmButtons.isSelected(bm)) {
				String alg = bm.getActionCommand();
				if (alg.equals("PCA")) {
					visAlg = "PCA";
				}
				else if (alg.equals("CMDS")) {
					visAlg = "CMDS";
				}
				else {
					System.out.println("Unknown visualization algorithm selected: "+alg);
				}
				try {
					endDoPCA();
					doPCAThread = new Thread(new Runnable() {
						public void run() {
							doPCA();
							scalePoems(0);
							updatePainting();
							repaint();
						}
					}, "doPCAThread");
					doPCAThread.start();
				} catch(Exception e) {
					JOptionPane.showMessageDialog(null, 
							"Error performing "+visAlg+"; see standard output for details.", 
							"Error", JOptionPane.ERROR_MESSAGE);
					System.out.println("Error performing "+visAlg);
					e.printStackTrace(System.out);
				}
			}
			else {
				if (DEBUG_ON) {
					System.out.println("Not selected radio button menu item?");
				}
			}
		}
		else if (src == zoomMoreMenuItem) {
			Poem.zoomFactor++;
			//System.out.println("zoomFactor changed to "+Poem.zoomFactor);
			updatePainting();
			repaint();
		}
		else if (src == zoomLessMenuItem) {
			Poem.zoomFactor--;
			Poem.zoomFactor = Math.max(1,Poem.zoomFactor); //since 1 shows all on screen
			//System.out.println("zoomFactor changed to "+Poem.zoomFactor);
			updatePainting();
			repaint();
		}
		else if (src == flipAxesMenuItem) {
			if (Poem.flipAxes) {
				scalePoems(1); //NOT flipped axes now
			}
			else {
				scalePoems(-1); //FLIPPED axes now
			}
			updatePainting(); //calls p.updateScale()
			repaint();
		}
		else if (src == viewNoPoetsMenuItem) {
			setAllPoemsVisible( false );
		}
		else if (src == viewAllPoetsMenuItem) {
			setAllPoemsVisible( true );
		}
		else if (src == viewSelectedDistancesMenuItem) {
			selectedDistancesFrame.setVisible(true);
			selectedDistancesFrame.requestFocus();
		}
		else if (src instanceof JCheckBoxMenuItem && poetMenuItems.contains((JCheckBoxMenuItem)src)) { //need to cast src?
			//checkbox. Find if selecting or deselecting; change; update; repaint
			String poet = ((JCheckBoxMenuItem)src).getText();
			//System.out.println("poetViewEvent"+poet);
			Poem p = null ;
			Iterator iter = poemCollection.iterator();
			while (iter.hasNext()) {
				p = (Poem)iter.next();
				if (p!=null && p.getAuthor()!=null && p.getAuthor().equals(poet)) {
					p.setVisible( ((JCheckBoxMenuItem)src).getState() );
				}
			}
		}
		else if (src == helpMenuItem) {
			//pop-up help window
			HelpWindow hw = new HelpWindow(); //does showing & everything
		}
		else if (src == aboutMenuItem) {
			JFrame jf = new JFrame("About");
			JTextArea jta = new JTextArea(0,50);
			jta.setText("Copyright (c) 2006, David M Kaplan.\nDeveloped for undergraduate Senior Thesis work in Computer Science at Princeton University.");
			jta.setEditable(false);
			jf.add(jta);
			jf.pack();
			jf.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			jf.setVisible(true);
		}
		else if (src == clearWeightsButton) {
			if (autoUpdateWeights) {
				if (GUI.doPCAInProcess && !GUI.stopDoPCA) { //it's running AND not trying to be stopped
					//stop prev thread, start new one
					//NOTWORKINGGUI.calculateStressThread.interrupt();
					stopDoPCA = true; //checked in loops in run()
				}
			}

			Component[] cArr = weightsPanel.getComponents();
			boolean oldAutoUpdate = GUI.autoUpdateWeights;
			GUI.autoUpdateWeights = false;
			for (int i=0;i<cArr.length;i++) {
				if (cArr[i] instanceof JSlider) {
					JSlider js = (JSlider)cArr[i];
					js.setValue(js.getMinimum());
					String metricName = js.getToolTipText();
					Poem.metricWeights[Poem.getMetricIndex(metricName)] = (double)js.getValue()/(double)js.getMaximum();
				}
			}
			
			GUI.autoUpdateWeights = oldAutoUpdate;
			if (autoUpdateWeights) {
				GUI.updateDistancesFromSelected();
				try {
					endDoPCA();
					doPCAThread = new Thread(new Runnable() {
						public void run() {
							doPCA(); //recalculate axes
							scalePoems(0); //rescale
							updatePainting(); //effect changes
							repaint(); //redraw
						}
					}, "doPCAThread");
					doPCAThread.start();
				} catch(Exception e) {
					JOptionPane.showMessageDialog(null, 
							"Error performing "+visAlg+"; see standard output for details.", 
							"Error", JOptionPane.ERROR_MESSAGE); 
					System.out.println("Error performing "+visAlg);
					e.printStackTrace(System.out);
				}
			}
		}
		else if (src == fullWeightsButton) {
			if (autoUpdateWeights) {
				if (GUI.doPCAInProcess && !GUI.stopDoPCA) { //it's running AND not trying to be stopped
					//stop prev thread, start new one
					//NOTWORKINGGUI.calculateStressThread.interrupt();
					stopDoPCA = true; //checked in loops in run()
				}
			}

			Component[] cArr = weightsPanel.getComponents();
			boolean oldAutoUpdate = GUI.autoUpdateWeights;
			GUI.autoUpdateWeights = false;
			for (int i=0;i<cArr.length;i++) {
				if (cArr[i] instanceof JSlider) {
					JSlider js = (JSlider)cArr[i];
					js.setValue(js.getMaximum());
					String metricName = js.getToolTipText();
					Poem.metricWeights[Poem.getMetricIndex(metricName)] = (double)js.getValue()/(double)js.getMaximum();
				}
			}
			
			GUI.autoUpdateWeights = oldAutoUpdate;
			if (autoUpdateWeights) {
				GUI.updateDistancesFromSelected();
				try {
					endDoPCA();
					doPCAThread = new Thread(new Runnable() {
						public void run() {
							doPCA(); //recalculate axes
							scalePoems(0); //rescale
							updatePainting(); //effect changes
							repaint(); //redraw
						}
					}, "doPCAThread");
					doPCAThread.start();
				} catch(Exception e) {
					JOptionPane.showMessageDialog(null, 
							"Error performing "+visAlg+"; see standard output for details.", 
							"Error", JOptionPane.ERROR_MESSAGE); 
					System.out.println("Error performing "+visAlg);
					e.printStackTrace(System.out);
				}
			}
		}
		else if (src == updateWeightsButton) {
			updateWeights(null);
			GUI.updateDistancesFromSelected();
		}
		else if (src == autoUpdateWeightsCheckBox) {
			GUI.autoUpdateWeights = !GUI.autoUpdateWeights;
			updateWeightsButton.setEnabled(!GUI.autoUpdateWeights);
		}
		else if (src instanceof JButton) {
			String cmd = ((JButton)src).getActionCommand();
			//delete from distances frame
			if (cmd.equals("deleteDistance")) {
				int index = distancesList.getSelectedIndex();
				distancesListModel.remove(index);
				if (distancesListModel.getSize()==0) {
					//hide window?
					//do nothing
				}
				else {
					if (index==distancesListModel.getSize()) {
						index--; //removed last item
					}
					distancesList.setSelectedIndex(index);
					distancesList.ensureIndexIsVisible(index);
				}
			}
			else if (cmd.equals("deleteAllDistances")) {
				distancesListModel.clear();
			}
		}
	}
	
	private void setAllPoemsVisible(boolean b) {
		Iterator iter = poetMenuItems.iterator();
		JCheckBoxMenuItem jcb ;
		while (iter.hasNext()) {
			jcb = (JCheckBoxMenuItem)iter.next();
			jcb.setState( b );
		}

		//update all
		iter = poemCollection.iterator();
		Poem p ;
		while (iter.hasNext()) {
			p = (Poem)iter.next();
			p.setVisible( b );
		}
	}
	
	/**
	 * Handles view menu's 'view full boxes' on/off
	 */
	public void stateChanged(ChangeEvent e) {
		Object src = e.getSource();
		if (src==viewBoxesMenuItem && Poem.viewBoxes!=viewBoxesMenuItem.getState()) {
			//System.out.println("ViewBoxesEvent switch");
			Poem.viewBoxes = viewBoxesMenuItem.getState();
			//NEW WAY need to iterate
			ListIterator li = poemCollection.listIterator();
			//iterate through poems, set on/off
			Poem p = null;
			if (Poem.viewBoxes) {
				while (li.hasNext()) {
					p = (Poem)li.next();
					p.setTextAndToolTip();
				}
			}
			else { //false
				while (li.hasNext()) {
					p = (Poem)li.next();
					if (p.getText().length() > 0) {
						p.toggleTextView();
					}
				}
			}
			updatePainting();
			repaint();
		}
		else if (src==blackBackgroundMenuItem) {
			if (blackBackgroundMenuItem.getState()) {
				dPane.setBackground(Color.black);
				detailsPane.jpRight.setBackground(Color.black);
			}
			else {
				dPane.setBackground(Color.white);
				detailsPane.jpRight.setBackground(Color.white);
			}
		}
		else if (src==taggedTextMenuItem) {
			/*if (DEBUG_ON) {
				System.out.println("ttmi.gs():"+taggedTextMenuItem.getState());
			}*/
			if (taggedTextMenuItem.getState() == Poem.viewTaggedText) {
				return;
			}
			if (taggedTextMenuItem.getState()) {
				//enable button...wait for add text until next processing
				GUI.detailsPane.taggedTextButton.setEnabled(true);
				Poem.viewTaggedText = true;
			}
			else {
				GUI.detailsPane.taggedTextButton.setEnabled(false);
				Poem.viewTaggedText = false;
			}
		}
		else if (src==calculateStressMenuItem) {
			GUI.calculateStressOption = calculateStressMenuItem.getState();
			if (!GUI.calculateStressOption && GUI.calculateStressInProcess) {
				//stop it
			}
		}
		else if (src==logDistancesMenuItem) {
			boolean oldB = GUI.USE_LOG_DISTANCES;
			GUI.USE_LOG_DISTANCES = logDistancesMenuItem.getState();
			if (oldB==GUI.USE_LOG_DISTANCES) {
				return;
			}
			
			//also update selectedDistances numbers
			GUI.updateDistancesFromSelected();
			try {
				endDoPCA();
				doPCAThread = new Thread(new Runnable() {
					public void run() {
						doPCA(); //recalculate axes
						scalePoems(0); //rescale
						updatePainting(); //effect changes
						repaint(); //redraw
					}
				}, "doPCAThread");
				doPCAThread.start();
			} catch(Exception e2) {
				JOptionPane.showMessageDialog(null, 
						"Error performing "+visAlg+"; see standard output for details.", 
						"Error", JOptionPane.ERROR_MESSAGE); 
				System.out.println("Error performing "+visAlg);
				e2.printStackTrace(System.out);
			}
		}
		else if (src instanceof JSlider) { //weights
			if (GUI.autoUpdateWeights) {
				JSlider js = (JSlider)src;
				if (DEBUG_ON) {
					System.out.println("pre-updateWeights() in stateChanged");
				}
				updateWeights(js);
			}
		}
	}
	
	/**
	 * MouseListener methods
	 */
	public void mouseClicked(MouseEvent e) {
		Poem thisP = (Poem)e.getSource();
		//Display details or something
		if ( (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) == InputEvent.CTRL_DOWN_MASK) {
			GUI.selectedPoem = thisP;
			GUI.detailsPane.updatePoem(GUI.selectedPoem);
			//calculate distances (verbose) to all other poems in GUI.poemCollection
			//make this a thread??
			GUI.updateDistancesFromSelected();
			//revalidate stuff for scrollpane
			selectedDistancesSplitPane.revalidate();
		}
		else if ( (e.getModifiersEx() & InputEvent.ALT_DOWN_MASK) == InputEvent.ALT_DOWN_MASK) {
			//hide
			thisP.setVisible(false);
		}
		else if ( (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) == InputEvent.SHIFT_DOWN_MASK) {
			//display true distance from selected poem to this one
			if (!(GUI.selectedPoem==null) && !thisP.equals(GUI.selectedPoem)) {
				//calculate weighted distance
				double dist = (GUI.USE_LOG_DISTANCES) ? GUI.logNDDist(GUI.selectedPoem.metricValues, thisP.metricValues, Poem.metricWeights) : GUI.nDDist(GUI.selectedPoem.metricValues, thisP.metricValues, Poem.metricWeights);
				dist *= Math.pow(10,GUI.selectedDistancePrecision/2);
				String dispString = new String(""+dist+" from \""+GUI.selectedPoem.getTitle()+"\" by "+
						GUI.selectedPoem.getAuthor()+" to \""+thisP.getTitle()+"\" by "+thisP.getAuthor());
				int index = GUI.distancesList.getSelectedIndex();
				if (index == -1) { //no selection
					index = 0;
				} else { //add after the selected one
					index++;
				}
				GUI.distancesListModel.insertElementAt(dispString, index);
				//select the new item and make it visible
				GUI.distancesList.setSelectedIndex(index);
				GUI.distancesList.ensureIndexIsVisible(index);
				GUI.distancesFrame.setVisible(true);
				GUI.distancesFrame.requestFocus();
			}
			else {
				Toolkit.getDefaultToolkit().beep(); //whoa!
			}
		}
		else {
			//toggle full display?
			thisP.toggleTextView();
		}
	}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mousePressed(MouseEvent e) {}
	public void mouseReleased(MouseEvent e) {}

	/**
	 * Pane where poem objects are drawn
	 */
	public class DisplayPane extends JPanel {
		public DisplayPane() {
			super(new BorderLayout());
			this.setOpaque(true);
			this.setBackground(Color.white);
		}
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			
			//OLDneed to update with this component's current size
			if (Poem.zoomFactor*GUI.screenWidth != this.getWidth() || Poem.zoomFactor*GUI.screenHeight != this.getHeight()) { 
				GUI.screenWidth = (int)Math.round(this.getWidth()/Poem.zoomFactor);
				GUI.screenHeight = (int)Math.round(this.getHeight()/Poem.zoomFactor);
				if (DEBUG_ON) {
					System.out.println("!!updating GUI to "+GUI.screenWidth+" x "+GUI.screenHeight);
					System.out.print("DisplayPane.paintComponent: updating dPane pSz from "+dPane.getPreferredSize()+" to ");
				}
				dPane.setPreferredSize(new Dimension(
					(int)Math.round(GUI.screenWidth*Poem.zoomFactor),
					(int)Math.round(GUI.screenHeight*Poem.zoomFactor)
					));
				if (DEBUG_ON) {
					System.out.println(dPane.getPreferredSize());
				}
				dPane.revalidate();
				if (!GUI.doPCAInProcess) {
					scalePoems(0); //rescale
					updatePainting();
				}
				dPane.repaint();
			}

			Color bgCol, fgCol;
			if (blackBackgroundMenuItem.getState()) {
				fgCol = Color.white;
				bgCol = Color.black;
			}
			else {
				fgCol = Color.black;
				bgCol = Color.white;
			}

			if (doPCAInProcess) {
				//blank out, write processing
				//OR let user play w/ old setup?
				g.setColor(bgCol);
				g.fillRect(0,0,this.getWidth(),this.getHeight());
				g.setColor(fgCol);
				g.drawString("Performing lower-dimension visualization...",10,30);
			}
			
			if (calculateStressOption) {
					g.setColor(fgCol);
				if (calculateStressInProcess) {
					g.drawString("Calculating stress...", 2, this.getHeight()-2);
				}
				else if (displayStress < 0) {
					//message on how to calc
					g.drawString("Please run PCA or MDS to calculate and display stress", 2, this.getHeight()-2);
				}
				else {
					String s = new Double(displayStress).toString();
					if (displayStress<0.001) {
						//scientific notation--round to ZERO
						s = new String("0.00");
					}
					g.drawString("Stress="+s.substring(0,Math.min(5,s.length())), 2, this.getHeight()-2);
				}
			}
		}
	}

	/**
	 * Pane for details of selected poem
	 */
	public static class DetailsPane extends JPanel implements ActionListener, ChangeListener {
		//vars
		JLabel title, author;
		JTextArea poemText, taggedText;
		JScrollPane textPane, taggedTextPane, jspCenter, jspRight ;
		JButton fullTextButton, taggedTextButton;
		JFrame fullTextFrame, taggedTextFrame;
		JPanel jpLeft, jpCenter, jpRight, jpLeft2 ; //title-author-text | metric vals | color legend
		JLabel[][] metricValueLabels ;
		final static String FULL_TEXT_NA = "[full text n/a]";
		final static int TEXT_FRAME_WIDTH = 300, TEXT_FRAME_HEIGHT = 400;
		final static int PREF_HEIGHT = 100 ; //of whole pane
		final static int LEGEND_ENTRY_HEIGHT = 19;
		public DetailsPane() {
			super(new GridLayout(1,3)); //three panels L to R
			this.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT); //to be safe
			
			//DEBUGGING
			//System.out.println("getH/getW="+this.getHeight()+"/"+this.getWidth());
			//System.out.println("preferredsize="+this.getPreferredSize());

			jpLeft = new JPanel();
			jpLeft2 = new JPanel();
			jpCenter = new JPanel();
			jpRight = new JPanel();
			jpCenter.setBackground(Color.white);
			jpRight.setBackground(Color.white);
			jpLeft.setLayout(new GridLayout(3,1));
			jpLeft2.setLayout(new GridLayout(1,2));
			jpCenter.setLayout(new GridLayout(0,2));
			jpRight.setLayout(new GridLayout(0,1));
			jspCenter = new JScrollPane(jpCenter,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			jspRight = new JScrollPane(jpRight,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			jspRight.getVerticalScrollBar().setUnitIncrement(LEGEND_ENTRY_HEIGHT);
			jspRight.getVerticalScrollBar().setBlockIncrement(LEGEND_ENTRY_HEIGHT*(int)Math.round((double)PREF_HEIGHT/(double)LEGEND_ENTRY_HEIGHT));
			jspRight.getViewport().addChangeListener(this); //to repaint, since had artifact problems...

			this.setOpaque(true);
			this.setBackground(Color.white);
			this.setBorder(new LineBorder(Color.black,1,false));

			title = new JLabel();
			author = new JLabel();
			title.setOpaque(true);
			title.setBackground(Color.white);
			author.setOpaque(true);
			author.setBackground(Color.white);
			author.setForeground(Color.black);

			fullTextButton = new JButton(FULL_TEXT_NA);
			taggedTextButton = new JButton(FULL_TEXT_NA);
			taggedTextButton.setEnabled(Poem.viewTaggedText);

			jpLeft.add(title);
			jpLeft.add(author);
			jpLeft2.add(fullTextButton);
			jpLeft2.add(taggedTextButton);
			jpLeft.add(jpLeft2);
			//jpLeft.add(fullTextButton);
			
			metricValueLabels = new JLabel[Poem.numMetrics][2];
			for (int i=0;i<Poem.numMetrics;i++) {
				metricValueLabels[i][0] = new JLabel(Poem.metricNameLookup[i]);
				jpCenter.add(metricValueLabels[i][0]);
				metricValueLabels[i][1] = new JLabel("[]");
				jpCenter.add(metricValueLabels[i][1]);
			}

			jspCenter.getVerticalScrollBar().setUnitIncrement((int)Math.round(jpCenter.getPreferredSize().getHeight()/(double)Poem.numMetrics));
			
			add(jpLeft);
			add(jspCenter); //Scrollable
			add(jspRight);
			
			//This shouldn't do anything since GUI.sW==0. Oh, but it does! Perhaps since it's a pointer, when the value actually gets set it changes prefSz?
			//System.out.println("GUI.screenWidth="+GUI.screenWidth);
			jpCenter.setPreferredSize(new Dimension(GUI.screenWidth/3,(int)jpCenter.getPreferredSize().getHeight()));

			this.setPreferredSize(new Dimension(GUI.screenWidth,PREF_HEIGHT));

			fullTextButton.addActionListener(this);
			taggedTextButton.addActionListener(this);

			JTextArea locPoemText = new JTextArea(1,0); //1 row, any # cols
			locPoemText.setLineWrap(false); //don't wrap
			locPoemText.setEditable(true);
			JScrollPane locTextPane = new JScrollPane(locPoemText,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			locTextPane.setMaximumSize(new Dimension(this.getWidth(),80));

			JTextArea locPoemText2 = new JTextArea(1,0); //1 row, any # cols
			locPoemText2.setLineWrap(true); //do wrap
			locPoemText2.setEditable(true);
			JScrollPane locTextPane2 = new JScrollPane(locPoemText2,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			locTextPane2.setMaximumSize(new Dimension(this.getWidth(),80));

			final JFrame locFullTextFrame = new JFrame("Full Text");
			locFullTextFrame.add(locTextPane); //setContentPane
			locFullTextFrame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE); //change to dispose when updated
			locFullTextFrame.setSize(TEXT_FRAME_WIDTH,TEXT_FRAME_HEIGHT);

			final JFrame locTaggedTextFrame = new JFrame("Full Text");
			locTaggedTextFrame.add(locTextPane2); //setContentPane
			locTaggedTextFrame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE); //change to dispose when updated
			locTaggedTextFrame.setSize(TEXT_FRAME_WIDTH,TEXT_FRAME_HEIGHT);

			textPane = locTextPane; //set pointers
			poemText = locPoemText;
			fullTextFrame = locFullTextFrame;
			taggedTextPane = locTextPane2;
			taggedText = locPoemText2;
			taggedTextFrame = locTaggedTextFrame;
		}
		
		public DetailsPane(Poem p) {
			this();
			if (p!=null) {
				updatePoem(p);
			}
		}
		
		public void updatePoem(Poem p) {
			//System.out.println("updateing poem:");
			if (p==null) {
				title.setText("");
				author.setText("");
				author.setBackground(Color.white);

				JTextArea jta = new JTextArea(1,0);
				jta.setEditable(true);
				jta.setLineWrap(false);
				poemText = jta;
				JTextArea jta2 = new JTextArea(1,0);
				jta2.setEditable(true);
				jta2.setLineWrap(true);
				taggedText = jta2;

				fullTextFrame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
				JFrame jf = new JFrame();
				fullTextFrame = jf;
				taggedTextFrame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
				JFrame jf2 = new JFrame();
				taggedTextFrame = jf2;

				fullTextFrame.setTitle("Full Text");
				fullTextButton.setText(FULL_TEXT_NA);
				fullTextFrame.setVisible(false);
				taggedTextFrame.setTitle("Tagged Text");
				taggedTextButton.setText(FULL_TEXT_NA);
				taggedTextFrame.setVisible(false);
				
				for (int i=0;i<Poem.numMetrics;i++) {
					metricValueLabels[i][1].setText("[]");
				}

				JScrollPane locTextPane = new JScrollPane(jta,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
				locTextPane.setMaximumSize(new Dimension(this.getWidth(),80));
				textPane = locTextPane;
				JScrollPane locTextPane2 = new JScrollPane(jta2,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
				locTextPane2.setMaximumSize(new Dimension(this.getWidth(),80));
				taggedTextPane = locTextPane2;

				return;
			}

			//System.out.println(""+p.getTitle()+" by "+p.getAuthor());

			//p!=null
			title.setText(p.getTitle());
			author.setText(p.getAuthor());
			//look up color
			Color c = Poem.colorTable.get(p.getAuthor());
			if (c!=null) { //it shouldn't be
				author.setBackground(c);
				author.repaint(); //helps?NOT
				jpLeft.repaint(); //helps?YES!
				//System.out.println("repainted jpL");
			}

			//instantiate new window/scroll pane/text area so that old one still exists
			//reassign this's pointers
			JTextArea locPoemText = new JTextArea(1,0); //1 row, any # cols
			locPoemText.setLineWrap(false); //don't wrap
			locPoemText.setEditable(true); //can copy, etc.
			JScrollPane locTextPane = new JScrollPane(locPoemText,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			locTextPane.setMaximumSize(new Dimension(this.getWidth(),80));

			//fullTextButton.addActionListener(this);
			fullTextFrame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
			JFrame locFullTextFrame = new JFrame("Full Text");
			locFullTextFrame.add(locTextPane); //setContentPane
			locFullTextFrame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
			locFullTextFrame.setSize(TEXT_FRAME_WIDTH,TEXT_FRAME_HEIGHT);

			textPane = locTextPane; //set pointers
			poemText = locPoemText;
			fullTextFrame = locFullTextFrame;

			fullTextFrame.setTitle(title.getText()+" by "+author.getText());
			String txt ;
			String[] ptxt = p.text;
			poemText.setEditable(true);
			poemText.selectAll();
			poemText.cut();
			fullTextButton.setText(FULL_TEXT_NA);
			if (ptxt!=null) {
				txt = ptxt[0];
				//fullTextButton.setText(ptxt[0]+"...");
				fullTextButton.setText("Original text");
				for (int i=1;i<ptxt.length;i++) {
					txt += "\n"+ptxt[i];
				}
				poemText.append(txt);
			}
			poemText.setEditable(true);

			if (Poem.viewTaggedText) {
				JTextArea locTaggedText = new JTextArea(1,0); //1 row, any # cols
				locTaggedText.setLineWrap(true); //do wrap: could be all one line...
				locTaggedText.setEditable(true); //can copy, etc.
				JScrollPane locTextPane2 = new JScrollPane(locTaggedText,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
				locTextPane2.setMaximumSize(new Dimension(this.getWidth(),80));

				taggedTextFrame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
				JFrame locTaggedTextFrame = new JFrame("Tagged Text");
				locTaggedTextFrame.add(locTextPane2); //setContentPane
				locTaggedTextFrame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
				locTaggedTextFrame.setSize(TEXT_FRAME_WIDTH,TEXT_FRAME_HEIGHT);

				taggedTextPane = locTextPane2; //set pointers
				taggedText = locTaggedText;
				taggedTextFrame = locTaggedTextFrame;
				taggedTextFrame.setTitle(title.getText()+" by "+author.getText());
		
				taggedText.setEditable(true);
				taggedText.selectAll();
				taggedText.cut();
	
				taggedTextButton.setText(FULL_TEXT_NA);
				if (p.poemTaggedText != null) {
					taggedTextButton.setText("Tagged text");
					String[] tag;
					txt = new String("");
					Iterator iter1 = p.poemTaggedText.iterator(); //over sentences
					while (iter1.hasNext()) {
						java.util.List taggedSentence = (java.util.List)iter1.next(); //over words in sentence
						Iterator iter2 = taggedSentence.iterator();
						while (iter2.hasNext()) {
							tag = (String[])iter2.next(); //{word,tag}
							txt += tag[0]+"/"+tag[1]+" ";
						}
						txt += "\n";
						if (DEBUG_ON && (Math.random()<0.01 || txt.length()<50)) {
							System.out.println("added tagged line to poem "+p.getTitle());
						}
					}
					taggedText.append(txt);
				}

			}

			for (int i=0;i<Poem.numMetrics;i++) {
				String s = "= "+p.metricValues[i];
				if (s.length()>9) {
					s = s.substring(0,9); //arbitrary
				}
				metricValueLabels[i][1].setText(s);
			}

		}
		
		/**
		 * Remove all labels from legend, and add one for each new poet
		 * Alphabetize? Other order?
		 */
		public void updateLegend() {
			//System.out.println("detailP:updating Legend");
			//remove all
			this.jpRight.removeAll();
			//use Poem.colorTable
			jpRight.setPreferredSize(new Dimension(jpRight.getPreferredSize().width,Poem.colorTable.size()*LEGEND_ENTRY_HEIGHT));
			Vector<JLabel> labelVector = new Vector<JLabel>();
			Vector<Float> hVector = new Vector<Float>();
			float[] hsbVals ;
			float h;
			int left, right, ind ;
			for (Enumeration e = Poem.colorTable.keys(); e.hasMoreElements();) {
				String s = (String)e.nextElement();
				Color c = (Color)Poem.colorTable.get(s);
				JLabel jl = new JLabel(s);
				jl.setOpaque(true);
				jl.setForeground(Color.black);
				jl.setBackground(c);
				jl.setBorder(new LineBorder(Color.black,1,false));
				
				//insertion sort by hue
				hsbVals = Color.RGBtoHSB(c.getRed(),c.getGreen(),c.getBlue(),null);
				h = hsbVals[0];
				left = 0;
				right = labelVector.size();
				while (left<right) {
					ind = (left+right)/2;
					if (((Float)hVector.elementAt(ind)).floatValue() < h) {
						left = ind+1;
					}
					else {
						right = ind;
					}
				}
				ind = left ; //and left==right
				labelVector.add(ind,jl);
				hVector.add(ind,new Float(h));
				//MAKE <5 SMALL SIZE: jl.setPreferredSize(jl.getPreferredSize().width,LEGENED_ENTRY_HEIGHT);
			}
			Iterator iter = labelVector.iterator();
			while (iter.hasNext()) {
				JLabel jl = (JLabel)iter.next();
				jpRight.add(jl);
			}
			jpRight.revalidate(); //for JScrollPane
			this.repaint();
		}

		/**
		 * Clicking button for full text pop-up window
		 */
		public void actionPerformed(ActionEvent ae) {
			if (ae.getSource()==fullTextButton && !fullTextButton.getText().equals(FULL_TEXT_NA)) {
				fullTextFrame.setVisible(true);
			}
			else if (ae.getSource()==taggedTextButton) {
				if (taggedText.getText().trim().length()>0) {
					taggedTextFrame.setVisible(true);
				}
				else {
					JOptionPane.showMessageDialog(null, 
							"Please load file again to run metrics and store POS tags.", 
							"No tags stored", JOptionPane.INFORMATION_MESSAGE);
				}
			}
		}

		/**
		* Repaint jpR when scrolled to eliminate artifacts (Java bug?)
		*/
		public void stateChanged(ChangeEvent e) {
			if (e.getSource() == jspRight.getViewport()) {
				jpRight.repaint();
			}
		}
	}

	/**
	 * pop-up help window
	 */
	public static class HelpWindow extends JFrame {
		public HelpWindow() {
			super("Help");
			this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			JTextArea jta = new JTextArea(0,70);
			jta.setEditable(false);
			jta.setText("Poem text files should be formatted as:\nTITLE\nAUTHOR\nTEXTTEXTTEXT[...]\n******\nTITLE2\nAUTHOR(2)\nTEXT...\n" +
						"When loaded, these will be analyzed with various stylistic metrics. To save all currently loaded poems to disk, choose \"Save to objects file...\" in the File menu\n" + 
						"Hold the Control key while clicking a poem to select it and bring up its values. Alt+click to hide from view. Shift-click to show true (weighted) distance.");
			this.add(jta);
			this.pack();
			this.setVisible(true);
		}
	}
}

/**
 * Poem represents a poem object, including both raw data
 * (text, title, author) as well as computed metrics and
 * the methods by which the metrics are calculated.
 * Static variables are used for naming lookups, etc.
 * To add a metric: update metricNameLookup; update metricWeights; add private/static variables; add counts/etc.; add run__Freq() to runMetrics();
 */
class Poem extends JLabel {
	//the GUI instance in use
	static GUI gui;
	
	//tagger stuff
	static boolean viewTaggedText = false ;
	static POSTagger tagger;
	static Vector<java.util.List> sentencesToTag;
	static Vector<String> curSentence;
	static java.util.List<java.util.List> taggedText;
	public java.util.List<java.util.List> poemTaggedText;
	//POS variables
	final static String[] POSArray = {"$", "'", "\"", "(", ")",
		",","--","?","!",".",":",";","...",
			"CC","CD","DT","EX","FW","IN","JJ","JJR","JJS","LS","MD","NN","NNP",
			"NNPS","NNS","PDT","POS","PRP","PRP$","RB","RBR","RBS","RP","SYM","TO",
			"UH","VB","VBD","VBG","VBN","VBP","VBZ","WDT","WP","WP$","WRB" };
	final static String[] POSAbbreviationLookup = {"dollar","single quotation mark","double quotation mark","opening parenthesis","closing parenthesis",
			"comma","dash","sentence terminator [question mark]","sentence terminator [exclamation point]","sentence terminator [period]","colon","semi-colon","ellipsis",
			"conjunction, coordinating","numeral, cardinal","determiner",
			"existential there","foreign word","preposition or conjunction, subordinating",
			"adjective or numeral, ordinal","adjective, comparative",
			"adjective, superlative","list item marker","modal auxiliary",
			"noun, common, singular or mass","noun, proper, singular",
			"noun, proper, plural","noun, common, plural","pre-determiner",
			"genitive marker","pronoun, personal","pronoun, possessive",
			"adverb","adverb, comparative","adverb, superlative","particle","symbol",
			"'to' as preposition or infinitive marker","interjection","verb, base form",
			"verb, past tense","verb, present participle or gerund","verb, past participle",
			"verb, present tense, not 3rd person singular",
			"verb, present tense, 3rd person singular","WH-determiner","WH-pronoun",
			"WH-pronoun, possessive","Wh-adverb"
			};
	static Hashtable<String,Integer> POSReverseLookup;
	//within PRP and PRP$ (personal pronouns)
	static Hashtable<String,String> personalPronounTable; //key="i",val="1SGN":1st person, singular, neuter
	final static String[] personalPronounArray = {"I","me","my","mine","myself",
			"you","your","yours","yourself","thee","thou","thy",
			"he","him","his","himself","hisself",
			"she","her","hers","herself",
			"it","its","itself","one","oneself",  //"ownself","self", AMBIGUOUS
			"we","us","our","ours","ourselves",
			"yourselves",
			"they","them","their","theirs","themselves"};
	final static String[] personalPronounLookup = {"1SGN","2SGN","3SGM","3SGF","3SGN","1PLN","2PLN","3PLN"};
	static Hashtable<String,Integer> personalPronounReverseLookup;

	//contractions; for freq & to help tagger
	static Hashtable<String,String[]> contractionsLookup ;

	//poem text, line by line
	public String[] text;

	//drawing variables
	private int xDraw, yDraw; //location (scaled) to draw on screen
	private double xPosRaw, yPosRaw; //the actual magnitudes of projections onto PCA axes
	static double xScale, yScale, xAdd, yAdd;
	static double zoomFactor ; //1.0 means fit all on screen at once
	static boolean flipAxes ; //draw x as y if true
	public static Hashtable<String,Color> colorTable; //to draw authors different colors
	static int color_num, color_denom, colorIter, last_color_denom;
	final static int drawWidth = 80, drawHeight = 25; //box size
	final static int dotWidth = 5, dotHeight = 5; //collapsed box size
	static boolean viewBoxes = true; //view full or collapsed box
	//font variables
	static String fontName;
	static int fontStyle, fontSize;
	static Font font;

	private String author, title; //separate b/c String & identifiers

	//actual metric values
	public double[] metricValues;

	//counts for use during runMetrics
	/***can delete these (or make static) after runMetrics if more memory needed*****/
	static int[] POSCounts;
	static int[] personalPronounCounts;
	private double wordCount, numLines, numStanzas ;
	static double contractionsCount ;
	static double sumWordLength ;

	//for histogram metrics: bin edges and corresponding metric values
	final static int[] numLinesBinMaxArray = {5, 10, 18, 25, 40, 60, 80, 120, 200, 500}; //last bin is >500
	final static double[] numLinesBinMetricValues = {0, .1, .2, .3, .4, .5, .6, .7, .8, .9, 1};
	final static int[] wcBinMaxArray = {16, 35, 60, 120, 200, 310, 450, 650, 1000, 2000}; //last bin is >3600
	final static double[] wcBinMetricValues = {0, .1, .2, .3, .4, .5, .6, .7, .8, .9, 1};
	final static int[] avgLineLengthBinMaxArray = {1, 2, 3, 4, 5, 6, 8, 9, 10, 12, 15, 20, 24};
	final static double[] avgLineLengthBinMetricValues = {0, .05, .1, .15, .2, .25, .3, .35, .40, .45, .50, .55, .60, .65};
	final static double[] avgWordLengthBinMaxArray = {3.5, 4, 4.5, 5, 5.5, 6}; //last bin is >8
	final static double[] avgWordLengthBinMetricValues = {0, .1, .2, .3, .4, .5, .6, .7, .8};
	final static int[] numStanzasBinMaxArray = {1, 2, 3, 4, 6, 8, 12, 18, 24, 36}; //last bin is >36
	final static double[] numStanzasBinMetricValues = {0, .1, .2, .3, .4, .5, .6, .7, .8, .9, 1};
	final static int[] avgLinesPerStanzaBinMaxArray = {1, 2, 3, 4, 10, 20, 30}; //last bin is >24
	final static double[] avgLinesPerStanzaBinMetricValues = {0, .1, .2, .3, .4, .5, .6, .7};

	//metric names, count, weights
	final static String[] metricNameLookup = {"wordCount",
			"$Freq","'Freq","\"Freq","(Freq",")Freq",",Freq","--Freq",
			"?Freq","!Freq",".Freq",":Freq",";Freq","...Freq",
			"CCFreq","CDFreq","DTFreq","EXFreq","FWFreq","INFreq",
			"JJFreq","JJRFreq","JJSFreq","LSFreq","MDFreq",
			"NNFreq","NNPFreq","NNPSFreq","properNounFreq","NNSFreq","PDTFreq","POSFreq",
			"PRPFreq","PRP$Freq","RBFreq","RBRFreq","RBSFreq","RPFreq","SYMFreq","TOFreq",
			"UHFreq","VBFreq","VBDFreq","VBGFreq","VBNFreq","VBPFreq","VBZFreq","WDTFreq",
			"WPFreq","WP$Freq","WRBFreq",
			"numLines","avgLineLength",
			"1SGNFreq","2SGNFreq","3SGMFreq","3SGFFreq","3SGNFreq",
			"1PLNFreq","2PLNFreq","3PLNFreq",
			"contractionsFreq","avgWordLength",
			"alliterationFreq","assonanceFreq","consonanceFreq","slantEndRhymeFreq","semiEndRhymeFreq","perfectEndRhymeFreq","identityEndRhymeFreq",
			"adjFreq","nounFreq","verbFreq","conjFreq",
			"topNounFreq","topAdjFreq","topVerbFreq","numStanzas","avgLinesPerStanza",
			"comparativesFreq", "soundDevicesFreq",
			"partialEndRhymeFreq","fullEndRhymeFreq","endRhymeFreq" };
	final static int numMetrics = metricNameLookup.length;
	static double[] metricWeights = {0.20, //WC	1
			0.0,0.0,0.7,0.0,0.0,0.7,0.7, //$ thru --	2 thru 8
			0.7,0.7,0.7,0.7,0.7,0.7, //? thru ...	9 thru 14
			0.7,0.7,0.4,0.7,0.0,1.0, //CC thru IN	thru 20
			1.0,1.0,1.0,0.1,0.9, //JJ thru MD		thru 25
			1.0,0.0,0.0,1.0,0.7,0.7,0.2, //NN thru POS	thru 32
			0.3,0.3,0.7,0.7,0.7,0.7,0.2,0.3, //PRP thru TO	thru 40
			0.7,0.7,0.7,0.7,0.7,0.7,0.7,0.7, //UH thru WDT	thru 48
			0.7,0.7,0.7, //WP WP$ WRB				thru 51
			.24,.13, //numLines avgll				thru 53
			0.4,0.4,0.4,0.4,0.175, //1SGN thru 3SGN	thru 58
			0.4,0.07,0.4, //1PLN thru 3PLN			thru 61
			0.7,0.7, //contractionsFreq thru avgWordLength	thru 63
			0.7,0.7,0.7,0.5,0.7,0.7,0.7, //alliteration thru identityEndRhyme	thru 70
			1.0, 1.0, 1.0, 1.0, //adjFreq thru conjFreq	thru 74
			1.0, 1.0, 1.0, 0.08, 0.1, //topNounFreq thru linesPerStanza	thru 79
			1.0, 1.0, //comparaitves thru soundDevices thru 81
			1.0, 1.0, 1.0 } ; //partial thru endRhymeFreq thru 84

	//dictionary for phoneme lookup
	static Dictionary dictionary;
	
	//variables to keep track of phonemes within window
	static LinkedList<String> phonemeWindow; //most recent phonemes; could add syllable_sep chars later...
	static LinkedList<String[]> lineEndingsWindow;
	static LinkedList<String> alliterationWindow;//only FIRST phoneme (if non-vowel); drive-->d
	//sizes
	final static int SYLLABLES_PER_WINDOW = 9 ; //for both consonant and vowel
	final static int ASSONANCE_THRESHOLD = 2, CONSONANCE_THRESHOLD = 2;
	final static int LINES_PER_WINDOW = 3 ; //as in, 3 preceding+1 current=4
	final static int ALLITERATION_WINDOW_SIZE = 1;
	//need doubles so count/wc is a decimal & not int
	static double alliterationCount, assonanceCount, consonanceCount ;
	static double identityEndRhymeCount, perfectEndRhymeCount, semiEndRhymeCount, slantEndRhymeCount ;
	final static int NO_RHYME = 0, SLANT_RHYME = 1, SEMI_RHYME = 2, PERFECT_RHYME = 3, IDENTITY_RHYME = 4;
	
	static Hashtable<String,Double> individualNounCounts, individualVerbCounts, individualAdjCounts ;

	/*** static initialization methods ***/
	
	/**
	 * Called in Applet's init()
	 */
	public static void init(GUI gui, URL codeBase, URL rulesURL, URL lexiconURL, URL contnURL) {
		Poem.gui = gui;
		
		Poem.dictionary = Dictionary.getDictionary(codeBase); //get instance

		//Poem.taggerRulesURL = rulesURL;
		//Poem.taggerLexiconURL = lexiconURL;
		try {
			//still pass contnURL in case we miss any contractions, somehow
			Poem.tagger = new POSTagger(lexiconURL,rulesURL,contnURL); //instantiate tagger
			Poem.loadContractions(contnURL);
		} catch(hepple.postag.InvalidRuleException ire) { 
			System.out.println("Error initializing tagger:");
			ire.printStackTrace(System.out);
		} catch(IOException ioe) { 
			System.out.println("I/O error initializing tagger:");
			ioe.printStackTrace(System.out);
		}

		Poem.xScale = 1; //defaults--changed before any actual drawing, though
		Poem.yScale = 1;
		Poem.xAdd = 0;
		Poem.yAdd = 0;
		Poem.flipAxes = false ; 

		Poem.colorTable = new Hashtable<String,Color>();
		//ABOVE;Poem.numMetrics = metricNameLookup.length;
		Poem.POSReverseLookup = new Hashtable<String,Integer>();
		for (int i=0;i<Poem.POSArray.length;i++) {
			Poem.POSReverseLookup.put(Poem.POSArray[i],new Integer(i));
		}

		//make personal pronoun hashtable
		personalPronounTable = new Hashtable<String,String>();

		personalPronounTable.put("I","1SGN");
		personalPronounTable.put("me","1SGN");
		personalPronounTable.put("my","1SGN");
		personalPronounTable.put("mine","1SGN");
		personalPronounTable.put("myself","1SGN");

		personalPronounTable.put("you","2SGN"); //could also be PLURAL.......
		personalPronounTable.put("your","2SGN"); //could also be PLURAL.......
		personalPronounTable.put("yours","2SGN"); //could also be PLURAL.......
		personalPronounTable.put("yourself","2SGN"); 
		personalPronounTable.put("thee","2SGN");  //could also be PLURAL.......
		personalPronounTable.put("thou","2SGN");  //could also be PLURAL.......
		personalPronounTable.put("thy","2SGN");  //could also be PLURAL.......

		personalPronounTable.put("yourselves","2PLN");

		personalPronounTable.put("he","3SGM");
		personalPronounTable.put("him","3SGM");
		personalPronounTable.put("his","3SGM");
		personalPronounTable.put("himself","3SGM");
		personalPronounTable.put("hisself","3SGM");

		personalPronounTable.put("she","3SGF");
		personalPronounTable.put("her","3SGF");
		personalPronounTable.put("hers","3SGF");
		personalPronounTable.put("herself","3SGF");

		personalPronounTable.put("it","3SGN");
		personalPronounTable.put("its","3SGN");
		personalPronounTable.put("itself","3SGN");
		personalPronounTable.put("one","3SGN");
		personalPronounTable.put("oneself","3SGN");

		personalPronounTable.put("we","1PLN");
		personalPronounTable.put("us","1PLN");
		personalPronounTable.put("our","1PLN");
		personalPronounTable.put("ours","1PLN");
		personalPronounTable.put("ourselves","1PLN");

		personalPronounTable.put("they","3PLN");
		personalPronounTable.put("them","3PLN");
		personalPronounTable.put("their","3PLN");
		personalPronounTable.put("theirs","3PLN");
		personalPronounTable.put("themselves","3PLN");

		personalPronounReverseLookup = new Hashtable<String,Integer>();
		for (int i=0;i<personalPronounLookup.length;i++) {
			personalPronounReverseLookup.put(personalPronounLookup[i],new Integer(i));
		}

		Poem.zoomFactor = 1;
		fontName = "Arial";
		fontStyle = Font.PLAIN;
		fontSize = 10;
		Poem.updateFont();
	}
	
    /**
    * Load the contractions lookup from specified URL
    */
    public static void loadContractions(URL url) throws IOException {
		Poem.contractionsLookup = new Hashtable<String,String[]>();
		if (url==null) return;

		BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
		String ln ;
		String[] toks, expansion ;
		while ((ln=br.readLine()) != null) {
			//contraction|ex pan sion
			toks = ln.split("\\|");
			expansion = toks[1].split(" ");
			contractionsLookup.put(toks[0],expansion);
		}
		br.close();
    }

	/*** constructors ***/
	
	/**
	 * Shared constructor code
	 */
	private Poem() {
		super();
		super.setSize(Poem.drawWidth,Poem.drawHeight);

		this.metricValues = new double[Poem.numMetrics];
		POSCounts = new int[Poem.POSArray.length]; //bins foreach POS
		personalPronounCounts = new int[Poem.personalPronounLookup.length]; //bin foreach 1SGN etc. category

		this.setBackground(new Color(255,255,255,80));
		this.setBorder(new LineBorder(Color.black,1,true));
		this.setOpaque(true);
		this.setForeground(Color.black);

		this.addMouseListener(Poem.gui);
	}

	/**
	 * Constructor: takes poem text, with txt[0]=title, [1]=author
	 */
	public Poem(String[] poemText) {
		this();
		this.text = new String[poemText.length-2];
		System.arraycopy(poemText,2,this.text,0,poemText.length-2);
		this.author = poemText[1];
		this.title = poemText[0];
		this.setTextAndToolTip();

		if (!Poem.viewBoxes) {
			this.toggleTextView(); //to OFF
		}
	}

	/**
	 * Constructor: title, author, and metric values (from pre-computed text file)
	 */
	public Poem(String author, String title, double[] values) {
		this();
		this.text = null;
		this.author = author;
		this.title = title;
		this.setTextAndToolTip();
		System.arraycopy(values,0,this.metricValues,0,values.length);

		if (!Poem.viewBoxes) {
			this.toggleTextView(); //to OFF
		}
	}

	/*** metrics-related methods ***/

	/**
	 * Pre-condition: poem text is loaded into 'text'
	 * Post-condition: metric variables are stored
	 * Runs all available style metrics on the poem text
	 * ALL OTHER METHODS SHOULD BE STATIC, & PASS IN LOCAL VARs!
	 */
	public void runMetrics() {
		long t1 = System.currentTimeMillis();
		//Want to INCLUDE or EXCLUDE blank lines?? Both for numLines and avgPerLine
		//for now - exclude (makes more sense, esp if separate #stanzas metric)
		numLines = this.text.length ;
		contractionsCount = 0 ;
		sumWordLength = 0 ;
		wordCount = 0.0;
		alliterationCount = 0;
		assonanceCount = 0;
		consonanceCount = 0;
		identityEndRhymeCount = 0;
		perfectEndRhymeCount = 0;
		semiEndRhymeCount = 0;
		slantEndRhymeCount = 0;
		for (int i=0;i<this.POSCounts.length;i++) {
			POSCounts[i]=0;
		}
		for (int i=0;i<this.personalPronounCounts.length;i++) {
			personalPronounCounts[i]=0;
		}

		//tagging vars
		curSentence = new Vector<String>();
		sentencesToTag = new Vector<java.util.List>();
		taggedText = null;
		
		//word frequency
		individualNounCounts = new Hashtable<String,Double>() ;
		individualVerbCounts = new Hashtable<String,Double>() ;
		individualAdjCounts = new Hashtable<String,Double>() ;
		
		//Phoneme vars
		phonemeWindow = new LinkedList<String>();
		lineEndingsWindow = new LinkedList<String[]>();
		alliterationWindow = new LinkedList<String>();
		//set sizes
		for (int i=0;i<SYLLABLES_PER_WINDOW;i++) {
			phonemeWindow.addFirst("xx0"); //dummy "vowel"
		}
		for (int i=0;i<LINES_PER_WINDOW;i++) {
			lineEndingsWindow.add(new String[] {""});
		}
		for (int i=0;i<ALLITERATION_WINDOW_SIZE;i++) {
			alliterationWindow.add("");
		}

		runWordPOSCount(); //also sets sentencesToTag
		//System.out.println("runMetrics() thru runWordPOSCount() took "+((System.currentTimeMillis()-t1)/1000)+" seconds");
		runWordCountBin();
		runAvgWordLengthBin();
		runAvgLineLengthBin();
		runNumLinesBin();
		runNumStanzasBin();
		runLinesPerStanzaBin();
		runContractionsFreq(); //need to write! divide by wc
		runAlliterationFreq(); //div by wc
		runAssonanceFreq();
		runConsonanceFreq();
		runRhymeFreq(); //div by numLines
		runSoundDevicesFreq();
		taggedText = tagger.runTagger(sentencesToTag);
		this.poemTaggedText = (Poem.viewTaggedText) ? taggedText : null ;
		/* DEBUGGING!!!OUTPUT TAGGER RESULTS */
		if (GUI.DEBUG_ON && false) {
			try {
				//OUTPUT tagged text for manual inspection (along w/ orig text??)
				//popup instead?
				JFrame jf = new JFrame("Tagged text: "+this.getTitle());
				JTextArea jta = new JTextArea();
				jta.setEditable(true);
				jta.setLineWrap(true);
				jta.append("TAGGED:\n");
				String[] tag;
				Iterator iter1 = taggedText.iterator(); //over sentences
				while (iter1.hasNext()) {
					java.util.List taggedSentence = (java.util.List)iter1.next(); //over words in sentence
					Iterator iter2 = taggedSentence.iterator();
					while (iter2.hasNext()) {
						tag = (String[])iter2.next(); //{word,tag}
						jta.append(tag[0]+"/"+tag[1]+" ");
					}
					jta.append("\n");
				}
				jta.append("ORIGINAL:\n");
				for (int i=0;i<this.text.length;i++) {
					jta.append(this.text[i]+"\n");
				}
				jf.add(jta);
				jf.setSize(400,500);
				jf.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
				jf.setVisible(true);
			} catch(Exception e) {
				e.printStackTrace(System.out);
			}
		}
		/**/
		runPOSFreq();
		runPersonalPronounFreq();
		runWordFreq();
		//POS counts, lengths, punctuation, etc.
		//System.out.println("runMetrics() took "+((System.currentTimeMillis()-t1)/1000)+" seconds");
	}

	/**
	 * Iterate through the lines of text, counting words (and POS etc.) foreach line
	 */
	protected void runWordPOSCount() {
		//computation to get word count
		//Vector<String> curSentence = new Vector<String>();
		boolean stanzaFlag = false ;
		for (int i=0;i<text.length;i++) {
			//calls countWords, which processes a LOT
			text[i] = text[i].replace("[","(");
			text[i] = text[i].replace("{","(");
			text[i] = text[i].replace("]",")");
			text[i] = text[i].replace("}",")");
			
			double lineLength = countWords(text[i]);

			wordCount += lineLength;
			if (lineLength == 0) {
				//no words-->overcounted numLines
				--numLines ;
				//stanza break
				if (!stanzaFlag) {
					++numStanzas ;
					stanzaFlag = true ;
				}
			}
			else {
				stanzaFlag = false;
			}
		}

		//make sure >=1 stanza
		if (!stanzaFlag) {
			++numStanzas;
		}

		//if no final period, add curSentence
		if (curSentence.size()>0) {
			Vector<String> cs2 = new Vector<String>(curSentence); //new instance
			sentencesToTag.add(cs2);
			curSentence = new Vector<String>();
		}
	}

	/**
	 * Returns the number of words in a space-separated line of poetry
	 * Make static? Private? Public??
	 */
	protected double countWords(String s) {
		double ret = 0;
		int posIndex;
		String[] words = null;
		words = s.split(" "); //also -- or ...
		ret = (double)words.length;
		String tmp, tmp2;//, retPOS;

		//iterate for all words on line s
		boolean lastWordFlag = false;
		String[] phonemeInfo = null, tmpStrArr = null ;
		String tmpPhoneme = null ;
		int tmpInt, tmpInt2, hyphenSkips = 0; //POS tagger deals with hyphens itself, 
					//and all in same iteration-->skip the "extra" iteration for hyphens broken up for phonemes
		for (int i=0;i<words.length;i++) {
			//tmp is an individual word, unless...
			tmp = words[i];
			tmp = tmp.trim(); //trim whitespace
			
			//determine if last word in sentence
			//there's a sentence terminator, and it's not "Mr." or anything
			if ((tmp.endsWith(".") || tmp.endsWith("!") || tmp.endsWith("?")) && (phonemeInfo=Poem.dictionary.getPhonemes(tmp))==null) {
				lastWordFlag = true;
			}

			//isolate word, lowercase
			//DOES LOWERCASE HERE POSE ANY PROBLEMS? Yes, if not reverted (NNP)
			/*tmp = tmp.toLowerCase();*/ //for Dictionary lookup

			tmp = Poem.markAndRemoveBeginEndPunctuation(tmp,this.POSCounts);

			if (tmp.length()==0) {
				ret--; //overcounted...
				continue;
			}

			//new POS tagging - use POSTagger
			//Look up contractions here, to store as metric; pass two (or more) parts separately to tagger
			//add word to curSentence
			//check for -- or ... embedded in tmp. Assuming not both...
			//ONLY 128-ascii for now (i.e. no dashes, only "-")
			tmpInt = tmp.indexOf("--");
			tmpInt2 = tmp.indexOf("...");
			if (tmpInt > 0) { //embedded (trimmed edges above)
				//split words
				tmpStrArr = tmp.split("--");
			}
			else if (tmpInt2 > 0) {
				//split on "..."
				tmpStrArr = tmp.split("...");
			}
			else {
				tmpStrArr = new String[] {tmp};
			}
			
			String curStr, origStr ; //current String, in lowercase
			for (int j=0;j<tmpStrArr.length;j++) {
				curStr = new String(tmpStrArr[j].toLowerCase()) ;
				origStr = new String(tmpStrArr[j]) ;
				//if (GUI.DEBUG_ON) {
				//	System.out.println("orig:"+origStr+";"+tmpStrArr[j]);
				//}
				if (curStr==null || curStr.trim().length()<=0) {
					System.out.println("empty word: *"+curStr+"*");
					continue ; //this SHOULDN'T happen...
				}
				//add word lengths
				sumWordLength += curStr.length();
				
				//Expand 'd contractions (e.g., want'd) here since phonemes are ~same
				//WAIT on other contractions until after phonemic lookup
				//has to have 'd NOT exist and ed YES exist in dict
				
				//DO PHONEME STUFF, before expanding contractions
				phonemeInfo = Poem.dictionary.getPhonemes(curStr);
				if (phonemeInfo==null || phonemeInfo.length<=0) {
					//try first caps
					phonemeInfo = Poem.dictionary.getPhonemes(curStr.substring(0,1).toUpperCase()+curStr.substring(1));
				}
				if ((phonemeInfo==null || phonemeInfo.length<=0) && curStr.endsWith("'d")) {
					//'d contraction (e.g. webb'd)
					String edExpand = curStr.replace("'d","ed"); //should only be one 'd sequence
					phonemeInfo = Poem.dictionary.getPhonemes(edExpand);
				}
				if ((phonemeInfo==null || phonemeInfo.length<=0) && (curStr.endsWith("'s") || curStr.endsWith("s'"))) {
					int sInd = (curStr.endsWith("'s")) ? curStr.lastIndexOf("'s") : curStr.lastIndexOf("s'");
					if (Poem.dictionary.getPhonemes(curStr.substring(0,sInd))!=null) {
						//'s/s' contraction (e.g. ladder's, ladders')
						//add to tmpStrArr, update origStr, curStr
						String[] tmpStrArr2 = new String[tmpStrArr.length + 1];

						//copy in order
						System.arraycopy(tmpStrArr,0,tmpStrArr2,0,j); //copy up to here
						System.arraycopy(new String[] {curStr.substring(0,sInd),curStr.substring(sInd)},
								0,tmpStrArr2,j,2);
						System.arraycopy(tmpStrArr,j+1,tmpStrArr2,j+2,tmpStrArr.length - j - 1);

						tmpStrArr = tmpStrArr2; //pointer assignment

						origStr = new String(tmpStrArr[j]) ;
						curStr = new String(origStr.toLowerCase());

						//get phonemes
						phonemeInfo = Poem.dictionary.getPhonemes(curStr);
					}
				}
				if ((phonemeInfo==null || phonemeInfo.length<=0) && 
						curStr.indexOf("-")>0 && 
						curStr.indexOf("-")<(curStr.length()-1)) { //contains hyphen, not on edge
					//hyphenation: break into multiple words, shift tmpStrArr
					//Keep Capitalization!!
					String[] subTmpStrArr = tmpStrArr[j].split("-");
					String[] tmpStrArr2 = new String[tmpStrArr.length + subTmpStrArr.length - 1];
					System.out.print("breaking up into hyphenateds:");
					for (int f=0;f<subTmpStrArr.length;f++) {
						System.out.print(" "+subTmpStrArr[f]);
					}
					
					hyphenSkips += subTmpStrArr.length-1; //for POS tagger
					
					System.out.println();
					//copy in order
					System.arraycopy(tmpStrArr,0,tmpStrArr2,0,j); //copy up to here
					System.arraycopy(subTmpStrArr,0,tmpStrArr2,j,subTmpStrArr.length);
					System.arraycopy(tmpStrArr,j+1,tmpStrArr2,j+subTmpStrArr.length,tmpStrArr.length - j - 1);

					tmpStrArr = tmpStrArr2;

					//get phonemes
					phonemeInfo = Poem.dictionary.getPhonemes(tmpStrArr[j].toLowerCase());
				}
				if (phonemeInfo==null || phonemeInfo.length<=0) {
					//try all caps
					phonemeInfo = Poem.dictionary.getPhonemes(curStr.toUpperCase());
				}
				if (phonemeInfo != null && phonemeInfo.length>0) { //otherwise, not in dictionary
					String s1 ;
					//first (easiest), take alliterative consonant
					tmpPhoneme = phonemeInfo[0] ;
					//check if consonant, even. For vowels, last char is a number (stress)
					if (!Poem.isVowelPhoneme(tmpPhoneme)) {
						//compare to others in window
						Iterator<String> iter = alliterationWindow.iterator();
						while (iter.hasNext()) {
							s1 = (String)iter.next();
							//size is only one, otherwise might need to rewrite this
							if (tmpPhoneme.equals(s1)) {
								++alliterationCount ;
								continue ; //or something? reduces double-counts
							}
						}
					}
					
					//update alliterationWindow
					alliterationWindow.addFirst(tmpPhoneme);
					alliterationWindow.removeLast();
					
					//second, look for assonance & consonance:
					//foreach new phoneme, match window phonemes (IGNORE stress)
					//if vowel, update phonemeWindow (cut off last until vowel
					for (int k=0;k<phonemeInfo.length;k++) {
						s1 = phonemeInfo[k];
						Iterator<String> iter = phonemeWindow.iterator();
						if (Poem.isVowelPhoneme(s1)) {
							//it's a vowel. add w/o stress
							//s = s.substring(0,s.length()-1);
							//search for matches
							int matches = 0;
							while (iter.hasNext()) {
								String sTmp = (String)iter.next();
								if (s1.substring(0,s1.length()-1).equals(sTmp.substring(0,sTmp.length()-1))) {
									++matches ;
								}
							}
							if (matches >= ASSONANCE_THRESHOLD) { //HARDCODED threshold
								++assonanceCount ;
								//System.out.println("assonance: "+s1+" in "+tmpStrArr[j]);
							}
							
							//delete LAST syllable of phonemeWindow, i.e. until hit a vowel (remove vowel too)
							while (!Poem.isVowelPhoneme(phonemeWindow.removeLast())) {;}
						}
						else {
							//it's a consonant. do consonance search, then add to window.
							int matches = 0;
							while (iter.hasNext()) {
								if (((String)iter.next()).equals(s1)) {
									++matches ;
								}
							}
							if (matches >= CONSONANCE_THRESHOLD) { //HARDCODED threshold
								++consonanceCount ;
								//System.out.println("consonance: "+s1+" in "+tmpStrArr[j]);
							}
						}
						
						//add to window
						phonemeWindow.addFirst(s1);
					}
					
					//rhyme, if ending word. Need to figure out all these cases--this is dummy code basically
					if (i==words.length-1 && j==tmpStrArr.length-1) { //last word block, last word
						//get current line ending: from consonant before last stressed vowel
						LinkedList<String> ending = new LinkedList<String>(); //first-->last in reading order
						boolean flag = false ; //if read last stressed vowel
						for (int k=phonemeInfo.length-1;k>=0;k--) {
							if (flag && phonemeInfo[k].substring(phonemeInfo[k].length()-1).matches("[0-9]")) {
								break ;
							}
							ending.addFirst(phonemeInfo[k]) ; //add to beginning, since working backwards
							//HARDCODED if vowel stress if 1 or 2 (i.e. > 0)
							if (phonemeInfo[k].endsWith("1") || phonemeInfo[k].endsWith("2")) {
								flag = true ;
							}
						}
						
						//check against lineEndingsWindow for rhymes
						Iterator<String[]> iter = lineEndingsWindow.iterator();
						//System.out.println("szEDN="+ending.size());
						//for (int q=0;q<ending.size();q++) {
						//	System.out.print(ending.get(q)+" ");
						//}
						//System.out.println();
						while (iter.hasNext()) {
							String[] sArr = (String[])iter.next();
							//call method
							//System.out.println(ending.toArray().getClass());
							//problem first iter when all ""??
							int rhymeVal = Poem.rhymePhonemes(sArr,(String[])ending.toArray(new String[] {})); //CLASSCASTEXCEPTION??
							//use results
							if (rhymeVal==Poem.SLANT_RHYME) {
								//partial
								++slantEndRhymeCount ;
							}
							else if (rhymeVal==Poem.SEMI_RHYME) {
								//partial
								++semiEndRhymeCount ;
							}
							else if (rhymeVal==Poem.PERFECT_RHYME) {
								//full
								++perfectEndRhymeCount ;
								//break ; //in case two consecutive couplets w/ same rhyme
							}
							else if (rhymeVal==Poem.IDENTITY_RHYME) {
								//full
								++identityEndRhymeCount ;
								//break ; //in case two consecutive couplets w/ same rhyme
							}
						}
						
						//update window
						lineEndingsWindow.addFirst((String[])ending.toArray(new String[] {}));
						lineEndingsWindow.removeLast();
					}
				}
				else {
					//not in dict (or no info)
					System.out.println("not in dictionary or no info for: *"+tmpStrArr[j]+"*");
				}

				if (hyphenSkips>0) {
					hyphenSkips--;
					continue;
				}
				
				//expand contraction
				//hyphenation poses a problem--could have "grey-green" not in lexicon, just grey and green.
				//What if just took the FINAL segment of hyphenation, if unknown? Good idea!
				curStr = origStr.toLowerCase();
				//System.out.println("orig:"+origStr);
				String[] sa = (String[])contractionsLookup.get(curStr);
				if (sa==null) {
					//check 'd ending, given that it's NOT a contraction (like they'd). Replace with -ed.
					//check Poem.tagger.lexicon.get(word) to make sure this helps
					if (origStr.endsWith("'d") && 
							!(Poem.tagger.lexicon.containsKey(curStr)) && 
							Poem.tagger.lexicon.containsKey(curStr.replace("'d","ed"))) {
						sa = new String[] {curStr.replace("'d","ed")};
					}
					else if (Poem.tagger.lexicon.get(curStr)==null &&
							curStr.contains("-") && 
							Poem.tagger.lexicon.get(curStr.substring(curStr.lastIndexOf("-")+1))!=null) {
						//hyphenation (per above comment)...anything else it could be?
						sa = new String[] {curStr.substring(curStr.lastIndexOf("-")+1)};
					}
					else if ((origStr.endsWith("'s") || origStr.endsWith("s'")) && 
							!(Poem.tagger.lexicon.containsKey(curStr))) { //possessive, e.g. ladder's, most likely
						int sInd = (origStr.endsWith("'s")) ? origStr.lastIndexOf("'s") : origStr.lastIndexOf("s'") ;
						sa = new String[] {curStr.substring(0,sInd), curStr.substring(sInd)};

						if (!(Poem.tagger.lexicon.containsKey(sa[0]))) { //unrecognized-->leave Caps
							sa[0] = origStr.substring(0,sInd);
						}
					}
					else { //keep Capitalized if lowercase not in lexicon...
						sa = (Poem.tagger.lexicon.containsKey(curStr)) ? new String[] {curStr} : new String[] {origStr};
					}
				}
				else {
					++contractionsCount;
					//if (GUI.DEBUG_ON) {
					//	System.out.println("contrct:"+sa[0]);
					//}
				}
				
				//add to curSentence for later tagging
				for (int k=0;k<sa.length;k++) {
					curSentence.add(sa[k]);
				}
			}
			ret += tmpStrArr.length - 1; //UNDERcounted wordcount by this many

			//if last word in sentence, add curSentence to sentencesToTag
			if (lastWordFlag) {
				Vector<String> cs2 = new Vector<String>(curSentence); //new instance	
				sentencesToTag.add(cs2);
				//System.out.println("adding sentence:"+cs2.elementAt(0)+" to "+cs2.elementAt(cs2.size()-1));
				curSentence = new Vector<String>();
				lastWordFlag = false; //reset
			}
		}
		return ret;
	}

	/**
	 * To make countWords look cleaner, moved the switch statement for
	 * punctuation marks in here.
	 */
	protected static String markAndRemoveBeginEndPunctuation(String tmp, int[] countArr) {
			String tmp2;
			String[] tmpStrArr;
			//Mark down any beginning or ending punctuation!
			tmpStrArr = Poem.trimNonChars(tmp);
			tmp2 = tmpStrArr[2] ;
			if (tmp2.endsWith("-") && !tmp2.endsWith("--")) { //ends with -, but not --
				//interpret as a dash
				//actually increment POSCounts, since these are being dropped
				++countArr[((Integer)POSReverseLookup.get("--")).intValue()];
				//cut off
				tmpStrArr[2] = tmp2.substring(0,tmp2.length()-1);
			}
			//loop over possible punctuation for begin/end
			for (int j=0;j<3;j+=2) { //the bread
				tmp = tmpStrArr[j].trim();
				while (tmp.length() > 0) {
					if (tmp.startsWith("...")) {
						tmp2 = "...";
					}
					else if (tmp.startsWith("--")) {
						tmp2 = "--";
					}
					else {
						switch(tmp.charAt(0)) {
							case '$':
								tmp2 = "$";
								break;
							case '\'':
								tmp2 = "'";
								break;
							case '\"':
								tmp2 = "\"";
								break;
							case '(':
							case '{':
							case '[':
								tmp2 = "(";
								break;
							case ')':
							case '}':
							case ']':
								tmp2 = ")";
								break;
							case ',':
								tmp2 = ",";
								break;
							case '?':
								tmp2 = "?";
								break;
							case '!':
								tmp2 = "!";
								break;
							case '.':
								tmp2 = ".";
								break;
							case ':':
								tmp2 = ":";
								break;
							case ';':
								tmp2 = ";";
								break;
							default:
								if (tmp.length()==1) {
									tmp = "";
								}
								else {
									tmp = tmp.substring(1,tmp.length());
								}
								continue; //while loop
						}
					}

					//actually increment POSCounts, since these are being dropped
					++countArr[((Integer)POSReverseLookup.get(tmp2)).intValue()];
					//cut off stored portion, loop
					tmp = (tmp2.length()>=tmp.length())?"":tmp.substring(tmp2.length());
				}
			}
			tmp = tmpStrArr[1]; //the meat
			return tmp;
	}

	/**
	 * Trim leading and trailing non-character chars of s
	 */
	public static String[] trimNonChars(String s) {
		int i, j;
		if (s==null || s.length()==0) return new String[] {"",s,""}; //no chars anyway

		//increment counter from beginning until first legit char
		for (i=0;i<s.length() && !Poem.isLowercaseLetter(s.charAt(i)) && !Poem.isUppercaseLetter(s.charAt(i));i++) {}
		//decrement counter from end until last legit char
		for (j=s.length()-1;j>=0 && !Poem.isLowercaseLetter(s.charAt(j)) && !Poem.isUppercaseLetter(s.charAt(j));j--) {}

		if (j<i) { //no real chars
			return new String[] {"","",""} ;
		}
		String[] ret = new String[3];
		ret[0] = s.substring(0,i);
		ret[1] = s.substring(i,j+1); //return from first real char to last real char
		ret[2] = (j+1<s.length()) ? s.substring(j+1) : "" ;
		return ret;
	}
	
	/**
	 * Says if vowel. For now, looks @ last digit. Could also do a table lookup.
	 */
	public static boolean isVowelPhoneme(String s) {
		if (s==null || s.length()<=0) return false ;
		return s.substring(s.length()-1).matches("[0-9]") ;
	}

	private static boolean isLowercaseLetter(char c) { //decide if legit
		boolean ret = false;
		if (c>='a' && c<='z') { //a through z
			ret = true;
		}
		//ret = (c=='-')?true:ret; //hyphen--BUT only for leading & trailing; could be --
		return ret;
	}
	
	private static boolean isUppercaseLetter(char c) { //decide if legit
		boolean ret = false;
		if (c>='A' && c<='Z') { //a through z
			ret = true;
		}
		//ret = (c=='-')?true:ret; //hyphen--BUT only for leading & trailing; could be --
		return ret;
	}
	
	/**
	 * Return:	NO_RHYME=0 if no rhyme (cat&dog);
	 * 			SLANT_RHYME=1 if slant (rumble&jungle)
	 *			SEMI_RHYME=2 if semirhyme (bend&ending)
	 *			PERFECT_RHYME=3 if perfect (sight&flight)
	 *			IDENTITY_RHYME=4 if identity (gun&begun)
	 * right now: 2 or 0
	 * precondition: both start w/ (consonants before) last stressed vowel?
	 * Don't really know stress on monosyllabic words...guess stressed?
	 */
	public static int rhymePhonemes(String[] a, String[] b) {
		//next time, on "programming w/ daveE"!
		//if consonants until first vowel (the stressed one) are same, could be ID
		if (a==null || a.length<=0 || b==null || b.length<=0 || (a.length==1 && a[0].length()<=0) || (b.length==1 && b[0].length()<=0)) {
			//System.out.println("NO_RH: null or zero length");
			return Poem.NO_RHYME ;
		}
		LinkedList<String> c1a = new LinkedList<String>(); //consonants before vowel in a
		LinkedList<String> c1b = new LinkedList<String>(); //consonants before vowel in b
		String v1a ; //stressed vowel (first) in a
		String v1b ; //stressed vowel (first) in b
		LinkedList<String> a2 = new LinkedList<String>(); //after first vowel
		LinkedList<String> b2 = new LinkedList<String>(); //after first vowel
		
		//keep track of matches, make decision at end
		boolean c1match, v1match, match2;
		
		//get c1a
		int i1 ;
		for (i1=0;i1<a.length && !Poem.isVowelPhoneme(a[i1]);i1++) {
			c1a.addLast(a[i1]); //keep in reading order
		}
		if (i1==a.length) return NO_RHYME; //no vowels in a
		
		//get v1a
		v1a = a[i1].substring(0,a[i1].length()-1) ; //ignore stress 1 or 2 (or 0 for mono)
		i1++;

		//get c1b
		int i2 ;
		for (i2=0;i2<b.length && !Poem.isVowelPhoneme(b[i2]);i2++) {
			c1b.addLast(b[i2]); //keep in reading order
		}
		if (i2==b.length) return NO_RHYME; //no vowels in a

		//get v1b
		v1b = b[i2].substring(0,b[i2].length()-1) ;
		i2++;
		
		//get a2
		while (i1<a.length) {
			a2.addLast(a[i1]); //keep in reading order
			i1++;
		}

		//get b2
		while (i2<b.length) {
			b2.addLast(b[i2]); //keep in reading order
			i2++;
		}
		
		//compare initial consonants
		c1match = c1a.equals(c1b); //specified in AbstractList--same length, all elements .equal()
		
		//compare stressed vowels
		v1match = v1a.equals(v1b);
		
		//compare endings
		//note: vowel stress discrepancy not an issue b/c all are 0
		match2 = a2.equals(b2); //specified in AbstractList

		//make decision
		if (c1match && v1match && match2) { //identical
			//System.out.println("ID_RH: "+Poem.arrToStr(a)+" & "+Poem.arrToStr(b));
			return Poem.IDENTITY_RHYME ;
		}
		else if (v1match && match2) { //&&!c1match. identical, excluding initial consonant(s)
			//System.out.println("PERF_RH: "+Poem.arrToStr(a)+" & "+Poem.arrToStr(b));
			return Poem.PERFECT_RHYME ;
		}
		else if (v1match || (match2 && a2.size()>0)) { 
			//either stressed vowel matches, OR everything after it exactly matches (& is non-empty)
			//examples (for better or for worse): rumble&jungle bent&peck; NOT rumble&jingle
			//System.out.println("SLANT_RH: "+Poem.arrToStr(a)+" & "+Poem.arrToStr(b));
			if (v1match) {
				//check for SEMI_RHYME
				Iterator ai=a2.iterator(), bi=b2.iterator();
				String sa, sb;
				while (ai.hasNext()) {
					sa = (String)ai.next();
					if (!bi.hasNext()) {
						if (Poem.isVowelPhoneme(sa)) {
							//b is done, a is moving on to next syllable-->semi rhyme
							return Poem.SEMI_RHYME ;
						}
						else {
							return Poem.SLANT_RHYME ;
						}
					}
					
					sb = (String)bi.next();
					if (!sa.equals(sb)) {
						return Poem.SLANT_RHYME;
					}
				}
				if (bi.hasNext()) {
					sb = (String)bi.next();
					if (Poem.isVowelPhoneme(sb)) {
						return Poem.SEMI_RHYME ;
					}
				}
				return Poem.SEMI_RHYME;
			}
			return Poem.SLANT_RHYME ;
		}
		//System.out.println("NO_RH: "+Poem.arrToStr(a)+" & "+Poem.arrToStr(b));
		return Poem.NO_RHYME ;
	}
	
	public static String arrToStr(String[] sArr) {
		if (sArr==null) return "";
		String ret = "";
		for (int i=0;i<sArr.length;i++) {
			ret += sArr[i]+" ";
		}
		return ret;
	}

	/**
	 * Set wordCount metric given this.wordCount, using static bins
	 */
	protected void runWordCountBin() {
		//first, determine which bin
		int bin ;
		for (bin=0;bin<Poem.wcBinMaxArray.length && wordCount>Poem.wcBinMaxArray[bin];bin++) {;}
		//then set value, by looking up in other array, and prorating
		double val = wcBinMetricValues[bin];
		if (bin<Poem.wcBinMaxArray.length) { //not final bin, which won't get adjusted, for better or worse
			double prevMax = (bin==0) ? 0 : wcBinMaxArray[bin-1];
			val += (wordCount - prevMax)/(wcBinMaxArray[bin]+1-prevMax)*(wcBinMetricValues[bin+1]-wcBinMetricValues[bin]);
		}
		this.setMetric("wordCount", val);
	}

	/**
	 * Set avgWordLength metric given this.wordCount, sumLength, using static bins
	 */
	protected void runAvgWordLengthBin() {
		//first, calc avg word length
		double avgWordLength = sumWordLength / wordCount ;
		//first, determine which bin
		int bin ;
		for (bin=0;bin<Poem.avgWordLengthBinMaxArray.length && avgWordLength>Poem.avgWordLengthBinMaxArray[bin];bin++) {;}
		//then set value, by looking up in other array, and prorating
		double val = avgWordLengthBinMetricValues[bin];
		if (bin<Poem.avgWordLengthBinMaxArray.length) { //not final bin, which won't get adjusted, for better or worse
			double prevMax = (bin==0) ? 0 : avgWordLengthBinMaxArray[bin-1];
			val += (avgWordLength - prevMax)/(avgWordLengthBinMaxArray[bin]+1-prevMax)*(avgWordLengthBinMetricValues[bin+1]-avgWordLengthBinMetricValues[bin]);
		}
		this.setMetric("avgWordLength", val);
	}

	/**
	 * Set avgLineLength metric given this.wordCount and this.numLines, using static bins
	 */
	protected void runAvgLineLengthBin() {
		//first calculate avgLineLength. Only non-blank lines count.
		double avgLineLength = wordCount / numLines ;
		//then determined bin
		int bin ;
		for (bin=0;bin<Poem.avgLineLengthBinMaxArray.length && avgLineLength>Poem.avgLineLengthBinMaxArray[bin];bin++) {;}
		//then set value, by looking up in other array, and prorating
		double val = avgLineLengthBinMetricValues[bin];
		if (bin<Poem.avgLineLengthBinMaxArray.length) { //not final bin, which won't get adjusted, for better or worse
			double prevMax = (bin==0) ? 0 : avgLineLengthBinMaxArray[bin-1];
			val += (avgLineLength - prevMax)/(avgLineLengthBinMaxArray[bin]+1-prevMax)*(avgLineLengthBinMetricValues[bin+1]-avgLineLengthBinMetricValues[bin]);
		}
		this.setMetric("avgLineLength", val);
	}

	/**
	 * Set numLines metric given numLines, using static bins
	 */
	protected void runNumLinesBin() {
		//first, determine which bin
		int bin ;
		for (bin=0;bin<Poem.numLinesBinMaxArray.length && numLines>Poem.numLinesBinMaxArray[bin];bin++) {;}
		//then set value, by looking up in other array, and prorating
		double val = numLinesBinMetricValues[bin];
		if (bin<Poem.numLinesBinMaxArray.length) { //not final bin, which won't get adjusted, for better or worse
			double prevMax = (bin==0) ? 0 : numLinesBinMaxArray[bin-1];
			val += (numLines - prevMax)/(numLinesBinMaxArray[bin]+1-prevMax)*(numLinesBinMetricValues[bin+1]-numLinesBinMetricValues[bin]);
		}
		this.setMetric("numLines", val);
		if (val<0.05) {
			System.out.println(this.getTitle()+"/"+this.getAuthor()+" has numLines="+numLines+"->"+val);
		}
	}
	
	/**
	 * Set numStanzas metric given bins/values
	 */
	protected void runNumStanzasBin() {
		//first, determine which bin
		int bin ;
		for (bin=0;bin<Poem.numStanzasBinMaxArray.length && numStanzas>Poem.numStanzasBinMaxArray[bin];bin++) {;}
		//then set value, by looking up in other array, and prorating
		double val = numStanzasBinMetricValues[bin];
		if (bin<Poem.numStanzasBinMaxArray.length) { //not final bin, which won't get adjusted, for better or worse
			double prevMax = (bin==0) ? 0 : numStanzasBinMaxArray[bin-1];
			val += (numStanzas - prevMax)/(numStanzasBinMaxArray[bin]+1-prevMax)*(numStanzasBinMetricValues[bin+1]-numStanzasBinMetricValues[bin]);
		}
		this.setMetric("numStanzas", val);
		//System.out.println("setting numStanzas to " + val + " from "+numStanzas);
	}

	/**
	 * Set linesPerStanza given numLines, numStanzas, bins, values
	 */
	protected void runLinesPerStanzaBin() {
		//first calculate avgLineLength. Only non-blank lines count.
		double avgLinesPerStanza = numLines / numStanzas ;
		//then determined bin
		int bin ;
		for (bin=0;bin<Poem.avgLinesPerStanzaBinMaxArray.length && avgLinesPerStanza>Poem.avgLinesPerStanzaBinMaxArray[bin];bin++) {;}
		//then set value, by looking up in other array, and prorating
		double val = avgLinesPerStanzaBinMetricValues[bin];
		if (bin<Poem.avgLinesPerStanzaBinMaxArray.length) { //not final bin, which won't get adjusted, for better or worse
			double prevMax = (bin==0) ? 0 : avgLinesPerStanzaBinMaxArray[bin-1];
			val += (avgLinesPerStanza - prevMax)/(avgLinesPerStanzaBinMaxArray[bin]+1-prevMax)*(avgLinesPerStanzaBinMetricValues[bin+1]-avgLinesPerStanzaBinMetricValues[bin]);
		}
		this.setMetric("avgLinesPerStanza", val);
		//System.out.println("Setting avgLPS to " + val + " from "+avgLinesPerStanza);
	}

	/**
	 * Calculate contractions frequency
	 */
	protected void runContractionsFreq() {
		this.setMetric("contractionsFreq", contractionsCount / wordCount);
	}

	/**
	 * Calculate alliteration frequency
	 */
	protected void runAlliterationFreq() {
		this.setMetric("alliterationFreq", alliterationCount / (wordCount - 1));
	}

	/**
	 * Calculate assonance frequency
	 */
	protected void runAssonanceFreq() {
		this.setMetric("assonanceFreq", assonanceCount / (wordCount - 1));//Right??+(double)SYLLABLES_PER_WINDOW));	
	}

	/**
	 * Calculate consonance frequency
	 */
	protected void runConsonanceFreq() {
		this.setMetric("consonanceFreq", consonanceCount / (wordCount - 1));//Right??+(double)SYLLABLES_PER_WINDOW));
	}

	/**
	 * Calculate rhyme (full & partial) frequency
	 */
	protected void runRhymeFreq() {
		if (numLines==1) {
			String[] rhymeMetrics = new String[] {"slantEndRhymeFreq","semiEndRhymeFreq","perfectEndRhymeFreq","identityEndRhymeFreq","partialEndRhymeFreq","fullEndRhymeFreq","endRhymeFreq"};
			for (int i=0;i<rhymeMetrics.length;i++) {
				this.setMetric(rhymeMetrics[i],0); //can't have rhyme w/ only 1 line
			}
		}
		else {
			double lpw = (double)LINES_PER_WINDOW;
			this.setMetric("slantEndRhymeFreq", slantEndRhymeCount / (Math.max(0,numLines-lpw)*lpw + Math.min(lpw,numLines)*(Math.min(lpw,numLines)-1)/2) );
			this.setMetric("semiEndRhymeFreq", semiEndRhymeCount / (Math.max(0,numLines-lpw)*lpw + Math.min(lpw,numLines)*(Math.min(lpw,numLines)-1)/2) );
			this.setMetric("perfectEndRhymeFreq", perfectEndRhymeCount / (Math.max(0,numLines-lpw)*lpw + Math.min(lpw,numLines)*(Math.min(lpw,numLines)-1)/2) );
			this.setMetric("identityEndRhymeFreq", identityEndRhymeCount / (Math.max(0,numLines-lpw)*lpw + Math.min(lpw,numLines)*(Math.min(lpw,numLines)-1)/2) );
			this.setMetric("partialEndRhymeFreq",this.getMetric("slantEndRhymeFreq")+this.getMetric("semiEndRhymeFreq"));
			this.setMetric("fullEndRhymeFreq",this.getMetric("perfectEndRhymeFreq")+this.getMetric("identityEndRhymeFreq"));
			this.setMetric("endRhymeFreq",this.getMetric("partialEndRhymeFreq")+this.getMetric("fullEndRhymeFreq"));
		}
	}
	
	/**
	 * Calculate frequency of "sound devices" alliteration+assonance+consonance
	 */
	protected void runSoundDevicesFreq() {
		this.setMetric("soundDevicesFreq",this.getMetric("alliterationFreq")+this.getMetric("assonanceFreq")+this.getMetric("consonanceFreq"));
	}

	/**
	 * Calculate counts and frequencies of POS. Sum=1
	 */
	protected void runPOSFreq() {
		//generate counts foreach POS from taggedText
		String[] tag = null ;
		int posIndex;
		String retPOS;
		Iterator iter1 = taggedText.iterator(); //over sentences
		while (iter1.hasNext()) {
			java.util.List taggedSentence = (java.util.List)iter1.next(); //over words in sentence
			Iterator iter2 = taggedSentence.iterator();
			while (iter2.hasNext()) {
				tag = (String[])iter2.next(); //{word,tag}
				//System.out.println(tag[0]+" is a "+tag[1]);
				retPOS = tag[1];
				if (retPOS.equals("''")) {
					retPOS = "\"";
				}
				//look up index of that POS
				//System.out.println("Plath debugging:"+tag[0]+" is "+retPOS);
				posIndex = ((Integer)POSReverseLookup.get(retPOS)).intValue();
				//increment count for that POS bin
				++POSCounts[posIndex];
				
				//count IF important POS towards wordFreq
				Double d ;
				if (retPOS.startsWith("V")) {
					//verb
					d = individualVerbCounts.get(tag[0]);
					if (d == null) {
						individualVerbCounts.put(tag[0],new Double(1));
					}
					else {
						individualVerbCounts.put(tag[0],new Double(d.doubleValue()+1));
					}
				}
				else if (retPOS.startsWith("NN")) { //proper or not
					//noun
					d = individualNounCounts.get(tag[0]);
					if (d == null) {
						individualNounCounts.put(tag[0],new Double(1));
					}
					else {
						individualNounCounts.put(tag[0],new Double(d.doubleValue()+1));
					}
				}
				else if (retPOS.startsWith("JJ")) { //JJR JJS JJ
					//adj
					d = individualAdjCounts.get(tag[0]);
					if (d == null) {
						individualAdjCounts.put(tag[0],new Double(1));
					}
					else {
						individualAdjCounts.put(tag[0],new Double(d.doubleValue()+1));
					}
				}
			}
		}

		double totalKnownWords = wordCount ; 

		if (totalKnownWords<=0) { //set all to zero
			System.out.println("setting all POS to zero (totalKnownWords==0)");
			for (int i=0;i<POSArray.length;i++) { 
				this.setMetric(Poem.POSArray[i]+"Freq",0.0);
			}
			return;
		}

		//freq is count/totKnWords
		double debugSum = 0;
		
		//keep track of adj=CD+JJ+JJR+JJS+PDT+PRP$, noun=NN+NNP+NNS+NNPS+PRP, 
		//verb=VB+VBD+VBG+VBN?+VBP+VBZ, conj=CC+IN
		//CHANGED to reflect J. Miles analysis!
		//adj
		Vector<String> adjSyms = new Vector<String>();
		//String[] adjArr = new String[] {"CD","JJ","JJR","JJS","PDT","PRP$"};
		String[] adjArr = new String[] {"CD","JJ","JJR","JJS"};
		for (int i=0;i<adjArr.length;i++) {
			adjSyms.add(adjArr[i]);
		}
		//noun
		Vector<String> nounSyms = new Vector<String>();
		String[] nounArr = new String[] {"NN","NNP","NNS","NNPS"};//,"PRP"};
		for (int i=0;i<nounArr.length;i++) {
			nounSyms.add(nounArr[i]);
		}
		//verb
		Vector<String> verbSyms = new Vector<String>();
		//String[] verbArr = new String[] {"VB","VBD","VBG","VBP","VBZ"};
		String[] verbArr = new String[] {"VB","VBD","VBP","VBZ"};
		for (int i=0;i<verbArr.length;i++) {
			verbSyms.add(verbArr[i]);
		}
		//conj
		Vector<String> conjSyms = new Vector<String>();
		String[] conjArr = new String[] {"CC","IN"};
		for (int i=0;i<conjArr.length;i++) {
			conjSyms.add(conjArr[i]);
		}
		
		double adjCount=0, nounCount=0, verbCount=0, conjCount=0 ;
		for (int i=0;i<POSArray.length;i++) { 
			//foreach POS, set metric value
			double freq = POSCounts[i] / totalKnownWords;
			debugSum += freq;
			//System.out.println("setting " + Poem.POSArray[i]+"Freq to " + freq);
			this.setMetric(Poem.POSArray[i]+"Freq",freq);
			if (adjSyms.contains(Poem.POSArray[i])) {
				adjCount += POSCounts[i] ;
			}
			else if (nounSyms.contains(Poem.POSArray[i])) {
				nounCount += POSCounts[i] ;
			}
			else if (verbSyms.contains(Poem.POSArray[i])) {
				verbCount += POSCounts[i] ;
			}
			else if (conjSyms.contains(Poem.POSArray[i])) {
				conjCount += POSCounts[i] ;
			}
		}

		//proper noun: NNP+NNPS
		this.setMetric("properNounFreq",this.getMetric("NNPFreq")+this.getMetric("NNPSFreq"));

		//comparatives: JJS+JJR+RBR+RBS
		this.setMetric("comparativesFreq",this.getMetric("JJRFreq")+this.getMetric("JJSFreq")+this.getMetric("RBRFreq")+this.getMetric("RBSFreq"));

		double ANVCsum = adjCount+nounCount+verbCount+conjCount ;
		this.setMetric("adjFreq",adjCount / totalKnownWords);//ANVCsum);
		this.setMetric("nounFreq",nounCount / totalKnownWords);//ANVCsum);
		this.setMetric("verbFreq",verbCount / totalKnownWords);//ANVCsum);
		this.setMetric("conjFreq",conjCount / totalKnownWords);//ANVCsum);
	}

	/**
	 * Calculate counts and frequencies of personal prons. Sum=1
	 */
	protected void runPersonalPronounFreq() {
		//compare to TOTAL words-->get ratios (implicit) and absolute frequency (explicit)
		double totalWords = wordCount ;
		double totalRecognizedPRPs = 0;

		//get COUNTS, only looking @ PRP, PRP$
		Iterator iter1 = taggedText.iterator();
		String[] tag = null ;
		while (iter1.hasNext()) {
			java.util.List taggedSentence = (java.util.List)iter1.next();
			Iterator iter2 = taggedSentence.iterator();
			while (iter2.hasNext()) {
				tag = (String[])iter2.next();
				if (tag[1].startsWith("PRP") && personalPronounTable.containsKey(tag[0])) {
					//System.out.print(" *"+tag[0]+"="+tag[1]);
					++personalPronounCounts[((Integer)personalPronounReverseLookup.get(personalPronounTable.get(tag[0]))).intValue()]; //e.g., reverse lookoup 1SGN for bin#
					++totalRecognizedPRPs;
				}
				else {
					//System.out.print(" "+tag[0]+"="+tag[1]);
				}
			}
		}
		//System.out.println();

		if (totalRecognizedPRPs<=0) { //set all to zero
			System.out.println("setting ALL PRPs to zero (total<=0)");
			for (int i=0;i<personalPronounCounts.length;i++) { 
				this.setMetric(Poem.personalPronounLookup[i]+"Freq",0.0);
			}
			return;
		}

		//freq is count/totKnWords
		double debugSum = 0;
		for (int i=0;i<personalPronounCounts.length;i++) { 
			double freq = personalPronounCounts[i] / totalWords;//totalRecognizedPRPs; //OR totalWords;
			debugSum += freq;
			//System.out.println("setting "+Poem.personalPronounLookup[i]+"Freq to "+freq+" from count="+personalPronounCounts[i]+" of "+totalRecognizedPRPs+" total.");
			this.setMetric(Poem.personalPronounLookup[i]+"Freq",freq);
		}
		//System.out.println("Sum="+debugSum);
	}
	
	/**
	 * Frequency of frequencies
	 */
	protected void runWordFreq() {
		Collection<Double> c;
		Double[] dubArr ;
		double[] dArr;
		//take top 1 most frequent noun
		c = individualNounCounts.values();
		dubArr = (Double[])c.toArray(new Double[] {});
		if (dubArr.length<=0) {
			this.setMetric("topNounFreq",0);
		}
		else {
			dArr = new double[dubArr.length];
			for (int i=0;i<dubArr.length;i++) {
				dArr[i] = dubArr[i].doubleValue();
			}
			Arrays.sort(dArr); //but ascending...
			this.setMetric("topNounFreq",dArr[dArr.length-1] / wordCount);
		}

		//take top 1 most frequent adj
		c = individualAdjCounts.values();
		dubArr = (Double[])c.toArray(new Double[] {});
		if (dubArr.length<=0) {
			this.setMetric("topAdjFreq",0);
			if (GUI.DEBUG_ON) {
				System.out.println("NO adjectives!");
			}
		}
		else {
			dArr = new double[dubArr.length];
			for (int i=0;i<dubArr.length;i++) {
				dArr[i] = dubArr[i].doubleValue();
			}
			Arrays.sort(dArr); //but ascending...
			if (GUI.DEBUG_ON) {
				System.out.println("topJJfreq="+(dArr[dArr.length-1] / wordCount));
			}
			this.setMetric("topAdjFreq",dArr[dArr.length-1] / wordCount);
		}

		//take top 1 most frequent verb
		c = individualVerbCounts.values();
		dubArr = (Double[])c.toArray(new Double[] {});
		if (dubArr.length<=0) {
			this.setMetric("topVerbFreq",0);
		}
		else {
			dArr = new double[dubArr.length];
			for (int i=0;i<dubArr.length;i++) {
				dArr[i] = dubArr[i].doubleValue();
			}
			Arrays.sort(dArr); //but ascending...
			this.setMetric("topVerbFreq",dArr[dArr.length-1] / wordCount);
		}
	}

	/*** Display-related methods ***/
	
	/**
	 * Override these methods so doesn't change from its true position/size
	 */
	public void setLocation(int x, int y) {}
	public void setSize(int w, int h) {}

	/**
	 * Sets JLabel text and ToolTipText from this.title, this.author
	 */
	public void setTextAndToolTip() {
		this.setText(title+" by "+author);
		this.setToolTipText(title+" by "+author);
		super.setSize(Poem.drawWidth,Poem.drawHeight);
	}

	/**
	 * set 'font' from the properties
	 */
	public static void updateFont() {
		Poem.font = new Font(fontName, fontStyle, fontSize);
	}

	/**
	 * Modifies xDraw, yDraw of this, to reflect xScale etc.
	 */
	public void updateScale() {
		//System.out.println("updateScale pre-xD/yD="+this.xDraw+"/"+this.yDraw);
		if (Poem.flipAxes) { //flip xRaw, yRaw
			this.xDraw = (int)Math.round(Poem.zoomFactor*(this.yPosRaw*Poem.xScale+Poem.xAdd));
			this.yDraw = (int)Math.round(Poem.zoomFactor*(this.xPosRaw*Poem.yScale+Poem.yAdd));
		}
		else { //nothing flipped
			this.xDraw = (int)Math.round(Poem.zoomFactor*(this.xPosRaw*Poem.xScale+Poem.xAdd));
			this.yDraw = (int)Math.round(Poem.zoomFactor*(this.yPosRaw*Poem.yScale+Poem.yAdd));
		}
		super.setLocation(xDraw,yDraw); //need super so not overridden
		//System.out.println("updateScale post-xD/yD="+this.xDraw+"/"+this.yDraw);
	}
	
	public static void resetZoom() {
		Poem.zoomFactor = 1.0;
	}

	/**
	 * Updates Font from Poem's
	 */
	public void updateObjFont() {
		this.setFont(Poem.font);
	}

	/**
	 * Clear color table
	 */
	public static void clearColors() {
		Poem.colorTable.clear();
		color_num = 1;
		color_denom = 6;
		last_color_denom = 6;
		colorIter = 1;
	}

	/**
	 * Increments color counter
	 * Using HSB, 1/6->6/6, then 1/12->11/12, etc
	 */
	public static void incrementColorCounters() {
			//update color_num and color_denom
			if (color_denom==6) { //special case
				if (color_num==6) {
					/*colorIter = (colorIter%3) + 1;
					if (colorIter==1) {*/
						color_num=1;
						color_denom=12;
					/*}
					else {
						color_num = 1;
					}*/
				}
				else {
					color_num++;
				}
			}
			else if (color_num==color_denom-1) { //e.g. 11/12-->1/24
				//rem here color_denom>6 from first "if"
				colorIter = (colorIter%3) + 1;
				color_num = 1;
				if (colorIter==1) {
					//continue normally
					color_denom *= 2;
					last_color_denom = color_denom ;
				}
				else { //repeat w/ same color_denom
					//revert to last platform
					color_denom = last_color_denom ;
				}
			}
			else { //e.g. 3/12-->5/12
				color_num += 2;
			}
	}
	
	/**
	 * Calculate and set background color
	 */
	public void calculateAndSetColor() {
		Color c = (Color)Poem.colorTable.get(this.author);
		if (c==null) {
			//first, make c based on color_num and color_denom
			float hue = (float)color_num/(float)color_denom;
			float brightness=1, sat=1 ;
			/*if (colorIter==1) {
				brightness = (float)1;
				sat = (float)1;
			}
			else */if (colorIter==2) {
				brightness = (float)1;
				sat = (float)0.5;
			}
			else if (colorIter==3) {
				brightness = (float)0.75;
				sat = (float) 1;
			}

			System.out.println("HSB="+hue+"|"+sat+"|"+brightness+"|"+this.author);
			int col = Color.HSBtoRGB(hue,sat,brightness);
			col += (100 << 24);//ALPHA in bits 24-31
			c = new Color(col,true);
			Poem.colorTable.put(this.author,c);
			//then, update counters
			Poem.incrementColorCounters();
		}
		this.setBackground(c);
	}

	public void paint(Graphics g) {
		if (this.getText().length()==0) {
			super.setSize(Poem.dotWidth,Poem.dotHeight);
		}
		else {
			super.setSize(Poem.drawWidth,Poem.drawHeight);
		}
		if (this.getX()!=this.getXDraw() || this.getY()!=this.getYDraw()) {
			super.setLocation(this.getXDraw(),this.getYDraw()); //Not sure why, but necessary
		}
		super.paint(g);
	}

	/**
	 * Alternates between just a "dot" and the text view
	 */
	public void toggleTextView() {
		if (this.getText().length()==0) { //currently collapsed, so expand
			this.setTextAndToolTip(); //sets text to full
		}
		else { //currently full
			this.setText(""); //erase text
			super.setSize(Poem.dotWidth,Poem.dotHeight); //make smaller
		}
	}

	/*** get-set methods ***/
	
	/**
	 * Looks up 'name' and returns the corresponding value
	 * -1 indicates error (e.g. name not found)
	 */
	public double getMetric(String name) {
		int index = Poem.getMetricIndex(name);
		double ret = -1;
		if (index<0) {
			//error -- not found
		}
		else {
			ret = metricValues[index];
		}
		return ret;
	}

	public void setMetric(String name, double val) {
		int index = Poem.getMetricIndex(name);
		if (index<0) {
			//error -- name not found
		}
		else {
			metricValues[index] = val;
		}
	}

	/**
	 * Look up the index from the metric name
	 */
	public static int getMetricIndex(String name) {		
		int index = 0;
		while (!Poem.metricNameLookup[index].equals(name) && index<numMetrics) {
			index++;
		}
		//System.out.println("metric "+name+" index "+index);
		//System.out.println(Poem.metricNameLookup[index]);
		if (index==numMetrics) {
			//error -- name not found
			System.out.println("metric Name not found: "+name);
			return -1;
		}
		else {
			return index;
		}
	}

	//Author
	public void setAuthor(String a) { this.author=a; }
	public String getAuthor() { return this.author; }

	//Title
	public void setTitle(String t) { this.title=t; }
	public String getTitle() { return this.title; }

	//xPosRaw
	public void setXPosRaw(double x) { this.xPosRaw = x; }
	public double getXPosRaw() { return this.xPosRaw; }

	//yPosRaw
	public void setYPosRaw(double y) { this.yPosRaw = y; }
	public double getYPosRaw() { return this.yPosRaw; }

	//xDraw
	public void setXDraw(int x) { this.xDraw = x; }
	public int getXDraw() { return this.xDraw; }

	//yDraw
	public void setYDraw(int y) { this.yDraw = y; }
	public int getYDraw() { return this.yDraw; }

	/**
	 * toString converts the metric values to a string for output
	 */
	public String toString() {
		String ret = this.author;
		ret += "|"+this.title;
		for (int i=0;i<metricValues.length;i++) {
			ret += "|"+new Double(metricValues[i]).toString();
		}
		return ret;
	}
	
}

/**
 * Dictionary holds in memory the data contained in the 
 * dictionary (text) file, as well as some access methods.
 * Implemented as singleton class.
 */
class Dictionary {
	/* word|capitalization|part-of-speech|syntactic-part-of-speech|phonemes1:phonemes2:phonemesx */
	public static String dictFilename ;
	private Hashtable<String,String[]> wordLookup;
	private URL codeBase;
	final static String SEPARATOR_TOKEN = "\\|";

	private static Dictionary dictionary = null;

	/** 
	 * Public access method.
	 */
	public static Dictionary getDictionary(URL codeBase) {
		if (dictionary == null) {
			dictionary = new Dictionary(codeBase);
		}
		return Dictionary.dictionary;
	}

	/**
	 * Constructor. Load dictionary from file, store in memory.
	 */
	private Dictionary(URL codeBase) {
		if (Dictionary.dictFilename == null) {
			Dictionary.dictFilename = "dictionary.dict"; //default
		}
		wordLookup = new Hashtable<String,String[]>(); //initialize
		URL dictURL = null;
		try {
			dictURL = this.getClass().getResource(Dictionary.dictFilename);
			dictURL = (dictURL==null) ? new URL(codeBase,Dictionary.dictFilename) : dictURL ; //works for abs or relative
		} catch(MalformedURLException mue) {
			mue.printStackTrace(System.out);
		}
		String line = null;
		String[] lineTokens = null;
		try {
			URLConnection conn = dictURL.openConnection();
			BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			while ( (line=br.readLine()) != null) { //store each line in turn
				lineTokens = line.split(SEPARATOR_TOKEN); //split info
				String[] info = new String[lineTokens.length-1]; //don't include orig word
				System.arraycopy(lineTokens,1,info,0,info.length); //start@1, copy to 0
				wordLookup.put(lineTokens[0],info); //store
			}
		} catch(IOException ioe) {
			ioe.printStackTrace(System.out);
			System.out.println("error: " + ioe.getMessage());
		} catch(Exception e) {
			//array
			e.printStackTrace(System.out);
			System.out.println("error: " + e.getMessage());
		}
	}
	
	/**
	 * Clear dictionary instance
	 */
	public static void clear() {
		Dictionary.dictionary.clearTable();
		Dictionary.dictionary = null ;
	}
	
	/**
	 * Clear this
	 */
	public void clearTable() {
		this.wordLookup.clear();
	}

	/**
	 * Returns array of phonemes
	 * For now: returns first pronunciation (irrespective of context)
	 * could add in POS?
	 */
	public String[] getPhonemes(String word) {
		String[] ret;
		String tmp;
		String[] tmpArr;
		String[] info = (String[])wordLookup.get(word); //get info
		if (info==null) { //not in table
			return null;
		}
		tmp = info[info.length - 1]; //phonemes are LAST entry
		tmpArr = tmp.split(":"); //alt pronunciations separated by colons
		tmp = tmpArr[0]; //just taking first pronunciation for now, irrespective of context
		ret = tmp.split(" "); //store phonemes into array to return
		return ret;
	}

	/**
	 * Returns part of speech symbol for 'word'
	 * For now: returns first POS entered (irrespective of context)
	 * NO LONGER USED; HEPTAG INSTEAD
	 */
	//public String getPOS(String word) {
		/**
		* Noun N
		* Adjective J
		* Verb V
		* Adverb A
		* Preposition R
		* Conjunction C
		* Pronoun U
		* Interjection I
		* Past Participle P
		* Other O
		* Unmarked ?
		**/
	//	String[] info = (String[])wordLookup.get(word);
		//String ret = info[2];
	//	if (info==null) {
	//		System.out.println("Word not found: *" + word + "*");
	//		return "?";
	//	}
	//	return info[2]; //WTYPE
	//}
}
