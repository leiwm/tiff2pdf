import java.io.*;


public class Tiff2Pdf {


    public static void main(String[] args) {
	
    }
    

    /**
     *
     * @param dir Path to directory to be listed
     * @return Array of strings with directory contents on success,
     *         null otherwise
     */
    public static String[] listDir(String dir_path) {
	
	File dir = new File(dir_path);

	return dir.list();

    }


    /**
     *
     * @param src Path to source file (file to be archived)
     * @param dst Path to destination directory (archive directory)
     * @return true on success, false otherwise
     */
    public static boolean archive(String src_path, String dst_path) {
	
	File src = new File(src_path);
	File dst = new File(dst_path);
	
	boolean success = src.renameTo(new File(dst, src.getName()));
	
	if (!success)
	    return false;
	
	return true;
	
    }
    

}