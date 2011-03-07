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
import com.vaadin.ui.*;
import com.vaadin.ui.AbstractSelect.Filtering;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Label;
import com.vaadin.ui.TextField;
import com.vaadin.ui.Window;
import com.vaadin.ui.Window.Notification;
import org.vaadin.peter.imagestrip.ImageStrip;
import pt.inevo.encontra.common.DefaultResultProvider;
import pt.inevo.encontra.convert.SVGConverter;
import pt.inevo.encontra.descriptors.CompositeDescriptorExtractor;
import pt.inevo.encontra.geometry.PolygonSet;
import pt.inevo.encontra.geometry.Polygon;
import pt.inevo.encontra.image.descriptors.*;
import pt.inevo.encontra.index.search.AbstractSearcher;
import pt.inevo.encontra.index.search.ParallelSimpleSearcher;
import pt.inevo.encontra.nbtree.index.ParallelNBTreeSearcher;
import pt.inevo.encontra.query.QueryProcessorDefaultParallelImpl;
import pt.inevo.encontra.query.criteria.StorageCriteria;
import pt.inevo.encontra.service.PolygonDetectionService;
import pt.inevo.encontra.service.impl.PolygonDetectionServiceImpl;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import javax.imageio.ImageIO;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import pt.inevo.encontra.descriptors.CompositeDescriptor;
import pt.inevo.encontra.engine.SimpleIndexedObjectFactory;
import pt.inevo.encontra.index.IndexedObject;
import pt.inevo.encontra.common.Result;
import pt.inevo.encontra.common.ResultSet;
import pt.inevo.encontra.index.SimpleIndex;
import pt.inevo.encontra.lucene.index.LuceneIndex;
import pt.inevo.encontra.nbtree.index.BTreeIndex;
import pt.inevo.encontra.query.CriteriaQuery;
import pt.inevo.encontra.query.Path;
import pt.inevo.encontra.query.criteria.CriteriaBuilderImpl;
import pt.inevo.encontra.storage.IEntity;
import pt.inevo.encontra.storage.IEntry;
import pt.inevo.encontra.storage.JPAObjectStorage;
import pt.inevo.encontra.webapp.loader.ImageLoaderActor;
import pt.inevo.encontra.webapp.loader.ImageModel;
import pt.inevo.encontra.webapp.loader.ImageModelLoader;
import pt.inevo.encontra.webapp.loader.Message;
import scala.Option;

public class EnContRAApplication extends Application {

    public class ImageStorage extends JPAObjectStorage<Long, ImageModel> {

        EntityManagerFactory emf = Persistence.createEntityManagerFactory("manager");
        EntityManager em = emf.createEntityManager(); // Retrieve an application managed entity manager

        public ImageStorage() {
            super();
            setEntityManager(em);
        }
    }

    public class WebAppEngine <O extends IEntity> extends AbstractSearcher<O> {

        @Override
        protected Result<O> getResultObject(Result<IEntry> entryresult) {
            Object result = storage.get(Long.parseLong(entryresult.getResultObject().getId().toString()));
            return new Result<O>((O) result);
        }
    }

    private WebAppEngine<ImageModel> e = new WebAppEngine<ImageModel>();
    private String[] descriptors = new String[]{"CEDD", "ColorLayout", "Dominant Color",
            "EdgeHistogram", "FCTH", "Scalable Color"};
    private Window main = new Window("EnContRA");
    private SplitPanel horiz = new SplitPanel();
    private ImageUploader uploader;
    private ComboBox databaseSelector = new ComboBox("");
    private ComboBox indexSelector = new ComboBox();
    private TextField keywords;
    private com.vaadin.ui.Label logViewer = new com.vaadin.ui.Label();
    private HashMap<CheckBox, Slider> descriptorsUI = new HashMap<CheckBox, Slider>();
    private HashMap<ImageStrip.Image, ImageModel> resultImages = new HashMap<ImageStrip.Image, ImageModel>();
    final Map<String, String> databases = new HashMap<String, String>();
    private static Properties props = new Properties();
    private ImageStorage storage;

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

