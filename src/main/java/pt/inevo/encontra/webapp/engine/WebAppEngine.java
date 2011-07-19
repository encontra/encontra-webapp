package pt.inevo.encontra.webapp.engine;

import pt.inevo.encontra.CommonInfo;
import pt.inevo.encontra.common.DefaultResultProvider;
import pt.inevo.encontra.common.Result;
import pt.inevo.encontra.descriptors.CompositeDescriptor;
import pt.inevo.encontra.descriptors.CompositeDescriptorExtractor;
import pt.inevo.encontra.descriptors.Descriptor;
import pt.inevo.encontra.descriptors.MultiDescriptorExtractor;
import pt.inevo.encontra.drawing.Drawing;
import pt.inevo.encontra.drawing.descriptors.TopogeoDescriptor;
import pt.inevo.encontra.drawing.descriptors.TopogeoDescriptorExtractor;
import pt.inevo.encontra.engine.AnnotatedIndexedObjectFactory;
import pt.inevo.encontra.image.descriptors.*;
import pt.inevo.encontra.index.IndexedObject;
import pt.inevo.encontra.index.IndexedObjectFactory;
import pt.inevo.encontra.index.search.AbstractSearcher;
import pt.inevo.encontra.nbtree.index.BTreeIndex;
import pt.inevo.encontra.nbtree.index.ParallelNBTreeSearcher;
import pt.inevo.encontra.query.QueryProcessorDefaultImpl;
import pt.inevo.encontra.query.QueryProcessorDefaultParallelImpl;
import pt.inevo.encontra.storage.IEntry;
import pt.inevo.encontra.storage.JPAObjectStorage;
import pt.inevo.encontra.webapp.loader.DrawingModel;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WebAppEngine extends AbstractSearcher<DrawingModel>{

    /**
     * Internal WebApp Drawing Storage. Saves the drawings into a JPA storage.
     */
    private class DrawingStorage extends JPAObjectStorage<Long, DrawingModel> {

        EntityManagerFactory emf;
        EntityManager em;

        DrawingStorage() {
            super();

            emf = Persistence.createEntityManagerFactory("manager");
            em = emf.createEntityManager(); // Retrieve an application managed entity manager
            this.setEntityManager(em);

        }
    }

    // TODO not being used right now!
    private class DrawingCombinedDescriptorExtractor extends MultiDescriptorExtractor<IndexedObject<Long, Drawing>, Descriptor> {

        @Override
        protected List<Descriptor> extractDescriptors(IndexedObject<Long, Drawing> object) {
            return null;
        }
    }

    public class WebAppIndexedObjectFactory extends AnnotatedIndexedObjectFactory {

//        private Random random = new Random();

    @Override
    protected List<IndexedObject> createObjects(List<IndexedObjectFactory.IndexedField> indexedFields) {
        List<IndexedObject> result = new ArrayList<IndexedObject>();
        for (IndexedObjectFactory.IndexedField field : indexedFields) {
            assert(field.id != null);

            if (field.name.equals("drawing")) {
                BufferedImage img = null;
                try {
                    img = ((Drawing)field.object).getImage();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                result.add(new IndexedObject(field.id, "image", img, field.boost));
                result.add(new IndexedObject(field.id, field.name, field.object, field.boost));
            } else {
                result.add(new IndexedObject(field.id, field.name, field.object, field.boost));
            }
        }
        return result;
    }
}

    public WebAppEngine() {
        super();

        setupEngine();
        initEngine();
    }

    private void initEngine() {
        //A searcher for the image content (using the selected descriptors)
        AbstractSearcher imageSearcher = new ParallelNBTreeSearcher();
        imageSearcher.setQueryProcessor(new QueryProcessorDefaultParallelImpl());
        imageSearcher.setResultProvider(new DefaultResultProvider());
        imageSearcher.setIndex(new BTreeIndex(CommonInfo.CONFIG_FILE_INDEX_PATH, "webappImageDBTree", CompositeDescriptor.class));

        //getting the descriptors for image-based search
        CompositeDescriptorExtractor compositeImageDescriptorExtractor = new CompositeDescriptorExtractor(IndexedObject.class, null);
        compositeImageDescriptorExtractor.addExtractor(new CEDDDescriptor<IndexedObject>(), 1);
//        compositeImageDescriptorExtractor.addExtractor(new ColorLayoutDescriptor<IndexedObject>(), 1);
        compositeImageDescriptorExtractor.addExtractor(new DominantColorDescriptor<IndexedObject>(), 1);
        compositeImageDescriptorExtractor.addExtractor(new EdgeHistogramDescriptor<IndexedObject>(), 1);
        compositeImageDescriptorExtractor.addExtractor(new FCTHDescriptor<IndexedObject>(), 1);
//        compositeImageDescriptorExtractor.addExtractor(new ScalableColorDescriptor(), 1);

        imageSearcher.setDescriptorExtractor(compositeImageDescriptorExtractor);
        getQueryProcessor().setSearcher("image", imageSearcher);

        //A searcher for the vectorial content (using the selected descriptors)
        AbstractSearcher vectorialSearcher = new ParallelNBTreeSearcher();
        vectorialSearcher.setQueryProcessor(new QueryProcessorDefaultParallelImpl());
        vectorialSearcher.setResultProvider(new DefaultResultProvider());
        vectorialSearcher.setIndex(new BTreeIndex(CommonInfo.CONFIG_FILE_INDEX_PATH, "webappVectorialDBTree", TopogeoDescriptor.class));

        TopogeoDescriptorExtractor topogeoDescriptorExtractor = new TopogeoDescriptorExtractor();
        vectorialSearcher.setDescriptorExtractor(topogeoDescriptorExtractor);
        getQueryProcessor().setSearcher("drawing", vectorialSearcher);
    }

    /**
     * Initializes the necessary properties for the engine to work.
     */
    private void setupEngine() {
        setObjectStorage(new DrawingStorage());
        setQueryProcessor(new QueryProcessorDefaultImpl());
        getQueryProcessor().setIndexedObjectFactory(new WebAppIndexedObjectFactory());
        getQueryProcessor().setTopSearcher(this);
        setResultProvider(new DefaultResultProvider());
    }

    @Override
    protected Result<DrawingModel> getResultObject(Result<IEntry> entryresult) {
        Object result = storage.get(entryresult.getResultObject().getId());
        return new Result<DrawingModel>((DrawingModel) result);
    }
}
