package pt.inevo.encontra;

import pt.inevo.encontra.util.Util;

/**
 * Common properties to be used in the EnContRA Web-application
 */
public class CommonInfo {

    public final static String CONFIG_FILE = "config.properties";
    public final static String CMIS_CONFIG_FILE = "cmis_config.properties";
    public final static String DATABASE_CONFIG_FILE = "databases.properties";
    public final static String CONFIG_FILE_INDEX_PROPERTY = "index";
    public final static String CONFIG_FILE_DESCRIPTORS_PROPERTY = "descriptors";
    public final static String CONFIG_FILE_DATABASE_PROPERTY = "database";
    public final static String CONFIG_FILE_INDEX_PATH_PROPERTY = "index_path";
    public static String CONFIG_FILE_INDEX_PATH = "./";

    public final static String[] DESCRIPTORS = new String[]{"CEDD", "ColorLayout", "Dominant Color",
            "EdgeHistogram", "FCTH", "Scalable Color", "Topogeo"};

    public final static String [] FILE_TYPES_VECTORIAL = {"svg"};
    public final static String [] FILE_TYPES_IMAGE = {"png", "jpg"};
    public final static String [] FILE_TYPES = Util.concat(FILE_TYPES_IMAGE, FILE_TYPES_VECTORIAL);

    public final static String EMPTY_PATH = "";
}