        indexSelector.setCaption("Choose the desired index:");
        indexSelector.addItem("Btree Index");
        indexSelector.addItem("Lucene Index");
        indexSelector.addItem("Simple Index");
        indexSelector.setFilteringMode(Filtering.FILTERINGMODE_OFF);
        indexSelector.setImmediate(true);

        configLayout.addComponent(indexSelector);

        configLayout.addComponent(new com.vaadin.ui.Label("Choose the descriptors to be used:"));

        for (String feat : descriptors) {
            HorizontalLayout hl = new HorizontalLayout();
            hl.setSpacing(true);
            CheckBox cb = new CheckBox(feat);
            cb.setDescription(feat + " Descriptor");
            cb.setEnabled(true);
            cb.setImmediate(true);
            cb.addListener(new Button.ClickListener() {

                public void buttonClick(ClickEvent event) {
                    boolean enabled = event.getButton().booleanValue();
                    Slider s = descriptorsUI.get((CheckBox) event.getButton());
                    s.setEnabled(enabled);
                }
            });

            final Slider slider = new Slider("Weight");
            slider.setWidth(100, Slider.UNITS_PIXELS);
            slider.setMin(0);
            slider.setMax(100);
            try {
                slider.setValue(100.0);
            } catch (Slider.ValueOutOfBoundsException ex) {
                ex.printStackTrace();
            }
            slider.setImmediate(true);
            slider.setEnabled(false);

            hl.addComponent(cb);
            hl.addComponent(slider);
            hl.setComponentAlignment(slider, Alignment.TOP_CENTER);

            descriptorsUI.put(cb, slider);

            configLayout.addComponent(hl);
        }

        Button applyNewConfiguration = new Button("Apply");
        applyNewConfiguration.setImmediate(true);
        applyNewConfiguration.addListener(new Button.ClickListener() {

            public void buttonClick(ClickEvent event) {

                setupEngine(true);
            }
        });

        configLayout.addComponent(applyNewConfiguration);

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
                ResultSet<ImageModel> results = knnQuery(file);

                ImageStrip strip = setupImageStrip();
                resultHolder.addComponent(strip);
                strip.addListener(new Property.ValueChangeListener() {

                    @Override
                    public void valueChange(ValueChangeEvent event) {
                        final ImageStrip.Image img = (ImageStrip.Image) event.getProperty().getValue();
                        ImageModel selectedModel = resultImages.get(img);

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

                        HorizontalLayout categoryLayout = new HorizontalLayout();
                        Label categoryLabel = new Label();
                        categoryLabel.setCaption("Category: ");
                        categoryLabel.setValue(selectedModel.getCategory());
                        categoryLayout.addComponent(categoryLabel);

                        v.addComponent(fileNameLayout);
                        v.addComponent(categoryLayout);

                        if (horiz.getSecondComponent() != null){
                            horiz.removeComponent(horiz.getSecondComponent());
                        }
                        horiz.addComponent(v);
                    }
                });

