import java.io.*;
import java.util.*;

public class WhitmanFormatter {
	final static String SEPARATOR = "******";
	final static String POET = "Walt Whitman";
	public static void main(String[] args) {
		BufferedReader br ;
		Vector<String> text;
		if (args.length<1) {
			System.out.println("usage: java WhitmanFormatter <filename.txt>");
			return;
		}

		try {
			br = new BufferedReader(new FileReader(args[0]));
			text = new Vector<String>();
			String ln ;
			
			//read in all lines of the input file
			boolean blanksFlag = false;
			while ((ln=br.readLine()) != null) {
				//trim END whitespace
				while (ln.trim().length()>0 && ln.substring(ln.length()-1).trim().length() == 0) {
					ln = ln.substring(0,ln.length()-1); //exclusive end index
				}
				
				if (blanksFlag&&ln.trim().length()==0) { //do nothing
					;
				}
				else if (ln.trim().length()==0) { //first blank line
					text.add(new String(""));
					blanksFlag = true;
				}
				else if (ln.startsWith("View Page")) {
					;//ignore
				}
				else if (ln.matches("^[0-9]+$")) { //delete section #'s
					;//ignore
				}
				else {
					text.add(ln);
					blanksFlag = false;
				}
			}

			br.close();

			//add ****** to line before title
			for (int i=0;i<text.size();i++) {
				ln = (String)text.elementAt(i);
				if (ln.equals(POET)) {
					text.add(i-1,SEPARATOR);
					i++; //skip the added line
				}
			}

			//write output
			String foutname = args[0].substring(0,args[0].length()-4) + "_fmtd.txt";
			BufferedWriter bw = new BufferedWriter(new FileWriter(foutname));
			for (int i=0;i<text.size();i++) {
				ln = (String)text.elementAt(i);
				bw.write(ln);
				bw.newLine();
				System.out.println(ln);
			}
			bw.close();
		} catch(Exception e) {
			e.printStackTrace(System.out);
		}
	}
}