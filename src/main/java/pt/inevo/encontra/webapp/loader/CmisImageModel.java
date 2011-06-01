package pt.inevo.encontra.webapp.loader;

import pt.inevo.encontra.index.annotation.Indexed;
import pt.inevo.encontra.storage.CmisObject;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class CmisImageModel implements CmisObject {

    private String id;
    private String filename;
    private String description;
    private BufferedImage image;

    private String category;

    public CmisImageModel() {
    }

    public CmisImageModel(String filename, String description, BufferedImage image) {
        this.filename = filename;
        this.description = description;
        this.image = image;
    }

    @Indexed
    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Indexed
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Indexed
    public BufferedImage getImage() {
        return image;
    }

    public void setImage(BufferedImage image) {
        this.image = image;
    }

    public String getCategory(){
        return this.category;
    }

    public void setCategory(String category){
        this.category = category;
    }

    @Override
    public String toString() {
        return "TestModel{"
                + "id=" + id
                + ", filename='" + filename + '\''
                + ", content='" + description + '\''
                + ", category='" + category + '\''
                + '}';
    }

    public Map<String, Object> getProperties() {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("cmis:name", filename);
        properties.put("cmis:contentStreamFileName", category);
        properties.put("cmis:changeToken", description);
        return properties;
    }

    public byte[] getContent() {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ObjectOutputStream stream = new ObjectOutputStream(output);
            stream.writeUTF(id);
            stream.writeUTF(filename);
            stream.writeUTF((description == null)? "" : description);
            stream.writeUTF((category == null)? "" : category);
            stream.flush();

            byte [] content = output.toByteArray();
            return content;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new byte[1];
    }

    public void setContent(byte[] content) {
        try {
            ByteArrayInputStream input = new ByteArrayInputStream(content);
            ObjectInputStream stream = new ObjectInputStream(input);
            id = stream.readUTF();
            filename = stream.readUTF();
            description = stream.readUTF();
            category = stream.readUTF();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
