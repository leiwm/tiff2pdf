import java.io.*;

import com.lowagie.text.pdf.RandomAccessFileOrArray;
import com.lowagie.text.pdf.codec.TiffImage;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.Document;


public class Tiff2Pdf {


    public static void main(String[] args) {
	
	boolean success = tiff2pdf("tiff_spool/fax.tiff", "pdf_spool");

	if (success)
	    System.out.println("Yes!");
	else
	    System.out.println("No!");	    

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
     * @param src_path Path to TIFF file to be converted
     * @param dst_path Path to destination directory
     * @return true on success, false otherwise
     */
    public static boolean tiff2pdf(String src_path, String dst_path) {
	
	try {

	    RandomAccessFileOrArray src = new RandomAccessFileOrArray(src_path);
	    int number_of_pages = TiffImage.getNumberOfPages(src);
	    Document dst = new Document();
	    dst.setPageSize(PageSize.A1);
	    PdfWriter.getInstance(dst, new FileOutputStream(dst_path + "/output.pdf"));
	    dst.open();
	    
	    for (int i = 1; i <= number_of_pages; i++) {
		Image temp = TiffImage.getTiffImage(src, i);
		dst.add(temp);
	    }
	    
	    dst.close();
	}
	
	catch (Exception i1) {
	    return false;
	}

	return true;

    }


    /**
     *
     * @param src_path Path to file to be archived
     * @param dst_path Path to archive destination directory
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