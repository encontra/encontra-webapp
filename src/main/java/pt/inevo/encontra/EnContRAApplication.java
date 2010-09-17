/* 
 * Copyright 2009 IT Mill Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package pt.inevo.encontra;

import com.vaadin.Application;
import com.vaadin.addon.colorpicker.ColorPicker;
import com.vaadin.addon.colorpicker.events.ColorChangeEvent;
import com.vaadin.data.Property;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.terminal.FileResource;
import com.vaadin.ui.*;
import com.vaadin.ui.AbstractSelect.Filtering;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.TextField;
import com.vaadin.ui.Window;
import org.vaadin.peter.imagestrip.ImageStrip;
import pt.inevo.encontra.convert.SVGConverter;
import pt.inevo.encontra.geometry.PolygonSet;
import pt.inevo.encontra.geometry.Polygon;
import pt.inevo.encontra.service.PolygonDetectionService;
import pt.inevo.encontra.service.impl.PolygonDetectionServiceImpl;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;
import javax.imageio.ImageIO;
import pt.inevo.encontra.descriptors.CompositeDescriptor;
import pt.inevo.encontra.descriptors.CompositeDescriptorExtractor;
import pt.inevo.encontra.engine.Engine;
import pt.inevo.encontra.engine.SimpleEngine;
import pt.inevo.encontra.engine.SimpleIndexedObjectFactory;
import pt.inevo.encontra.image.descriptors.CEDDDescriptor;
import pt.inevo.encontra.image.descriptors.ColorLayoutDescriptor;
import pt.inevo.encontra.image.descriptors.DominantColorDescriptor;
import pt.inevo.encontra.image.descriptors.EdgeHistogramDescriptor;
import pt.inevo.encontra.image.descriptors.FCTHDescriptor;
import pt.inevo.encontra.image.descriptors.ScalableColorDescriptor;
import pt.inevo.encontra.index.IndexedObject;
import pt.inevo.encontra.index.Result;
import pt.inevo.encontra.index.ResultSet;
import pt.inevo.encontra.index.SimpleIndex;
import pt.inevo.encontra.lucene.index.LuceneIndex;
import pt.inevo.encontra.nbtree.index.BTreeIndex;
import pt.inevo.encontra.query.KnnQuery;
import pt.inevo.encontra.query.Query;
import pt.inevo.encontra.storage.SimpleObjectStorage;

public class EnContRAApplication extends Application {
    
    private Engine<ImageModel> e = new SimpleEngine<ImageModel>();

    private Window main = new Window("EnContRA");
    private ComboBox databaseSelector = new ComboBox("");
    private ComboBox indexSelector = new ComboBox();
    private TwinColSelect featuresSelector;
    private Property featureSelectorProperty;

    @Override
    public void init() {

        setMainWindow(main);

        final VerticalLayout root = new VerticalLayout();
        main.setContent(root);

        final SVGCanvas canvas = new SVGCanvas();
        // Create the color picker
        ColorPicker cp = new ColorPicker("Our ColorPicker", Color.BLACK);
        cp.addListener(new ColorPicker.ColorChangeListener() {

            public void colorChanged(ColorChangeEvent event) {
                canvas.setColor(event.getColor());
                getMainWindow().showNotification("Color changed!");
            }
        });

        final VerticalLayout canvasLayout = new VerticalLayout();
        canvasLayout.addComponent(cp);
        canvasLayout.addComponent(canvas);

        final PolygonDetectionService service = new PolygonDetectionServiceImpl();

        canvas.addListener(new Property.ValueChangeListener() {

            public void valueChange(Property.ValueChangeEvent valueChangeEvent) {
                SVGCanvas c = (SVGCanvas) valueChangeEvent.getProperty();
                String svg = c.getSVG();
                PolygonSet polygons = service.detectPolygons(svg);
                // draw polylines
                for (Polygon p : polygons) {
                    System.out.println(p.AsString(true, "#000000", "none"));
                }
            }
        });
        final ImageUploader uploader = new ImageUploader();

        final VerticalLayout configLayout = new VerticalLayout();
        configLayout.setSpacing(true);
        configLayout.setMargin(true, true, true, true);

        databaseSelector.setCaption("Choose the desired database");
        databaseSelector.addItem("Logos");
        databaseSelector.addItem("Landscapes");
        databaseSelector.addItem("Illustrations");
        databaseSelector.addItem("Monuments");
        databaseSelector.addItem("Models");
        databaseSelector.addItem("Photos");
        databaseSelector.addItem("All");
        databaseSelector.setFilteringMode(Filtering.FILTERINGMODE_OFF);
        databaseSelector.setImmediate(true);

        indexSelector.setCaption("Choose the desired index:");
        indexSelector.addItem("Btree Index");
        indexSelector.addItem("Lucene Index");
        indexSelector.addItem("Simple Index");
        indexSelector.setFilteringMode(Filtering.FILTERINGMODE_OFF);
        indexSelector.setImmediate(true);

        featuresSelector = new TwinColSelect("Please select the descriptors to use");
        featuresSelector.addItem("CEDD");
        featuresSelector.addItem("ColorLayout");
        featuresSelector.addItem("Dominant Color");
        featuresSelector.addItem("EdgeHistogram");
        featuresSelector.addItem("FCTH");
        featuresSelector.addItem("Scalable Color");
        featuresSelector.setRows(5);
        featuresSelector.setNullSelectionAllowed(false);
        featuresSelector.setMultiSelect(true);
        featuresSelector.setImmediate(true);
        featuresSelector.addListener(new Property.ValueChangeListener() {

            public void valueChange(ValueChangeEvent event) {
                if (!event.getProperty().toString().equals("[]")) {
                    System.out.println(event.getProperty());
                    featureSelectorProperty = event.getProperty();
                }
            }
        });


        Button applyNewConfiguration = new Button("Apply");
        applyNewConfiguration.setImmediate(true);
        applyNewConfiguration.addListener(new Button.ClickListener() {

            public void buttonClick(ClickEvent event) {

                System.out.println("Configuring the Retrieval Engine...");
                e.setObjectStorage(new SimpleObjectStorage(ImageModel.class));
                e.setIndexedObjectFactory(new SimpleIndexedObjectFactory());

                Runtime.getRuntime().gc();

                //A searcher for the image content (using only one type of descriptor
                SimpleImageSearcher imageSearcher = new SimpleImageSearcher();

                //getting the descriptors
                CompositeDescriptorExtractor compositeImageDescriptorExtractor = new CompositeDescriptorExtractor(IndexedObject.class, null);

                String[] selectedFeatures = featureSelectorProperty.toString().split(",");
                for (String feature : selectedFeatures) {
                    if (feature.contains("CEDD")) {
                        compositeImageDescriptorExtractor.addExtractor(new CEDDDescriptor<IndexedObject>(), 1);
                    } else if (feature.contains("ColorLayout")) {
                        compositeImageDescriptorExtractor.addExtractor(new ColorLayoutDescriptor<IndexedObject>(), 1);
                    } else if (feature.contains("Dominant Color")) {
                        compositeImageDescriptorExtractor.addExtractor(new DominantColorDescriptor<IndexedObject>(), 1);
                    } else if (feature.contains("EdgeHistogram")) {
                        compositeImageDescriptorExtractor.addExtractor(new EdgeHistogramDescriptor<IndexedObject>(), 1);
                    } else if (feature.contains("FCTH")) {
                        compositeImageDescriptorExtractor.addExtractor(new FCTHDescriptor<IndexedObject>(), 1);
                    } else if (feature.contains("Scalable Color")) {
                        compositeImageDescriptorExtractor.addExtractor(new ScalableColorDescriptor(), 1);
                    }
                }

                imageSearcher.setDescriptorExtractor(compositeImageDescriptorExtractor);

                if (indexSelector.getValue().equals("Btree Index")) { //using a BTreeIndex
                    imageSearcher.setIndex(new BTreeIndex(CompositeDescriptor.class));
                } else if (indexSelector.getValue().equals("Lucene Index")) { //using a LuceneIndex
                    imageSearcher.setIndex(new LuceneIndex("LuceneIndex", CompositeDescriptor.class));
                } else { //using a SimpleIndex
                    imageSearcher.setIndex(new SimpleIndex(CompositeDescriptor.class));
                }

                e.setSearcher(imageSearcher);

                System.out.println("Loading some objects to the test indexes...");

                ImageModelLoader loader = null;
                if (databaseSelector.getValue().equals("Photos")) {
                    loader = new ImageModelLoader("D:\\work\\ColaDI\\EnContRA\\encontra-index\\encontra-nbtree-index\\src\\test\\resources\\additional_images");
                } else if (databaseSelector.getValue().equals("Logos")) {
                    loader = new ImageModelLoader("D:\\work\\ColaDI\\EnContRA\\encontra-index\\encontra-nbtree-index\\src\\test\\resources\\BrandsAndLogos");
                } else if (databaseSelector.getValue().equals("Landscapes")) {
                    loader = new ImageModelLoader("D:\\work\\ColaDI\\EnContRA\\encontra-index\\encontra-nbtree-index\\src\\test\\resources\\Landscapes");
                } else if (databaseSelector.getValue().equals("Illustrations")) {
                    loader = new ImageModelLoader("D:\\work\\ColaDI\\EnContRA\\encontra-index\\encontra-nbtree-index\\src\\test\\resources\\DailyDrawings");
                } else if (databaseSelector.getValue().equals("Monuments")) {
                    loader = new ImageModelLoader("D:\\work\\ColaDI\\EnContRA\\encontra-index\\encontra-nbtree-index\\src\\test\\resources\\Monuments");
                } else if (databaseSelector.getValue().equals("Models")) {
                    loader = new ImageModelLoader("D:\\work\\ColaDI\\EnContRA\\encontra-index\\encontra-nbtree-index\\src\\test\\resources\\modelos");
                } else {
                    loader = new ImageModelLoader("D:\\work\\ColaDI\\EnContRA\\encontra-index\\encontra-nbtree-index\\src\\test\\resources");
                }

                loader.load();
                Iterator<File> it = loader.iterator();

                for (int i = 0; it.hasNext(); i++) {
                    try {
                        File f = it.next();
                        ImageModel im = loader.loadImage(f);
                        e.insert(im);
                    } catch (Exception e) {
                        System.out.println("Couldn't load the image, because: " + e.toString());
                        continue;
                    }
                }

                System.out.println("End of the loading phase...");
            }
        });

        configLayout.addComponent(databaseSelector);
        configLayout.addComponent(indexSelector);
        configLayout.addComponent(featuresSelector);
        configLayout.addComponent(applyNewConfiguration);

        final TabSheet tabsheet = new TabSheet();
        tabsheet.addTab(canvasLayout, "Sketch", null);
        tabsheet.addTab(uploader, "Picture", null);
        tabsheet.addTab(configLayout, "Configuration", null);

        root.addComponent(tabsheet);

        HorizontalLayout h = new HorizontalLayout();
        TextField keywords = new TextField();
        keywords.setColumns(40);
        h.addComponent(keywords);

        Button b = new Button("Search");
        main.addComponent(b);

        final ImageStrip strip = new ImageStrip(ImageStrip.Alignment.HORIZONTAL);
        strip.setHeight(200, ImageStrip.UNITS_PIXELS);
        strip.setImageHeight(200);
        strip.setImageWidth(200);
        root.addComponent(strip);

        b.addListener(new Button.ClickListener() {

            public void buttonClick(Button.ClickEvent clickEvent) {
                File file = null;
                strip.removeAllImages();

                if (tabsheet.getSelectedTab().equals(canvasLayout)) {  // SKETCH
                    String svg = canvas.getSVG();
                    try {
                        file = File.createTempFile("encontra", ".jpg");
                        new SVGConverter().convertToMimeType("image/jpeg", new ByteArrayInputStream(svg.getBytes()), new FileOutputStream(file));

                    } catch (IOException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }

                } else {  // IMAGE
                    file = uploader.getFile();
                }

                if (file != null) {
                    try {
                        System.out.println("Creating a knn query...");
                        BufferedImage image = ImageIO.read(file);

                        Query knnQuery = new KnnQuery(new IndexedObject<Serializable, BufferedImage>(28004, image), 10);
                        System.out.println("Searching for elements in the engine...");
                        ResultSet<ImageModel> results = e.search(knnQuery);

                        strip.setMaxAllowed(10);
                        strip.setAnimated(true);

                        int i = 0;
                        for (Result<ImageModel> r : results) {
                            strip.addImage(
                                    new FileResource(new File(r.getResult().getFilename()), EnContRAApplication.this), "image_" + i++);
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });
        h.addComponent(b);

        root.addComponent(h);

        setTheme("mytheme");
    }
}
