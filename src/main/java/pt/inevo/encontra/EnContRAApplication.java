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

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorFactory;
import akka.dispatch.Future;
import com.vaadin.Application;
import com.vaadin.addon.colorpicker.ColorPicker;
import com.vaadin.addon.colorpicker.events.ColorChangeEvent;
import com.vaadin.data.Property;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.terminal.FileResource;
import com.vaadin.ui.AbstractSelect.Filtering;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.*;
import com.vaadin.ui.Label;
import com.vaadin.ui.TextField;
import com.vaadin.ui.Window;
import com.vaadin.ui.Window.Notification;
import org.vaadin.peter.imagestrip.ImageStrip;
import pt.inevo.encontra.common.Result;
import pt.inevo.encontra.common.ResultSet;
import pt.inevo.encontra.common.ResultSetDefaultImpl;
import pt.inevo.encontra.convert.SVGConverter;
import pt.inevo.encontra.drawing.Drawing;
import pt.inevo.encontra.drawing.DrawingFactory;
import pt.inevo.encontra.geometry.Polygon;
import pt.inevo.encontra.geometry.PolygonSet;
import pt.inevo.encontra.query.CriteriaQuery;
import pt.inevo.encontra.query.Path;
import pt.inevo.encontra.query.criteria.CriteriaBuilderImpl;
import pt.inevo.encontra.query.criteria.StorageCriteria;
import pt.inevo.encontra.service.PolygonDetectionService;
import pt.inevo.encontra.service.impl.PolygonDetectionServiceImpl;
import pt.inevo.encontra.webapp.engine.WebAppEngine;
import pt.inevo.encontra.webapp.loader.DrawingLoaderActor;
import pt.inevo.encontra.webapp.loader.DrawingModel;
import pt.inevo.encontra.webapp.loader.DrawingModelLoader;
import pt.inevo.encontra.webapp.loader.Message;
import scala.Option;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

public class EnContRAApplication extends Application {

    private WebAppEngine e = new WebAppEngine();
    private Window main = new Window("EnContRA");
    private SplitPanel horiz = new SplitPanel();
    private ImageUploader uploader;
    private ComboBox databaseSelector = new ComboBox("");
    private ComboBox indexSelector = new ComboBox();
    private TextField keywords;
    private com.vaadin.ui.Label logViewer = new com.vaadin.ui.Label();
    private HashMap<CheckBox, CheckBox> descriptorsUI = new HashMap<CheckBox, CheckBox>();
    private HashMap<ImageStrip.Image, DrawingModel> resultImages = new HashMap<ImageStrip.Image, DrawingModel>();
    final Map<String, String> databases = new HashMap<String, String>();
    private static Properties props = new Properties();
    private static Logger log = Logger.getLogger(EnContRAApplication.class.toString());
    private CheckBox imageCheckbox, vectorialCheckbox;

