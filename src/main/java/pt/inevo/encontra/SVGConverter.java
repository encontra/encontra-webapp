package pt.inevo.encontra;

import org.apache.batik.dom.AbstractDocument;
import org.apache.batik.dom.svg.SVGDOMImplementation;
import org.apache.batik.transcoder.Transcoder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.JPEGTranscoder;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.batik.transcoder.image.TIFFTranscoder;
import org.apache.batik.util.SVGConstants;
import org.w3c.dom.Element;

import java.io.*;
import java.util.HashMap;
import java.util.Map;


public class SVGConverter {


    protected final TranscoderFactory factory = new TranscoderFactory();

    private static class TranscoderFactory {
        protected final static Map transcoders = new HashMap();

        /**
         * Create a transcoder for a specified MIME type.
         * @param mimeType The MIME type of the destination format
         * @return A suitable transcoder, or <code>null</code> if one cannot be found
         */
        public Transcoder createTranscoder(String mimeType) {
            Class transcoderClass = (Class) transcoders.get(mimeType);
            if (transcoderClass == null) {
                return null;
            } else {
                try {
                    return (Transcoder) transcoderClass.newInstance();
                } catch (Exception ex) {
                    return null;
                }
            }
        }

        /**
         * Add a mapping from the specified MIME type to a transcoder.
         * Note: The transcoder must have a no-argument constructor.
         * @param mimeType The MIME type of the Transcoder
         * @param transcoderClass The <code>Class</code> object for the Transcoder.
         */
        public void addTranscoder(String mimeType, Class transcoderClass) {
            transcoders.put(mimeType, transcoderClass);
        }

        /**
         * Remove the mapping from a specified MIME type.
         * @param mimeType The MIME type to remove from the mapping.
         */
        public void removeTranscoder(String mimeType) {
            transcoders.remove(mimeType);
        }
    }

    public SVGConverter() {

        // Add the default transcoders which come with Batik
        factory.addTranscoder("image/jpeg", JPEGTranscoder.class);
        factory.addTranscoder("image/jpg", JPEGTranscoder.class);
        factory.addTranscoder("image/png", PNGTranscoder.class);
        factory.addTranscoder("image/tiff", TIFFTranscoder.class);
    }
    public void convertToMimeType(String mimetype, InputStream input, OutputStream output) {
        // Create a JPEGTranscoder and set its quality hint.
        JPEGTranscoder t = new JPEGTranscoder();
        t.addTranscodingHint(JPEGTranscoder.KEY_QUALITY,new Float(.8));

        // Set the transcoder input and output.
        TranscoderInput tinput = new TranscoderInput(input);
        try {
            TranscoderOutput toutput = new TranscoderOutput(output);

            // Perform the transcoding.
            t.transcode(tinput, toutput);
            output.flush();
            output.close();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (TranscoderException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}
