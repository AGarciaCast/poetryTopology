import java.io.*;
import java.util.*;

public class WhitmanLineCombiner {
	//final static String SEPARATOR = "******";
	//final static String POET = "Walt Whitman";
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
				if (ln.length()>1 && ln.trim().length()>0 && ln.substring(0,1).trim().length()<=0) {
					//non-empty line, but starts w/ whitespace-->continuation of previous line
					String prev = text.lastElement();
					if (prev.endsWith("-") && !prev.endsWith("--")) {
						//word broken up between lines
						prev = prev.substring(0,prev.length()-1).concat(ln.trim());
					}
					else {
						//add a space to the end of prev, concat:
						prev = prev.concat(" ").concat(ln.trim());
					}
					text.set(text.size()-1,prev);
				}
				else {
					text.add(ln);
				}
				
				if (ln.length()>1 && ln.trim().length()>0 && ln.substring(0,1).trim().length()<=0
						&& !(ln.trim().substring(0,1).equals(ln.trim().substring(0,1).toLowerCase()))) {
					//Check these--sometimes actual line breaks, intentional indentation...(e.g. With the love of comrades in For You O Democracy)
					System.out.println(ln);
				}
			}

			br.close();

			//write output
			String foutname = args[0].substring(0,args[0].length()-4) + "_fmtd2.txt";
			BufferedWriter bw = new BufferedWriter(new FileWriter(foutname));
			for (int i=0;i<text.size();i++) {
				ln = (String)text.elementAt(i);
				bw.write(ln);
				bw.newLine();
				//System.out.println(ln);
			}
			bw.close();
		} catch(Exception e) {
			e.printStackTrace(System.out);
		}
	}
}