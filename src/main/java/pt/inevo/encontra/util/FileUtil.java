package pt.inevo.encontra.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Object that can find all the files in a directory with a given extension.
 * @author Ricardo
 */
public class FileUtil {

    /**
     * Find all the files within a directory (or subdirectory) with a given extension.
     * @param directory the root of the directory we want to search
     * @param extensions the file extensions we are looking for
     * @return
     */
    public static List<File> findFilesRecursively(File directory, String[] extensions) {
        List<File> list = new ArrayList<File>();
        if (directory.isFile()) {
            if (hasExtension(directory, extensions)) {
                list.add(directory);
            }
            return list;
        }
        addFilesRecursevely(list, directory, extensions);
        return list;
    }

    private static boolean hasExtension(File f, String[] extensions) {
        int sz = extensions.length;
        String ext;
        String name = f.getName();
        for (int i = 0; i < sz; i++) {
            ext = (String) extensions[i];
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static void addFilesRecursevely(List<File> found, File rootDir, String[] extensions) {
        if (rootDir == null) {
            return; // we do not want waste time
        }
        File[] files = rootDir.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            File file = new File(rootDir, files[i].getName());
            if (file.isDirectory()) {
                addFilesRecursevely(found, file, extensions);
            } else {
                if (hasExtension(files[i], extensions)) {
                    found.add(file);
                }
            }
        }
    }
}
