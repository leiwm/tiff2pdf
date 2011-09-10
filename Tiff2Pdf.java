import java.io.File;
import java.io.FileOutputStream;

import com.lowagie.text.pdf.RandomAccessFileOrArray;
import com.lowagie.text.pdf.codec.TiffImage;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.Document;


public class Tiff2Pdf {


    public static void main(String[] args) {
	
	int convert = convertTiff("tiff_spool/fax.tiff", "pdf_spool");
	int move = archiveTiff("tiff_spool/fax.tiff", "tiff_archive");

	if (move != 0) {
	    
	    System.out.println("File was not successfully moved! ");

	}
	    
    }
    
    
    public static int convertTiff(String N_tiff_file, String N_pdf_spool_dir) {
	
	try {

	    RandomAccessFileOrArray tiff_file = new RandomAccessFileOrArray(N_tiff_file);
	    
	    int pages = TiffImage.getNumberOfPages(tiff_file);
	    
	    Document pdf_file = new Document();
	    
	    PdfWriter.getInstance(pdf_file, new FileOutputStream(String.format("%s/%s", N_pdf_spool_dir, N_tiff_file)));
	    
	    pdf_file.open();
	    
	    for(int i = 1; i <= pages; i++) {
		Image temp_image = TiffImage.getTiffImage(tiff_file, i);
		pdf_file.add(temp_image);
	    }
	    
	    pdf_file.close();
	}
	
	catch (Exception i1) {
	    
	    System.out.println("ERROR");
	    return 1;
	    
	}
	
	return 0;
	
    }
    
    
    public static int archiveTiff(String N_tiff_file, String N_tiff_archive_dir) {
	
	File tiff_file = new File(N_tiff_file);
	
	File tiff_archive_dir = new File(N_tiff_archive_dir);
	
	boolean success = tiff_file.renameTo(new File(tiff_archive_dir, tiff_file.getName()));
	
	if (!success) {
	    
	    return 1;
	    
	}
	
	return 0;
	
    }
    

}