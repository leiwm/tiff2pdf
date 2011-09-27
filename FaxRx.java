/*
 *
 *
 * Copyright (C) 2008-2009 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 */

package org.sipfoundry.faxrx;

import java.io.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.regex.*;
import java.util.Date;
import java.text.SimpleDateFormat;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.activation.MimetypesFileTypeMap;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import com.lowagie.text.pdf.RandomAccessFileOrArray;
import com.lowagie.text.pdf.codec.TiffImage;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.Document;

import org.apache.log4j.Logger;
import org.sipfoundry.commons.freeswitch.FaxReceive;
import org.sipfoundry.commons.freeswitch.FreeSwitchEventSocketInterface;
import org.sipfoundry.commons.freeswitch.Localization;
import org.sipfoundry.commons.freeswitch.Set;
import org.sipfoundry.commons.freeswitch.Sleep;
import org.sipfoundry.commons.userdb.User;
import org.sipfoundry.commons.userdb.ValidUsers;
import org.sipfoundry.commons.userdb.User.EmailFormats;
import org.sipfoundry.sipxivr.IvrConfiguration;
import org.sipfoundry.sipxivr.Mailbox;
import org.sipfoundry.sipxivr.RemoteRequest;
import org.sipfoundry.voicemail.EmailFormatter;

public class FaxRx {
    static final Logger LOG = Logger.getLogger("org.sipfoundry.sipxivr");

    // Global store for AutoAttendant resource bundles keyed by locale
    private static final String RESOURCE_NAME = "org.sipfoundry.attendant.AutoAttendant";
    private static HashMap<Locale, ResourceBundle> s_resourcesByLocale = new HashMap<Locale, ResourceBundle>();

    private IvrConfiguration m_ivrConfig;
    private FreeSwitchEventSocketInterface m_fses;
    private String m_localeString;
    private String m_mailboxid;
    private Localization m_loc;
    private Mailbox m_mailbox;

    /**
     *
     * @param ivrConfig top level configuration stuff
     * @param fses The FreeSwitchEventSocket with the call already answered
     * @param parameters The parameters from the sip URI (to determine locale and which Moh id to
     *        use)
     */
    public FaxRx(IvrConfiguration ivrConfig, FreeSwitchEventSocketInterface fses,
            Hashtable<String, String> parameters) {
        this.m_ivrConfig = ivrConfig;
        this.m_fses = fses;
        this.m_mailboxid = parameters.get("mailbox");

        // Look for "locale" parameter
        m_localeString = parameters.get("locale");
        if (m_localeString == null) {
            // Okay, try "lang" instead
            m_localeString = parameters.get("lang");
        }
    }

    /**
     * Load all the needed configuration.
     *
     */
    void loadConfig() {
        // Load the resources for the given locale.
        m_loc = new Localization(RESOURCE_NAME, m_localeString, s_resourcesByLocale, m_ivrConfig, m_fses);

    }

    public void run() {

        // run linger only for fax, otherwise we end up with hunged FS session
        m_fses.cmdResponse("linger");

        if (m_loc == null) {
            loadConfig();
        }

        // Wait a bit
        Sleep s = new Sleep(m_fses, 2000);
        s.go();

        receive();
    }

