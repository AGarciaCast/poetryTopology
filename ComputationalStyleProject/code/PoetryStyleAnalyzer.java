/*
* Stand-alone application version of Applet
* Copyright (c) 2006, David M Kaplan
* 		javac PoetryStyleAnalyzer.java GUI.java
*		jar -cvfm PoetryStyleAnalyzer.jar srtmanifest.mf GUI*.class Dictionary.class Poem.class PoetryStyleAnalyzer*.class hepple\postag\*.class hepple\postag\rules\*.class Jama\*.class Jama/util/*.class
*		jarsigner -keypass 112358 -storepass 112358 -keystore keystore PoetryStyleAnalyzer.jar dkapcert
*		java PoetryStyleAnalyzer
*		java -mx120m PoetryStyleAnalyzer
*		java -verbose:gc PoetryStyleAnalyzer
*		jar -cvfm PoetryStyleAnalyzer.jar srtmanifest.mf GUI*.class Dictionary.class Poem.class PoetryStyleAnalyzer.class hepple\postag\*.class hepple\postag\rules\*.class Jama\*.class Jama/util/*.class hepple\postag\resources\*.txt ../poems/SRTPoetry/*.txt
*/

//import GUI ;
import javax.swing.JFrame;
import javax.swing.JApplet;
import java.applet.Applet;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Hashtable;
import java.io.File;
import java.awt.Dimension;

public class PoetryStyleAnalyzer extends JApplet {
	static JFrame jf;
	static GUI gui;
	static final boolean DEBUG_ON = true ;
	static boolean isApplet = false ;
	static PoetryStyleAnalyzer thisPSA = null ;
	
	public PoetryStyleAnalyzer() {super();PoetryStyleAnalyzer.thisPSA = this;}
	