                resultImages.clear();
                for (Result<ImageModel> r : results) {
                    ImageStrip.Image img = strip.addImage(new FileResource(new File(r.getResultObject().getFilename()), EnContRAApplication.this));
                    //to keep track of which image was selected and display it later
                    resultImages.put(img, r.getResultObject());
                }
            }
        });
        h.addComponent(searchButton);

        root.addComponent(h);

        loadConfig();

        setTheme("mytheme");
    }

    //resets the second component of the horizontal panel
    private void resetQBEHorizPanel(){
        if (horiz.getSecondComponent() != null){
            horiz.removeComponent(horiz.getSecondComponent());
            VerticalLayout v = new VerticalLayout();
            v.addComponent(new com.vaadin.ui.Label("Select an image from above to display it here."));
            v.setMargin(new Layout.MarginInfo(true, true, true, true));
            horiz.addComponent(v);
            horiz.requestRepaint();
        }
    }

    private void setupEngine(boolean load){
        if (indexSelector.getValue() == null || databaseSelector.getValue() == null) {
            main.showNotification("You must select a database, an index to be used and at least one descriptor type.",
                    Notification.TYPE_ERROR_MESSAGE);
            return;
        }

        Properties p = new Properties();

        System.out.println("Configuring the Retrieval Engine...");
        storage = new ImageStorage();
        e.setObjectStorage(storage);
        e.setQueryProcessor(new QueryProcessorDefaultParallelImpl());
        e.getQueryProcessor().setIndexedObjectFactory(new SimpleIndexedObjectFactory());
        e.getQueryProcessor().setTopSearcher(e);
        e.setResultProvider(new DefaultResultProvider());

        //A searcher for the image content (using the selected descriptors)
        AbstractSearcher imageSearcher = null;
        if (indexSelector.getValue().equals("Lucene Index"))
            imageSearcher = new ParallelSimpleSearcher();
        else imageSearcher = new ParallelNBTreeSearcher();
        imageSearcher.setQueryProcessor(new QueryProcessorDefaultParallelImpl());
        imageSearcher.setResultProvider(new DefaultResultProvider());

        //getting the descriptors
        CompositeDescriptorExtractor compositeImageDescriptorExtractor = new CompositeDescriptorExtractor(IndexedObject.class, null);

        String descriptors = "";
        Set<Entry<CheckBox, Slider>> features = descriptorsUI.entrySet();
        for (Entry<CheckBox, Slider> pair : features) {
            if (pair.getKey().getCaption().contains("CEDD") && pair.getKey().booleanValue()) {
                descriptors += "CEDD,";
                compositeImageDescriptorExtractor.addExtractor(new CEDDDescriptor<IndexedObject>(), Double.parseDouble(pair.getValue().getValue().toString()) / 100);
            } else if (pair.getKey().getCaption().contains("ColorLayout") && pair.getKey().booleanValue()) {
                descriptors += "ColorLayout,";
                compositeImageDescriptorExtractor.addExtractor(new ColorLayoutDescriptor<IndexedObject>(), Double.parseDouble(pair.getValue().getValue().toString()) / 100);
            } else if (pair.getKey().getCaption().contains("Dominant Color") && pair.getKey().booleanValue()) {
                descriptors += "Dominant Color,";
                compositeImageDescriptorExtractor.addExtractor(new DominantColorDescriptor<IndexedObject>(), Double.parseDouble(pair.getValue().getValue().toString()) / 100);
            } else if (pair.getKey().getCaption().contains("EdgeHistogram") && pair.getKey().booleanValue()) {
                descriptors += "EdgeHistogram,";
                compositeImageDescriptorExtractor.addExtractor(new EdgeHistogramDescriptor<IndexedObject>(), Double.parseDouble(pair.getValue().getValue().toString()) / 100);
            } else if (pair.getKey().getCaption().contains("FCTH") && pair.getKey().booleanValue()) {
                descriptors += "FCTH,";
                compositeImageDescriptorExtractor.addExtractor(new FCTHDescriptor<IndexedObject>(), Double.parseDouble(pair.getValue().getValue().toString()) / 100);
            } else if (pair.getKey().getCaption().contains("Scalable Color") && pair.getKey().booleanValue()) {
                descriptors += "Scalable Color,";
                compositeImageDescriptorExtractor.addExtractor(new ScalableColorDescriptor(), Double.parseDouble(pair.getValue().getValue().toString()) / 100);
            }
        }
        //take the "," from the last position
        descriptors = descriptors.substring(0, descriptors.length()-1);

        p.put(CommonInfo.CONFIG_FILE_DESCRIPTORS_PROPERTY, descriptors);

        imageSearcher.setDescriptorExtractor(compositeImageDescriptorExtractor);

        String index = "";
        if (indexSelector.getValue().equals("Btree Index")) { //using a BTreeIndex
            index = "Btree Index";
            imageSearcher.setIndex(new BTreeIndex("webappBTree", CompositeDescriptor.class));
        } else if (indexSelector.getValue().equals("Lucene Index")) { //using a LuceneIndex
            index = "Lucene Index";
            imageSearcher.setIndex(new LuceneIndex("LuceneIndex", CompositeDescriptor.class));
            imageSearcher.setDescriptorExtractor(compositeImageDescriptorExtractor);
        } else { //using a SimpleIndex
            index = "SimpleIndex";
            imageSearcher.setIndex(new SimpleIndex(CompositeDescriptor.class));
        }

        p.put(CommonInfo.CONFIG_FILE_INDEX_PROPERTY, index);

        p.put(CommonInfo.CONFIG_FILE_DATABASE_PROPERTY, databaseSelector.getValue());

        try {
            OutputStream out = new FileOutputStream(new File(CommonInfo.CONFIG_FILE));
            p.store(out, "EnContRAAplication configuration file");
            System.out.println("Properties file saved!");
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        e.getQueryProcessor().setSearcher("image", imageSearcher);

        System.out.println("Loading some objects to the test indexes...");

        if (load)
            load(databases.get(databaseSelector.getValue()));

        System.out.println("End of the loading phase...");
        main.showNotification("Database loading sucessfully finished!");
    }

    //load configuration file
    private void loadConfig(){
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

        String index = p.getProperty(CommonInfo.CONFIG_FILE_INDEX_PROPERTY);
        String [] descriptors = p.getProperty(CommonInfo.CONFIG_FILE_DESCRIPTORS_PROPERTY).split(",");
        String database = p.getProperty(CommonInfo.CONFIG_FILE_DATABASE_PROPERTY);

        databaseSelector.setValue(database);
        indexSelector.setValue(index);

        Set<CheckBox> checkBoxes = descriptorsUI.keySet();
        for (CheckBox check : checkBoxes) {
            for (String desc: descriptors) {
                if (desc.equals(check.getCaption())) {
                    check.setValue(true);
                    descriptorsUI.get(check).setEnabled(true);
                    break;
                }
            }
        }

        setupEngine(false);
    }

    //load the image database
    private void load(String databaseFolder) {
        System.out.println("Loading some objects to the test indexes...");
        ImageModelLoader loader = new ImageModelLoader(databaseFolder);
        loader.scan();

        ActorRef loaderActor = UntypedActor.actorOf(new UntypedActorFactory() {
            @Override
            public UntypedActor create() {
                return new ImageLoaderActor(e);
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
    private ResultSet<ImageModel> knnQuery(File file) throws IOException {
        System.out.println("Creating a knn query...");
        BufferedImage image = ImageIO.read(file);

        //Creating a combined query for the results
        CriteriaBuilderImpl cb = new CriteriaBuilderImpl();
        CriteriaQuery<ImageModel> criteriaQuery = cb.createQuery(ImageModel.class);

        //Create the Model/Attributes Path
        Path<ImageModel> model = criteriaQuery.from(ImageModel.class);
        Path imageModel = model.get("image");

        String storageQuery = "";
        String keywordsStr = keywords.getValue().toString();
        if (!keywordsStr.equals("")) {
            String [] keywordsSplit = keywordsStr.split(",");
            for (int i = 0; i < keywordsSplit.length ; i++) {
                storageQuery += "category like '%" + keywordsSplit[i].toLowerCase() + "%' ";
                if (i+1 < keywordsSplit.length)
                    storageQuery += "and ";
            }
        }

        CriteriaQuery query = cb.createQuery().where(
                cb.similar(imageModel, image)).distinct(true).limit(10);
        ResultSet<ImageModel> results = null;
        if (!keywordsStr.equals("")) {
            query.setCriteria(new StorageCriteria(storageQuery));
        }

        results = e.search(query);
        printResults(results);
        System.out.println("...done! Query returned: " + results.getSize() + " results.");
        return results;
    }

    //print the results
    private void printResults(ResultSet<ImageModel> set){
        System.out.println("Results:");
        for (Result<ImageModel> r: set){
            System.out.println(r);
        }
    }
}
