package pt.inevo.encontra.webapp.loader;

import pt.inevo.encontra.drawing.Drawing;
import pt.inevo.encontra.drawing.DrawingFactory;
import pt.inevo.encontra.index.annotation.Indexed;
import pt.inevo.encontra.storage.IEntity;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;
import java.io.IOException;

/**
 * Drawing model entity. Represents a SVG drawing to be indexed.
 */
@Entity
public class DrawingModel implements IEntity<Long> {

    @Id
    private Long id;
    private String filename;
    private String description;

    @Transient
    private Drawing drawing;

    public DrawingModel() {}

    public DrawingModel(String filename, String description, Drawing image) {
        this.filename = filename;
        this.description = description;
        this.drawing = image;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
    public Drawing getDrawing() {
        if (drawing == null) {
            try {
                drawing = DrawingFactory.getInstance().drawingFromSVG(this.getFilename());
            } catch (IOException e) {
                return null;
            }
        }
        return drawing;
    }

    public void setDrawing(Drawing image) {
        this.drawing = image;
    }

    @Override
    public String toString() {
        return "DrawingModel{"
                + "id=" + id
                + ", filename='" + filename
                + ", description='" + description
                + ", drawing='" + drawing.toString()
                + '}';
    }
}
