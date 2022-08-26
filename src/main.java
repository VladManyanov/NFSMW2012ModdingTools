import java.io.IOException;

public class main {

	private static String help = "NFS:MW (2012) Modding Tools by Hypercycle, v1"
			+ "\nProbably you are entered wrond command, please refer to the Readme file.";
	
	public static void main(String[] args) throws IOException {
		EventCarListEditor eventCarListEd = new EventCarListEditor();
		switch(args[0]) {
		case "unpack":
			eventCarListEd.unpackDatFile(args[1], args[2]); break;
		case "repack":
			eventCarListEd.writeDatFileOutput(args[1], args[2]); break;
		default:
			System.out.println(help); break;
		}
	}
}