    @Override
    public void init() {

        setMainWindow(main);

        final VerticalLayout root = new VerticalLayout();
        main.setContent(root);

        final SVGCanvas canvas = new SVGCanvas();
        // Create the color picker
        ColorPicker cp = new ColorPicker("Our ColorPicker", Color.BLACK);
        cp.addListener(new ColorPicker.ColorChangeListener() {

            @Override
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
        uploader = new ImageUploader();
        uploader.setHeight(400, ImageUploader.UNITS_PIXELS);

        final SplitPanel configPanel = new SplitPanel();
        configPanel.setMargin(true, true, true, true);
        configPanel.setOrientation(SplitPanel.ORIENTATION_HORIZONTAL);
        configPanel.setSplitPosition(50); // percent

        final VerticalLayout configLayout = new VerticalLayout();
        configLayout.setSpacing(true);
        configLayout.setMargin(true, true, true, true);

        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(CommonInfo.DATABASE_CONFIG_FILE);
        Properties p = new Properties();
        try {
            p.load(inputStream);
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        Iterator it = p.keySet().iterator();
        while (it.hasNext()) {
            final String key = (String) it.next();
            final String value = p.getProperty(key);

            databases.put(key, value);
        }

        databaseSelector.setCaption("Choose the desired database");
        for (Map.Entry<String, String> entry : databases.entrySet()) {
            final String key = entry.getKey();

            databaseSelector.addItem(key);
        }

        databaseSelector.setFilteringMode(Filtering.FILTERINGMODE_OFF);
        databaseSelector.setImmediate(true);

        configLayout.addComponent(databaseSelector);

//        indexSelector.setCaption("Choose the desired index:");
//        indexSelector.addItem("Btree Index");
//        indexSelector.addItem("Lucene Index");
//        indexSelector.addItem("Simple Index");
//        indexSelector.setFilteringMode(Filtering.FILTERINGMODE_OFF);
//        indexSelector.setImmediate(true);
//
//        configLayout.addComponent(indexSelector);

        configLayout.addComponent(new com.vaadin.ui.Label("Choose the descriptors to be used:"));

        for (String feat : CommonInfo.DESCRIPTORS) {
            HorizontalLayout hl = new HorizontalLayout();
            hl.setSpacing(true);
            CheckBox cb = new CheckBox(feat);
            cb.setDescription(feat + " Descriptor");
            cb.setEnabled(true);
            cb.setImmediate(true);
            cb.addListener(new Button.ClickListener() {

                public void buttonClick(ClickEvent event) {
                    boolean enabled = event.getButton().booleanValue();
//                    Slider s = descriptorsUI.get(event.getButton());
//                    s.setEnabled(enabled);
                }
            });

//            final Slider slider = new Slider("Weight");
//            slider.setWidth(100, Slider.UNITS_PIXELS);
//            slider.setMin(0);
//            slider.setMax(100);
//            try {
//                slider.setValue(100.0);
//            } catch (Slider.ValueOutOfBoundsException ex) {
//                ex.printStackTrace();
//            }
//            slider.setImmediate(true);
//            slider.setEnabled(false);
//
            hl.addComponent(cb);
//            hl.addComponent(slider);
//            hl.setComponentAlignment(slider, Alignment.TOP_CENTER);

//            descriptorsUI.put(cb, slider);
            descriptorsUI.put(cb, cb);

            configLayout.addComponent(hl);
        }

        Button applyNewConfiguration = new Button("Index");
        applyNewConfiguration.setImmediate(true);
        applyNewConfiguration.addListener(new Button.ClickListener() {

            public void buttonClick(ClickEvent event) {

                setupEngine(true);
            }
        });

        Button updateSearchDescriptors = new Button("Update Descriptors");
        updateSearchDescriptors.setImmediate(true);
        updateSearchDescriptors.addListener(new Button.ClickListener() {

            public void buttonClick(ClickEvent event) {

                setupEngine(false);
            }
        });

        configLayout.addComponent(applyNewConfiguration);
        configLayout.addComponent(updateSearchDescriptors);

        final VerticalLayout vr = new VerticalLayout();
        vr.setSpacing(true);
        vr.setMargin(true, true, true, true);

        vr.addComponent(logViewer);
        logViewer.setImmediate(true);
        logViewer.setHeight(350, TextField.UNITS_PIXELS);

        com.vaadin.ui.Button cleanLog = new com.vaadin.ui.Button("Clean");
        cleanLog.addListener(new Button.ClickListener() {

            @Override
            public void buttonClick(ClickEvent event) {
                logViewer.setValue("");
            }
        });
        vr.addComponent(cleanLog);

        configPanel.addComponent(configLayout);
        configPanel.addComponent(vr);

        // Add a horizontal SplitPanel to the lower area
        horiz.setOrientation(SplitPanel.ORIENTATION_HORIZONTAL);
        horiz.setSplitPosition(50); // percent
        horiz.setHeight(450, VerticalLayout.UNITS_PIXELS);

        VerticalLayout vl = new VerticalLayout();
        vl.setMargin(new Layout.MarginInfo(true, true, true, true));
        vl.addComponent(uploader);
        horiz.addComponent(vl);

        VerticalLayout v = new VerticalLayout();
        v.addComponent(new com.vaadin.ui.Label("Select an image from above to display it here."));
        v.setMargin(new Layout.MarginInfo(true, true, true, true));
        horiz.addComponent(v);

        final TabSheet tabsheet = new TabSheet();
        tabsheet.addTab(canvasLayout, "Sketch", null);
        tabsheet.addTab(horiz, "Picture", null);
        tabsheet.addTab(configPanel, "Configuration", null);

        root.addComponent(tabsheet);

        HorizontalLayout h = new HorizontalLayout();
        keywords = new TextField();
        keywords.setColumns(40);
        h.addComponent(keywords);

        final Button searchButton = new Button("Search");
        main.addComponent(searchButton);

        final HorizontalLayout resultHolder = new HorizontalLayout();
        resultHolder.setHeight(150, ImageStrip.UNITS_PIXELS);
        resultHolder.setWidth(100, ImageStrip.UNITS_PERCENTAGE);
        root.addComponent(resultHolder);

        //search button listener
        searchButton.addListener(new Button.ClickListener() {

            public void buttonClick(Button.ClickEvent clickEvent) {
                File file = null;

                if (tabsheet.getSelectedTab().equals(canvasLayout)) {  // SKETCH
                    String svg = canvas.getSVG();
                    try { //query by sketch
                        file = File.createTempFile("encontra", ".jpg");
                        new SVGConverter().convertToMimeType("image/jpeg", new ByteArrayInputStream(svg.getBytes()), new FileOutputStream(file));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } else {  // IMAGE
                    file = uploader.getFile();
                }

                if (file != null) {
                    try {
                        queryByExample(file);
                        resetQBEHorizPanel();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        main.showNotification("There was an error when performing the query, please re-try!",
                                Notification.TYPE_ERROR_MESSAGE);
                    }
                }
            }

            private void queryByExample(File file) throws IOException {
                //remove all previous results
                resultHolder.removeAllComponents();

                //get all the results
                ResultSet<DrawingModel> results = knnQuery(file);

                ImageStrip strip = setupImageStrip();
                resultHolder.addComponent(strip);
                strip.addListener(new Property.ValueChangeListener() {

                    @Override
                    public void valueChange(ValueChangeEvent event) {
                        final ImageStrip.Image img = (ImageStrip.Image) event.getProperty().getValue();
                        DrawingModel selectedModel = resultImages.get(img);

                        Embedded e = new Embedded("", new FileResource(new File(selectedModel.getFilename()), EnContRAApplication.this));
                        e.setHeight("250");
                        VerticalLayout v = new VerticalLayout();
                        v.setMargin(new Layout.MarginInfo(true, true, true, true));
                        v.setSpacing(true);
                        Button findSimilar = new Button("Find similar");
                        findSimilar.addListener(new Button.ClickListener() {

                            @Override
                            public void buttonClick(ClickEvent event) {
                                try {
                                    String filename = resultImages.get(img).getFilename();
                                    File f = new File(filename);
                                    uploader.setFile(f);
                                    queryByExample(f);
                                    resetQBEHorizPanel();

                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                    main.showNotification("There was an error when performing the query, please re-try!",
                                            Notification.TYPE_ERROR_MESSAGE);
                                }
                            }
                        });
                        v.addComponent(findSimilar);
                        v.addComponent(e);

                        HorizontalLayout fileNameLayout = new HorizontalLayout();
                        Label filenameLabel = new Label();
                        filenameLabel.setCaption("Filename: ");
                        filenameLabel.setValue(selectedModel.getFilename());
                        fileNameLayout.addComponent(filenameLabel);

//                        HorizontalLayout categoryLayout = new HorizontalLayout();
//                        Label categoryLabel = new Label();
//                        categoryLabel.setCaption("Category: ");
//                        categoryLabel.setValue(selectedModel.getCategory());
//                        categoryLayout.addComponent(categoryLabel);

                        v.addComponent(fileNameLayout);
//                        v.addComponent(categoryLayout);

                        if (horiz.getSecondComponent() != null) {
                            horiz.removeComponent(horiz.getSecondComponent());
                        }
                        horiz.addComponent(v);
                    }
                });

                resultImages.clear();
                for (Result<DrawingModel> r : results) {

                    System.out.println("Result Id:" + r.getResultObject().getId() + ", distance:" + r.getScore());

                    File tmpFile = File.createTempFile("tempFile" + r.getResultObject().getId(), ".png");
                    String filename = tmpFile.getAbsolutePath();
                    filename = filename.substring(0, filename.length() - 4);
//                   r.getResultObject().getDrawing().export(filename);
                    ImageIO.write(r.getResultObject().getImage(), "png", tmpFile);

                    ImageStrip.Image img = strip.addImage(new FileResource(tmpFile, EnContRAApplication.this));

                    //to keep track of which image was selected and display it later
                    resultImages.put(img, r.getResultObject());
                }
            }
        });
        h.addComponent(searchButton);

        HorizontalLayout hl = new HorizontalLayout();
        hl.setSpacing(true);
        imageCheckbox = new CheckBox("Image");
        imageCheckbox.setDescription("Use image Descriptors");
        imageCheckbox.setEnabled(true);
        imageCheckbox.setImmediate(true);
//        cb.addListener(new Button.ClickListener() {
//
//            public void buttonClick(ClickEvent event) {
//                boolean enabled = event.getButton().booleanValue();
//                Slider s = descriptorsUI.get(event.getButton());
//                s.setEnabled(enabled);
//            }
//        });

        h.addComponent(imageCheckbox);

        vectorialCheckbox = new CheckBox("Vectorial");
        vectorialCheckbox.setDescription("Use Vector Descriptors");
        vectorialCheckbox.setEnabled(true);
        vectorialCheckbox.setImmediate(true);

        h.addComponent(vectorialCheckbox);

        root.addComponent(h);

        loadConfig();

        setTheme("mytheme");
    }

    //resets the second component of the horizontal panel
    private void resetQBEHorizPanel() {
        if (horiz.getSecondComponent() != null) {
            horiz.removeComponent(horiz.getSecondComponent());
            VerticalLayout v = new VerticalLayout();
            v.addComponent(new com.vaadin.ui.Label("Select an image from above to display it here."));
            v.setMargin(new Layout.MarginInfo(true, true, true, true));
            horiz.addComponent(v);
            horiz.requestRepaint();
        }
    }

    private void setupEngine(boolean load) {
        if (databaseSelector.getValue() == null) {
            main.showNotification("You must select a database to be used.",
                    Notification.TYPE_ERROR_MESSAGE);
            return;
        }

        Properties p = new Properties();

        System.out.println("Configuring the Retrieval Engine...");

        List<String> descriptors = new ArrayList<String>();
        Set<Map.Entry<CheckBox, CheckBox>> features = descriptorsUI.entrySet();
        for (Map.Entry<CheckBox, CheckBox> pair : features) {
            if (pair.getKey().getCaption().contains("CEDD") && pair.getKey().booleanValue()) {
                descriptors.add("CEDD");
            } else if (pair.getKey().getCaption().contains("ColorLayout") && pair.getKey().booleanValue()) {
                descriptors.add("ColorLayout");
            } else if (pair.getKey().getCaption().contains("Dominant Color") && pair.getKey().booleanValue()) {
                descriptors.add("DominantColor");
            } else if (pair.getKey().getCaption().contains("EdgeHistogram") && pair.getKey().booleanValue()) {
                descriptors.add("EdgeHistogram");
            } else if (pair.getKey().getCaption().contains("FCTH") && pair.getKey().booleanValue()) {
                descriptors.add("FCTH");
            } else if (pair.getKey().getCaption().contains("Scalable Color") && pair.getKey().booleanValue()) {
                descriptors.add("ScalableColor");
            } else if (pair.getKey().getCaption().contains("Topogeo") && pair.getKey().booleanValue()) {
                descriptors.add("Topogeo");
            }
        }
        //setting the active descriptor extractors and the correspondent searchers
        e.setActiveExtractors(descriptors);

//        storage = new DrawingStorage(loadCmisConfig());
//        storage = new DrawingStorage();
//        e.setObjectStorage(storage);
//        e.setQueryProcessor(new QueryProcessorDefaultImpl());
//        e.getQueryProcessor().setIndexedObjectFactory(new SimpleIndexedObjectFactory());
//        e.getQueryProcessor().setTopSearcher(e);
//        e.setResultProvider(new DefaultResultProvider());

//        //A searcher for the image content (using the selected descriptors)
//        AbstractSearcher imageSearcher = null;
//        if (indexSelector.getValue().equals("Lucene Index"))
//            imageSearcher = new ParallelSimpleSearcher();
//        else imageSearcher = new ParallelNBTreeSearcher();
//        imageSearcher.setQueryProcessor(new QueryProcessorDefaultParallelImpl());
//        imageSearcher.setResultProvider(new DefaultResultProvider());
//
//        //getting the descriptors
//        CompositeDescriptorExtractor compositeImageDescriptorExtractor = new CompositeDescriptorExtractor(IndexedObject.class, null);
//
//        String descriptors = "";
//        Set<Entry<CheckBox, Slider>> features = descriptorsUI.entrySet();
//        for (Entry<CheckBox, Slider> pair : features) {
//            if (pair.getKey().getCaption().contains("CEDD") && pair.getKey().booleanValue()) {
//                descriptors += "CEDD,";
//                compositeImageDescriptorExtractor.addExtractor(new CEDDDescriptor<IndexedObject>(), Double.parseDouble(pair.getValue().getValue().toString()) / 100);
//            } else if (pair.getKey().getCaption().contains("ColorLayout") && pair.getKey().booleanValue()) {
//                descriptors += "ColorLayout,";
//                compositeImageDescriptorExtractor.addExtractor(new ColorLayoutDescriptor<IndexedObject>(), Double.parseDouble(pair.getValue().getValue().toString()) / 100);
//            } else if (pair.getKey().getCaption().contains("Dominant Color") && pair.getKey().booleanValue()) {
//                descriptors += "Dominant Color,";
//                compositeImageDescriptorExtractor.addExtractor(new DominantColorDescriptor<IndexedObject>(), Double.parseDouble(pair.getValue().getValue().toString()) / 100);
//            } else if (pair.getKey().getCaption().contains("EdgeHistogram") && pair.getKey().booleanValue()) {
//                descriptors += "EdgeHistogram,";
//                compositeImageDescriptorExtractor.addExtractor(new EdgeHistogramDescriptor<IndexedObject>(), Double.parseDouble(pair.getValue().getValue().toString()) / 100);
//            } else if (pair.getKey().getCaption().contains("FCTH") && pair.getKey().booleanValue()) {
//                descriptors += "FCTH,";
//                compositeImageDescriptorExtractor.addExtractor(new FCTHDescriptor<IndexedObject>(), Double.parseDouble(pair.getValue().getValue().toString()) / 100);
//            } else if (pair.getKey().getCaption().contains("Scalable Color") && pair.getKey().booleanValue()) {
//                descriptors += "Scalable Color,";
//                compositeImageDescriptorExtractor.addExtractor(new ScalableColorDescriptor(), Double.parseDouble(pair.getValue().getValue().toString()) / 100);
//            } else if (pair.getKey().getCaption().contains("Topogeo") && pair.getKey().booleanValue()) {
//                descriptors += "Topogeo,";
//                compositeImageDescriptorExtractor.addExtractor(new TopogeoDescriptorExtractor(), Double.parseDouble(pair.getValue().getValue().toString()) / 100);
//            }
//        }
//        //take the "," from the last position
//        descriptors = descriptors.substring(0, descriptors.length()-1);
//
//        p.put(CommonInfo.CONFIG_FILE_DESCRIPTORS_PROPERTY, descriptors);
        p.put(CommonInfo.CONFIG_FILE_INDEX_PATH_PROPERTY, CommonInfo.CONFIG_FILE_INDEX_PATH);
//
//        imageSearcher.setDescriptorExtractor(compositeImageDescriptorExtractor);
//
//        String index = "";
//        if (indexSelector.getValue().equals("Btree Index")) { //using a BTreeIndex
//            index = "Btree Index";
//            imageSearcher.setIndex(new BTreeIndex(CommonInfo.CONFIG_FILE_INDEX_PATH, "webappBTree", CompositeDescriptor.class));
//        } else if (indexSelector.getValue().equals("Lucene Index")) { //using a LuceneIndex
//            index = "Lucene Index";
//            imageSearcher.setIndex(new LuceneIndex("LuceneIndex", CompositeDescriptor.class));
//            imageSearcher.setDescriptorExtractor(compositeImageDescriptorExtractor);
//        } else { //using a SimpleIndex
//            index = "SimpleIndex";
//            imageSearcher.setIndex(new SimpleIndex(CompositeDescriptor.class));
//        }
//
//        p.put(CommonInfo.CONFIG_FILE_INDEX_PROPERTY, index);
//
        p.put(CommonInfo.CONFIG_FILE_DATABASE_PROPERTY, databaseSelector.getValue());
//
        try {
            OutputStream out = new FileOutputStream(new File(CommonInfo.CONFIG_FILE));
            p.store(out, "EnContRAAplication configuration file");
            System.out.println("Properties file saved!");
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
//
//        e.getQueryProcessor().setSearcher("drawing", imageSearcher);

        System.out.println("Loading some objects to the test indexes...");

        if (load)
            load(databases.get(databaseSelector.getValue()));

        System.out.println("End of the loading phase...");
        main.showNotification("Engine setup completed!");
    }

    //load configuration file
    private void loadConfig() {
        InputStream inputStream = null;
        Properties p = new Properties();
        try {
            inputStream = new FileInputStream(new File(CommonInfo.CONFIG_FILE));
            p.load(inputStream);
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
            return;
        } catch (IOException e1) {
            e1.printStackTrace();
            return;
        }

//        String index = p.getProperty(CommonInfo.CONFIG_FILE_INDEX_PROPERTY);
        CommonInfo.CONFIG_FILE_INDEX_PATH = p.getProperty(CommonInfo.CONFIG_FILE_INDEX_PATH_PROPERTY);
//        String [] descriptors = p.getProperty(CommonInfo.CONFIG_FILE_DESCRIPTORS_PROPERTY).split(",");
        String database = p.getProperty(CommonInfo.CONFIG_FILE_DATABASE_PROPERTY);

        databaseSelector.setValue(database);
//        indexSelector.setValue(index);

//        Set<CheckBox> checkBoxes = descriptorsUI.keySet();
//        for (CheckBox check : checkBoxes) {
//            for (String desc: descriptors) {
//                if (desc.equals(check.getCaption())) {
//                    check.setValue(true);
//                    descriptorsUI.get(check).setEnabled(true);
//                    break;
//                }
//            }
//        }

        setupEngine(false);
    }

    /**
     * Loads the CMIS configuration file.
     *
     * @return
     */
    private Map<String, String> loadCmisConfig() {
        Properties properties = new Properties();
        Map<String, String> parameter = new HashMap<String, String>();
        try {
            properties.load(this.getClass().getClassLoader().getResourceAsStream(CommonInfo.CMIS_CONFIG_FILE));
            Enumeration e = properties.propertyNames();
            while (e.hasMoreElements()) {
                String propertyName = e.nextElement().toString();
                parameter.put(propertyName, properties.getProperty(propertyName));
            }
        } catch (IOException e1) {
            e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return parameter;
    }

    //load the image database
    private void load(String databaseFolder) {
        System.out.println("Loading some objects to the test indexes...");
        DrawingModelLoader loader = new DrawingModelLoader(databaseFolder);
        loader.scan();

        ActorRef loaderActor = UntypedActor.actorOf(new UntypedActorFactory() {
            @Override
            public UntypedActor create() {
                return new DrawingLoaderActor(e);
            }
        }).start();

        Message m = new Message();
        m.operation = "PROCESSALL";
        m.obj = loader;

        Future future = loaderActor.sendRequestReplyFuture(m, Long.MAX_VALUE, null);
        future.await();
        if (future.isCompleted()) {
            Option resultOption = future.result();
            if (resultOption.isDefined()) {
                Object result = resultOption.get();
                System.out.println("Database Processed: " + result);
            } else {
                System.out.println("There where an error processing the database!");
            }
        }
    }

    //setups the image strip
    private ImageStrip setupImageStrip() {
        final ImageStrip strip = new ImageStrip(ImageStrip.Alignment.HORIZONTAL);
        strip.setHeight(150, ImageStrip.UNITS_PIXELS);
        strip.setWidth(100, ImageStrip.UNITS_PERCENTAGE);
        strip.setAnimated(true);
        strip.setMaxAllowed(10);
        // Make strip to behave like select
        strip.setSelectable(true);
        //size of the surrounding boxes
        strip.setImageBoxWidth(150);
        strip.setImageBoxHeight(150);
        //max size of the images
        strip.setImageMaxWidth(135);
        strip.setImageMaxHeight(135);
        return strip;
    }

    //creates and performs a knn query to the engine
    private ResultSet<DrawingModel> knnQuery(File file) throws IOException {

        int mid = file.getName().lastIndexOf(".");
        String ext = file.getName().substring(mid+1,file.getName().length());

        System.out.println("Creating a knn query...");

        //detect if it is a svg file (native drawing, and try to read it properly)
        Drawing drawing = null;
        if (ext.equals("svg")) {
            drawing = DrawingFactory.getInstance().drawingFromSVG(file.getAbsolutePath());
        }

        //Creating a combined query for the results
        CriteriaBuilderImpl cb = new CriteriaBuilderImpl();
        CriteriaQuery<DrawingModel> criteriaQuery = cb.createQuery(DrawingModel.class);

        //Create the Model/Attributes Path
        Path<DrawingModel> model = criteriaQuery.from(DrawingModel.class);
        Path drawingPath = model.get("drawing");
        Path imagePath = model.get("image");

        String storageQuery = "";
        String keywordsStr = keywords.getValue().toString();
        if (!keywordsStr.equals("")) {
            String [] keywordsSplit = keywordsStr.split(",");
            for (int i = 0; i < keywordsSplit.length ; i++) {
                storageQuery += "filename like '%" + keywordsSplit[i].toLowerCase() + "%' ";
                if (i+1 < keywordsSplit.length)
                    storageQuery += "and ";
            }
        }


        //try to read the image if it was not a native drawing (svg file)
        BufferedImage img = null;
        try {
            if (drawing != null)
                img = drawing.getImage();
            else {
                img = ImageIO.read(file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        CriteriaQuery query = null;
        if (imageCheckbox.booleanValue() && vectorialCheckbox.booleanValue())  {
            query = cb.createQuery().where(
               cb.or(cb.similar(imagePath, img), cb.similar(drawingPath, drawing))).distinct(true).limit(20);
        } else if (imageCheckbox.booleanValue()) {
            query = cb.createQuery().where(
                cb.similar(imagePath, img)).distinct(true).limit(20);
        } else if (vectorialCheckbox.booleanValue()) {
            query = cb.createQuery().where(
                cb.similar(drawingPath, drawing)).distinct(true).limit(20);
        } else {
            main.showNotification("You must select at least a query type!",
                    Notification.TYPE_ERROR_MESSAGE);
            return new ResultSetDefaultImpl<DrawingModel>();
        }
//
        ResultSet<DrawingModel> results = null;
        if (!keywordsStr.equals("")) {
            query.setCriteria(new StorageCriteria(storageQuery));
        }

        results = e.search(query);
        System.out.println("...done! Query returned: " + results.getSize() + " results.");
        return results;
    }
}
