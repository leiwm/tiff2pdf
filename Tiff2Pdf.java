import java.io.*;
import java.util.regex.*;

import com.lowagie.text.pdf.RandomAccessFileOrArray;
import com.lowagie.text.pdf.codec.TiffImage;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.Document;


public class Tiff2Pdf {


    public static void main(String[] args) {
	
	File tiff_spool = new File("/home/jromero/Desktop/tiff2pdf/tiff_spool");
	File tiff_archive = new File("/home/jromero/Desktop/tiff2pdf/tiff_archive");
	File pdf_spool = new File("/home/jromero/Desktop/tiff2pdf/pdf_spool");

	String[] tiff_files = listDir(tiff_spool.getPath());
	for (String tiff_file : tiff_files) {
	    
	    if (tiff2Pdf(new File(tiff_spool, tiff_file), pdf_spool)) {

		archive(new File(tiff_spool, tiff_file), tiff_archive);

	    }

	}

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
		
		// Reading TIFF file
		RandomAccessFileOrArray tiff_file = new RandomAccessFileOrArray(src.getPath());
		// Getting number of pages of TIFF file
		int pages = TiffImage.getNumberOfPages(tiff_file);
		
		// Creating PDF file
		Document pdf_file = new Document(PageSize.A1);
		
		PdfWriter.getInstance(pdf_file,
				      new FileOutputStream(new File(dst.getPath(), pdf_name)));
		
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
	    
	    catch (Exception i1) {
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
	
	boolean success = src.renameTo(new File(dst, src.getName()));
	
	if (success)
	    return true;
	else
	    return false;
	
    }
    

}