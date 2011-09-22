import java.io.*;
import java.util.regex.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Date;
import java.text.SimpleDateFormat;

import com.lowagie.text.pdf.RandomAccessFileOrArray;
import com.lowagie.text.pdf.codec.TiffImage;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.Document;


public class Tiff2Pdf {


    public static void main(String[] args) {
	
	/* Code just meant to test Tiff2Pdf methods */

	File tiff_spool = new File("/home/jromero/Desktop/tiff2pdf/tiff_spool");
	File tiff_archive = new File("/home/jromero/Desktop/tiff2pdf/tiff_archive");
	File pdf_spool = new File("/home/jromero/Desktop/tiff2pdf/pdf_spool");
	
	ArrayList tiff_files = listDir(tiff_spool);
	Iterator iterator = tiff_files.iterator();
	
	try {
	    
	    while (iterator.hasNext()) {
		File current_file = (File) iterator.next();
		if (tiff2Pdf(new File(tiff_spool, current_file.toString()), pdf_spool)) {
		    archive(new File(tiff_spool, current_file.toString()), tiff_archive);
		}
	    }
	}
	catch (Exception ex) {
	    return;
	}
	
    }
    

    /**
     *
     * @param directory Path to directory to be listed
     * @return ArrayList of File instances
     */
    public static ArrayList listDir(File directory) {
	
	ArrayList<File> files = new ArrayList<File>();
	
	if (directory.isDirectory()) {
	    for (String file_name : directory.list()) {
		files.add(new File(file_name));
	    }
	}
	
	return files;
	
    }


    /**
     *
     * @param src Path to TIFF file to be converted
     * @param dst Path to destination directory
     * @return true on success, false otherwise
     */
    public static boolean tiff2Pdf(File src, File dst) {
	
	Pattern pattern = Pattern.compile("(.*).tiff");
	Matcher matcher = pattern.matcher(src.getName());
	boolean match_found = matcher.find();
	
	// Check if src is a TIFF file
	if (match_found) {
	    
	    // Keep name, change extension to .pdf
	    String pdf_name = matcher.group(1) + ".pdf";
	    
	    try {

		// Read TIFF file
		RandomAccessFileOrArray tiff_file = new RandomAccessFileOrArray(src.getPath());

		// Get number of pages of TIFF file
		int pages = TiffImage.getNumberOfPages(tiff_file);

		// Create PDF file
		Document pdf_file = new Document(PageSize.A1);
		
		PdfWriter.getInstance(pdf_file,
				      new FileOutputStream(new File(dst, pdf_name)));

		// Open PDF file
		pdf_file.open();
		
		// Write PDF file page by page
		for (int page = 1; page <= pages; page++) {
		    Image temp = TiffImage.getTiffImage(tiff_file, page);
		    pdf_file.add(temp);
		}
		
		// Close PDF file
		pdf_file.close();
	    }
	    catch (Exception ex) {
		return false;
	    }
	    
	    return true;
	}
	
	else {
	    return false;
	}
	
    }
    

    /**
     *
     * @param src Path to file to be archived
     * @param dst Path to archive directory
     * @return true on success, false otherwise
     */
    public static boolean archive(File src, File dst) {
	
	boolean success = false;
	
	try {
	    success = src.renameTo(new File(dst, src.getName()));
	}
	catch(NullPointerException ex) {
	    success =  false;
	}

	return success;

    }
    

    /**
     *
     * @return current timestamp following yyyy-MM-dd-HH-mm-ss pattern
     */
    public static String timestamp() {
	SimpleDateFormat sdf = new SimpleDateFormat();
	sdf.applyPattern("yyyy-MM-dd-HH-mm-ss");
	return sdf.format(new Date());
    }


}
