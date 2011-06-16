package pt.inevo.encontra.webapp.loader;

import pt.inevo.encontra.index.IndexedObject;
import pt.inevo.encontra.index.annotation.Indexed;
import pt.inevo.encontra.storage.CmisObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class CmisIndexedObject extends IndexedObject<String, BufferedImage> implements CmisObject {

    private BufferedImage image;

    @Indexed
    public BufferedImage getBufferedImage(){
        return image;
    }

    @Override
    public Map<String, Object> getProperties() {
        return new HashMap<String, Object>();
    }

    @Override
    public byte[] getContent() {
        return new byte[0];  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void setContent(byte[] bytes) {
        InputStream in = new ByteArrayInputStream(bytes);
        try {
            BufferedImage bImageFromConvert = ImageIO.read(in);
            setValue(bImageFromConvert);
            image = bImageFromConvert;
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return;
    }
}
