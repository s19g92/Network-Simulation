import java.io.*;
import java.util.*;
import java.lang.*;

@SuppressWarnings("unused")
public class Reader {

	int count;
	String file;
	char neighbour;

	/** Creates a new instance of Reader */
	public Reader(String Filename, char n) {
		file = Filename;
		neighbour = n;
		count = 0;
	}

	/* ========= read output files and write into input files ========== */
	@SuppressWarnings("resource")
	char[] readFile() {
		char[] acutal_msg = null;
		File f = new File(file);
		if (f.exists() && !f.isDirectory()) {
			try {
				BufferedReader ReadFile = new BufferedReader(new FileReader(file));
				int temp = 0;
				String str;
				while ((str = ReadFile.readLine()) != null) {
					++temp;
					if (temp > count) /* new msg */
					{
						char[] msg_rcvd = str.toCharArray();
						acutal_msg = Arrays.copyOfRange(msg_rcvd, 2, 19);
						break;
					}
				}
				if(count < temp)
					count++;

			} catch (Exception e) {
				System.out.println(e + " in readFile()");
			}
			return acutal_msg;
		}
		return null;
	}
}