    private void sendIM(User user, String instantMsg) {
        URL sendIMUrl;

        String urlStr = IvrConfiguration.get().getSendIMUrl();
        if (urlStr == null) {
            return;
        }

        try {
            sendIMUrl = new URL(urlStr + "/" + user.getUserName() + "/sendFaxReceiveIM");

            RemoteRequest rr = new RemoteRequest(sendIMUrl, "text/plain", instantMsg);
            if (!rr.http()) {
                LOG.error("faxrx::sendIM Trouble with RemoteRequest " + rr.getResponse());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendEmail(String emailAddr, File emailAttachment, EmailFormatter emf, String faxSubject) {

        if (emailAddr == null) {
            return;
        }

        Properties props = System.getProperties();
        props.put("mail.smtp.host", "localhost");
        props.put("mail.smtp.user", "postmaster");
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage message = new MimeMessage(session);

        try {
            message.addRecipient(MimeMessage.RecipientType.TO, new InternetAddress(emailAddr));

            message.setFrom(new InternetAddress(emf.getSender()));

            message.setSubject(faxSubject, "UTF-8");

            MimeBodyPart faxBodyPart = new MimeBodyPart();

            DataSource dataSource = new FileDataSource(emailAttachment) {
                public String getContentType() {
		    MimetypesFileTypeMap mimeTypes = new MimetypesFileTypeMap();
		    mimeTypes.addMimeTypes("image/tiff tiff TIFF");
		    mimeTypes.addMimeTypes("application/pdf pdf PDF");
		    return mimeTypes.getContentType(emailAttachment);
                }
            };

            faxBodyPart.setDataHandler(new DataHandler(dataSource));
            faxBodyPart.setFileName(emailAttachment.getName());
            faxBodyPart.setHeader("Content-Transfer-Encoding", "base64");
            faxBodyPart.setDisposition(Part.ATTACHMENT);

            Multipart mpmixed = new MimeMultipart();
            mpmixed.addBodyPart(faxBodyPart);
            message.setContent(mpmixed);

            Transport.send(message);

        } catch (AddressException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (MessagingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void receive() {
        File faxPathName = null;
	File emailAttachment = null;
        FaxReceive faxReceive = null;
        String faxInfo;

        LOG.info("faxrx::Starting mailbox (" + m_mailbox + ") in locale " + m_loc.getLocale());

        User user = ValidUsers.INSTANCE.getUser(m_mailboxid);
        if (user == null) {
            LOG.error("FaxReceive: no user found for mailbox " + m_mailboxid);
            return;
        }

        user.setLocale(m_loc.getLocale());
        m_mailbox = new Mailbox(user);

        try {
            faxPathName = File.createTempFile("fax_" + timestamp() + "_", ".tiff");
            new Set(m_fses, "fax_enable_t38_request", "true").go();
            new Set(m_fses, "fax_enable_t38", "true").go();
            faxReceive = new FaxReceive(m_fses, faxPathName.getAbsolutePath());
            faxReceive.go();
        } 
	
	catch (IOException e) {
            e.printStackTrace();
            return;
        }

        finally {

	    // convert TIFF to PDF
	    File converted = tiff2Pdf(faxPathName);
	    if (converted != null) {
		// if conversion is succesful attach the PDF file
		emailAttachment = converted;
		faxPathName.delete();
	    }
	    else {
		// if conversion is not succesful attach the TIFF file
		emailAttachment = faxPathName;
		LOG.error("Fax Receive: Could not convert TIFF to PDF. TIFF will be attached");
	    }

            // construct a reasonable faxInfo string to be used as part of the email
            // subject and instant message. Send email even if faxReceive.rxSuccess() false
            // as there could be incomplete faxes - act as a fax machine
            String name = null;
            String number = null;

            EmailFormatter emf = EmailFormatter.getEmailFormatter(EmailFormats.FORMAT_BRIEF, m_ivrConfig, m_mailbox,
                    null);

            if (faxReceive.getRemoteStationId() != null) {
                name = faxReceive.getRemoteStationId();
            } else {
                if (!m_fses.getVariable("channel-caller-id-name").equals("unknown")) {
                    name = m_fses.getVariable("channel-caller-id-name");
                }
            }

            if (!m_fses.getVariable("channel-caller-id-number").equals("0000000000")) {
                number = m_fses.getVariable("channel-caller-id-number");
            }

            faxInfo = faxReceive.faxTotalPages() + " " + emf.fmt("page_fax_from") + " ";
            if (name != null) {
                faxInfo += name + " ";
            }

            if (number != null) {
                faxInfo += "(" + number + ")";
            }

            if (name == null && number == null) {
                faxInfo += emf.fmt("an_unknown_sender");
            }

            // need to send to at least one email address
            boolean sent = false;
            String faxSubject = emf.fmt("Your") + " " + faxInfo;

            if (user.getEmailFormat() != EmailFormats.FORMAT_NONE) {
                sendEmail(user.getEmailAddress(), emailAttachment, emf, faxSubject);
                sent = true;
            }

            if (user.getAltEmailFormat() != EmailFormats.FORMAT_NONE) {
                sendEmail(user.getAltEmailAddress(), emailAttachment, emf, faxSubject);
                sent = true;
            }

            // need to send to at least one email address so let's be more aggressive

            if (!sent) {
                if (user.getEmailAddress() != null) {
                    sendEmail(user.getEmailAddress(), emailAttachment, emf, faxSubject);
                    sent = true;
                }
            }

            if (!sent) {
                // need to send to at least one email address so let's be even more aggressive
                if (user.getAltEmailAddress() != null) {
                    sendEmail(user.getAltEmailAddress(), emailAttachment, emf, faxSubject);
                } else {
                    // didn't send anywhere !!
                    LOG.error("Fax Receive: No email address for user " + user.getUserName());
                }
            }

            if (faxReceive.rxSuccess()) {
                LOG.debug("Fax received successfully " + faxInfo);
                sendIM(user, emf.fmt("You_received_a") + " " + faxInfo + ".");
            } else {
                LOG.error("Fax receive failed from " + m_fses.getVariable("channel-caller-id-number")
                        + ". Error text: " + faxReceive.getResultText() + ". Error code: "
                        + faxReceive.getResultCode());
                sendIM(user, emf.fmt("You_received_an_incomplete") + " " + faxInfo + ".");
            }

            emailAttachment.delete();
        }
    }

    private File tiff2Pdf(File tiffFile) {
	
	Pattern pattern = Pattern.compile("(.*).tiff");
	Matcher matcher = pattern.matcher(tiffFile.getName());
	boolean matchFound = matcher.find();
	
	// check if tiffFile is actually a TIFF file, just in case
	if (matchFound) {
	    
	    // located at default tmp-file directory
	    File pdfFile = new File(System.getProperty("java.io.tmpdir"), matcher.group(1) + ".pdf");
	    
	    try {

		// read TIFF file
		RandomAccessFileOrArray tiff = new RandomAccessFileOrArray(tiffFile.getAbsolutePath());

		// get number of pages of TIFF file
		int pages = TiffImage.getNumberOfPages(tiff);

		// create PDF file
		Document pdf = new Document(PageSize.LETTER);
		
		PdfWriter.getInstance(pdf, new FileOutputStream(pdfFile));

		// open PDF filex
		pdf.open();
		
		// write PDF file page by page
		for (int page = 1; page <= pages; page++) {
		    Image temp = TiffImage.getTiffImage(tiff, page);
		    pdf.add(temp);
		}
		
		// close PDF file
		pdf.close();
	    }

	    catch (Exception e) {
		e.printStackTrace();		
		return null;
	    }
	    
	    return pdfFile;
	}
	
	else {
	    return null;
	}
    }

    private String timestamp() {
	SimpleDateFormat sdf = new SimpleDateFormat();
	sdf.applyPattern("yyyy-MM-dd-HH-mm-ss");
	return sdf.format(new Date());
    }

}
