package pt.inevo.encontra;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import javax.imageio.ImageIO;
import pt.inevo.encontra.util.FileUtil;

/**
 * A ImageModel loader.
 * @author Ricardo
 */
public class ImageModelLoader implements Iterable<File> {

        protected String imagesPath = "";
        protected static Long idCount = 0l;
        protected java.util.List<File> imagesFiles;

        public ImageModelLoader() {
        }

        public ImageModelLoader(String imagesPath) {
            this.imagesPath = imagesPath;
        }

        public static ImageModel loadImage(File image) {

            //for now only sets the filename
            ImageModel im = new ImageModel(image.getAbsolutePath(), "", null);
            im.setId(idCount++);

            //get the description
            //TO DO - load the description from here
            im.setDescription("Description: " + image.getAbsolutePath());

            //get the bufferedimage
            try {
                BufferedImage bufImg = ImageIO.read(image);
                im.setImage(bufImg);
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            return im;
        }

        public java.util.List<ImageModel> getImages(String path) {
            File root = new File(path);
            String[] extensions = {"jpg", "png"};

            java.util.List<File> imageFiles = FileUtil.findFilesRecursively(root, extensions);
            java.util.List<ImageModel> images = new ArrayList<ImageModel>();

            for (File f : imageFiles) {
                images.add(loadImage(f));
            }

            return images;
        }

        public void load(String path) {
            File root = new File(path);
            String[] extensions = {"jpg", "png"};

            imagesFiles = FileUtil.findFilesRecursively(root, extensions);
        }

        public void load() {
            load(imagesPath);
        }

        public java.util.List<ImageModel> getImages() {
            return getImages(imagesPath);
        }

        @Override
        public Iterator<File> iterator() {
            return imagesFiles.iterator();
        }
    }
