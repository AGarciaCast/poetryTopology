import java.io.*;
import java.util.Vector;

public class AddBreaks {
	final static String PRE_STRING1 = "POSITION: absolute; TOP: ", PRE_STRING2 = "position:absolute;top:";
	final static String TAG_CLOSE = ">";
	final static String BR = "<BR>";
	final static int REG_HEIGHT = 18 ;
	public static void main(String[] args) {
		//read file, add line breaks from TOP: positions
		BufferedReader br ;
		Vector<String> text;
		if (args.length<1) {
			System.out.println("usage: java AddBreaks <filename.htm>");
			return;
		}
		try {
			br = new BufferedReader(new FileReader(args[0]));
			text = new Vector<String>();
			String ln ;
			
			//read in all lines of the input file
			String sTmp ;
			int pos1=0, pos2=0, ind1, ind2, divInd, diff ;
			boolean addBreakFlag = false ;
			while ((ln=br.readLine()) != null) {
				//if need to add break, see if there's a > (end of the DIV tag) on this line
				if (addBreakFlag && (divInd=ln.indexOf(TAG_CLOSE))>=0) {
					divInd += TAG_CLOSE.length();
					//add in BR
					ln = ln.substring(0,divInd) + BR + ln.substring(divInd) ;
					addBreakFlag = false;
				}
				
				if (ln.contains(PRE_STRING1) || ln.contains(PRE_STRING2)) {
					String PRE_STRING = (ln.contains(PRE_STRING1))?PRE_STRING1:PRE_STRING2;
					String POS_END = (ln.contains(PRE_STRING1)) ? "px" : ";" ;
					//get position
					ind1 = ln.indexOf(PRE_STRING) + PRE_STRING.length() ;
					ind2 = ln.indexOf(POS_END,ind1);
					sTmp = ln.substring(ind1,ind2);
					try {
						pos2 = Integer.parseInt(sTmp);
					} catch(Exception e) {e.printStackTrace(System.out);}
					if (pos1!=0) {
						diff = pos2 - pos1 ;
						if (diff > REG_HEIGHT) {
							//add line break, or flag for next one
							if ((divInd=ln.indexOf(TAG_CLOSE,ind2))>=0) {
								divInd += TAG_CLOSE.length();
								//add in BR
								ln = ln.substring(0,divInd) + BR + ln.substring(divInd) ;
							}
							else {
								addBreakFlag = true;
							}
						}
					}
					pos1 = pos2;
				}
				//add
				text.add(ln);
			}
			
			br.close();
			
			//output
			String foutname = args[0].substring(0,args[0].length()-4) + "_fmtd.htm";
			BufferedWriter bw = new BufferedWriter(new FileWriter(foutname));
			for (int i=0;i<text.size();i++) {
				ln = (String)text.elementAt(i);
				bw.write(ln);
				bw.newLine();
				System.out.println(ln);
			}
			bw.close();
		} catch(Exception e2) {e2.printStackTrace(System.out);}
	}
}