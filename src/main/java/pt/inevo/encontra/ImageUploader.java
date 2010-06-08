package pt.inevo.encontra;


import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import com.vaadin.terminal.FileResource;
import com.vaadin.ui.*;

import javax.imageio.ImageIO;

public class ImageUploader extends CustomComponent
        implements Upload.SucceededListener,
        Upload.FailedListener,
        Upload.Receiver,
        Upload.StartedListener,
        Upload.ProgressListener,
        Upload.FinishedListener {
    Panel root;         // Root element for contained components.
    Panel imagePanel;   // Panel that contains the uploaded image.
    File  file;         // File to write to.
    private ProgressIndicator pi = new ProgressIndicator();
    private Upload upload;

    public ImageUploader() {
        root = new Panel("My Upload Component");
        setCompositionRoot(root);
        // Create the Upload component.

        final Upload upload = new Upload(null, this);
        upload.setImmediate(true);
        // Use a custom button caption instead of plain "Upload".
        upload.setButtonCaption("Upload Now");
        // Listen for events regarding the success of upload.
        upload.addListener((Upload.SucceededListener) this);
        upload.addListener((Upload.FailedListener) this);
        root.addComponent(upload);

        root.addComponent(pi);
        pi.setVisible(false);

        // Create a panel for displaying the uploaded image.
        imagePanel = new Panel("Uploaded image");
        imagePanel.addComponent(
                new Label("No image uploaded yet"));
        root.addComponent(imagePanel);

    }

    public void uploadStarted(Upload.StartedEvent event) {
        // This method gets called immediatedly after upload is started
        upload.setVisible(false);
        pi.setVisible(true);
        pi.setValue(0f);
        pi.setPollingInterval(500);
    }

    public void updateProgress(long readBytes, long contentLength) {
        // This method gets called several times during the update
        pi.setValue(new Float(readBytes / (float) contentLength));
    }

    public void uploadFinished(Upload.FinishedEvent event) {
        // This method gets called always when the upload finished,
        // either succeeding or failing
        pi.setVisible(false);
    }


    // Callback method to begin receiving the upload.
    public OutputStream receiveUpload(String filename,
                                      String MIMEType) {
        FileOutputStream fos = null; // Output stream to write to
        try {
            file = File.createTempFile("encontra-",filename);

            // Open the file for writing.
            fos = new FileOutputStream(file);
        } catch (final java.io.FileNotFoundException e) {
            // Error while opening the file. Not reported here.
            e.printStackTrace();
            return null;
        }catch (IOException e) {
            e.printStackTrace();
        }
        return fos; // Return the output stream to write to
    }
    // This is called if the upload is finished.
    public void uploadSucceeded(Upload.SucceededEvent event) {
        // Log the upload on screen.
        root.addComponent(new Label("File " + event.getFilename()
                + " of type '" + event.getMIMEType()
                + "' uploaded."));

        // Display the uploaded file in the image panel.
        final FileResource imageResource =
                new FileResource(file, getApplication());
        imagePanel.removeAllComponents();
        imagePanel.addComponent(new Embedded("", imageResource));

    }


    // This is called if the upload fails.
    public void uploadFailed(Upload.FailedEvent event) {
        // Log the failure on screen.
        root.addComponent(new Label("Uploading "
                + event.getFilename() + " of type '"
                + event.getMIMEType() + "' failed."));
    }

    public File getFile() {
        return file;
    }
    
    public BufferedImage getImage() {
        BufferedImage img=null;
        try {
            img=ImageIO.read(file);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return img;
    }

}