import java.io.*;
import java.util.*;

public class add_breaks {
	public static void main(String[] args) {
		BufferedReader br;
		Vector<String> text;
		try {
			br = new BufferedReader(new FileReader(args[0]));
			text = new Vector<String>();
			String ln ;
			
			//read in all lines of the input file
			ln = br.readLine();
			text.add(ln);
			while ((ln=br.readLine()) != null) {
				text.add(ln);
				text.add("******");
			}
			br.close();

			String foutname = args[0].substring(0,args[0].length()-4) + "_fmtd.txt";
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