	public static void main(String[] args) {
		//for now, ignore arguments
		for (int i=0;i<args.length;i++) {
			if (DEBUG_ON) {
				System.out.println("arg#"+i+"=*"+args[i]+"*");
			}
		}
		
		jf = new JFrame("American Poetry Style Analyzer");
		
		jf.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.out.println("Good-bye");
				//get rid of gui
				gui.stop();
				gui.destroy();
				jf.setVisible(false);
				jf = null;
				System.out.println("Thanks for using PoetryStyleAnalyzer!");
				if (!isApplet) {
					System.exit(0); //exit Java interpreter
				}
			}
		});

		jf.addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e) {
				//update GUI.screenWidth, etc.
				GUI.screenWidth = (int)gui.dPaneScrollPane.getSize().getWidth() - gui.dPaneScrollPane.getInsets().left - gui.dPaneScrollPane.getInsets().right ;
				GUI.screenHeight = (int)gui.dPaneScrollPane.getSize().getHeight() - gui.dPaneScrollPane.getInsets().top - gui.dPaneScrollPane.getInsets().bottom ;
				if (DEBUG_ON) {
					System.out.println("GUI="+GUI.screenWidth+"x"+GUI.screenHeight);
					System.out.print("jf.componentResized: updating dPane pSz from "+gui.dPane.getPreferredSize()+" to ");
				}
				gui.dPane.setPreferredSize(new Dimension(
					(int)Math.round(GUI.screenWidth*Poem.zoomFactor),
					(int)Math.round(GUI.screenHeight*Poem.zoomFactor)
					));
				if (DEBUG_ON) {
					System.out.println(gui.dPane.getPreferredSize());
				}
				gui.dPane.revalidate();
				gui.scalePoems(0); //rescale
				gui.updatePainting();
				gui.dPane.repaint();
			}
		});
		
		gui = new GUI(); //need to init()/start() too?

		if (GUI.appParams == null) {
			//set parameters
			GUI.appParams = new Hashtable<String,String>();

			GUI.appParams.put("dictionaryFile","../Dictionaries/merged2.dct");
			GUI.appParams.put("PoemObjFilename","");
			GUI.appParams.put("rulesFile","hepple/postag/resources/ruleset_1.txt");
			GUI.appParams.put("lexiconFile","hepple/postag/resources/lexicon_all_1.txt");
			GUI.appParams.put("contractionsFile","hepple/postag/resources/contractions.txt");
			GUI.appParams.put("PoemFilename","../poems/SRTPoetry/Oxfordanthology_fmtd.txt");
			GUI.appParams.put("weightsFile","../poems/SRTPoetry/weights_selective2.txt");

			for (int i=0;i<args.length;i++) {
				String[] a = args[i].split("=");
				if (GUI.appParams.get(a[0])!=null) {
					System.out.println("user-specified: "+a[0]+"="+a[1]);
				}
				else {
					System.out.println("Not valid parameter: "+a[0]+"; parameters are dictionaryFile, "+
							"PoemObjFilename, rulesFile, lexiconFile, contractionsFile, PoemFilename, and weightsFile");
				}
				GUI.appParams.put(a[0],a[1]);
			}
		}

		try {
			File f = new File(".").getAbsoluteFile();
			GUI.codeBase = f.toURL();
			if (DEBUG_ON) {
				System.out.println("codeBase="+GUI.codeBase);
			}
		} catch(Exception e) {
			System.out.println("Error initializing application:");
			e.printStackTrace(System.out);
		}

		try {
			gui.init();
			gui.start();
		}
		catch(Exception e) {
			e.printStackTrace(System.out);
			if (isApplet) {
				thisPSA.quitProgram();
			}
			else {
				System.exit(-1);
			}
		}

		gui.setPreferredSize(new Dimension(500,400));
		jf.add(gui);
		jf.pack();
		jf.setVisible(true);
		GUI.weightsFrame.requestFocus();
		jf.requestFocus();
	}

	/**
	 * Applet Init()
	 */
	public void init() {
		isApplet = true ;
		
		if (GUI.appParams == null) {
			GUI.appParams = new Hashtable<String,String>();
			
			//read parameters from HTML
			String dictFile, poemObjFilename, rulesFile, lexiconFile, contractionsFile, poemFilename, weightsFilename ;
			dictFile = getParameter("dictionaryFile");
			poemObjFilename = getParameter("PoemObjFilename");
			rulesFile = getParameter("rulesFile");
			lexiconFile = getParameter("lexiconFile");
			contractionsFile = getParameter("contractionsFile");
			poemFilename = getParameter("PoemFilename");
			weightsFilename = getParameter("weightsFile");
			
			//replace with defaults if not set
			dictFile = (dictFile==null || dictFile.trim().length()<1) ? "../Dictionaries/merged2.dct" : dictFile ;
			poemObjFilename = (poemObjFilename==null || poemObjFilename.trim().length()<1) ? "" : poemObjFilename ;
			rulesFile = (rulesFile==null || rulesFile.trim().length()<1) ? "hepple/postag/resources/ruleset_1.txt" : rulesFile ;
			lexiconFile = (lexiconFile==null || lexiconFile.trim().length()<1) ? "hepple/postag/resources/lexicon_all_1.txt" : lexiconFile ;
			contractionsFile = (contractionsFile==null || contractionsFile.trim().length()<1) ? "hepple/postag/resources/contractions.txt" : contractionsFile ;
			poemFilename = (poemFilename==null || poemFilename.trim().length()<1) ? "../poems/SRTpoetry/Oxfordanthology_fmtd.txt" : poemFilename ;
			weightsFilename = (weightsFilename==null || weightsFilename.trim().length()<1) ? "../poems/SRTpoetry/weights_selective2.txt" : weightsFilename ;
			
			//set parameters for GUI
			GUI.appParams.put("dictionaryFile",dictFile);
			GUI.appParams.put("PoemObjFilename",poemObjFilename);
			GUI.appParams.put("rulesFile",rulesFile);
			GUI.appParams.put("lexiconFile",lexiconFile);
			GUI.appParams.put("contractionsFile",contractionsFile);
			GUI.appParams.put("PoemFilename",poemFilename);
			GUI.appParams.put("weightsFile",weightsFilename);
		}

		PoetryStyleAnalyzer.main(new String[] {});
	}

	public void start() {
		//don't need to "start" anything, just init()
	}

	public void stop() {
		//not really any "ongoing" execution that needs to be stopped
	}
	
	public void destroy() {
		System.out.println("Destroy()");
	}
	
	public void quitProgram() {
		//free resources
		//get rid of gui
		gui.stop();
		gui.destroy();
		jf.setVisible(false);
		System.out.println("Thanks for using PoetryStyleAnalyzer!");
	}
	
	public String getAppletInfo() {
		return "Copyright (c) David Kaplan 2006. Created for senior thesis at Princeton.";
	}
}