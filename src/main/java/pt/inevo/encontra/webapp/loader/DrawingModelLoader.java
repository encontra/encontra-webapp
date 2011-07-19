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
public class DrawingModelLoader {

    protected String imagesPath = "";
    protected static Long idCount = 0l;
    protected List<File> imagesFiles;
    protected Iterator<File> it;
    protected HashMap<String, String> annotations = new HashMap<String, String>();
    protected Logger logger;

    public DrawingModelLoader() {
        logger = LoggerFactory.getLogger(DrawingModelLoader.class);
    }

    public DrawingModelLoader(String imagesPath) {
        this();
        this.imagesPath = imagesPath;
    }

    public DrawingModel loadImage(File image) {

        //for now only sets the filename
        DrawingModel im = new DrawingModel(image.getAbsolutePath(), "", null);
        im.setId(idCount);
        idCount++;

        //get the description - if it exists
        String name = image.getParentFile().getName() + "/" + image.getName();
        im.setDescription(annotations.get(name));

        //load the drawing
        try {
            Drawing drawing = DrawingFactory.getInstance().drawingFromSVG(image.getAbsolutePath());
            im.setDrawing(drawing);
        } catch (IOException ex) {
            logger.error("Couldn't load the drawing: " + image.getName() + ".");
            return null;
        }

        logger.info("Drawing " + image.getName() + " successfully loaded.");

        return im;
    }

    public void scan() {
        scan(imagesPath);
    }

    public void scan(String path) {
        //detect if a path was specified
        if (path.equals(CommonInfo.EMPTY_PATH)) {
            logger.info("No path specified, so assuming it is the current directory!");
            path = "./";
        }

        File root = new File(path);
        String[] extensions = CommonInfo.FILE_TYPES;
        imagesFiles = FileUtil.findFilesRecursively(root, extensions);
        it = imagesFiles.iterator();
    }

    public boolean hasNext() {
        return it.hasNext();
    }

    public File next() {
        return it.next();
    }
}
