package pt.inevo.encontra.webapp.loader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.inevo.encontra.CommonInfo;
import pt.inevo.encontra.drawing.Drawing;
import pt.inevo.encontra.drawing.DrawingFactory;
import pt.inevo.encontra.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
* Loader for Objects of the type: ImageModel.
* @author Ricardo
*/
public class CmisImageModelLoader {

    protected String imagesPath = "";
    protected static Long idCount = 0l;
    protected List<File> imagesFiles;
    protected Iterator<File> it;
    protected HashMap<String, String> annotations = new HashMap<String, String>();
    protected Logger logger;

    public CmisImageModelLoader() {
        logger = LoggerFactory.getLogger(CmisImageModelLoader.class);
    }

    public CmisImageModelLoader(String imagesPath) {
        this();
        this.imagesPath = imagesPath;
    }

    public DrawingModel loadImage(File image) {

        //for now only sets the filename
        DrawingModel im = new DrawingModel(image.getAbsolutePath(), "", null);
        im.setId(idCount.toString());
        idCount++;

        //get the description
        String name = image.getParentFile().getName() + "/" + image.getName();
        im.setDescription(annotations.get(name));
        im.setCategory(im.getDescription());

        //get the bufferedimage
        try {
            System.out.println("Drawing from " + image.getName());
            Drawing drawing = DrawingFactory.getInstance().drawingFromSVG(image.getAbsolutePath());
            im.setDrawing(drawing);

//            BufferedImage bufImg = ImageIO.read(image);
//            im.setImage(bufImg);
        } catch (IOException ex) {
            logger.error("Couldn't load the picture: " + image.getName());
            return null;
        }

        return im;
    }

    public void scan() {
        scan(imagesPath);
    }

    public void scan(String path) {
//        try {
            File root = new File(path);
            String[] extensions = CommonInfo.FILE_TYPES;
            imagesFiles = FileUtil.findFilesRecursively(root, extensions);
            it = imagesFiles.iterator();
//            File annot = new File(path + "\\annotation.txt");
//            if (annot.exists()) {
//                BufferedReader reader = new BufferedReader(new FileReader(annot));
//                String line = "";
//                while ((line = reader.readLine()) != null) {
//                    String[] parts = line.split(" ");
//                    String[] name = parts[0].split("/");
////                    annotations.put(name[1] + ".jpg", line);
//                    annotations.put(parts[0] + ".jpg", line);
//                }
//            }
//        } catch (IOException ex) {
//            logger.error("Couldn't load the annotations for the model.");
//        }
    }

    public boolean hasNext() {
        return it.hasNext();
    }

    public File next() {
        return it.next();
    }
}
