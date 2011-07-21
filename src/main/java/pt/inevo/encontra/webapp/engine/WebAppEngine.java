package pt.inevo.encontra.webapp.engine;

import pt.inevo.encontra.CommonInfo;
import pt.inevo.encontra.common.DefaultResultProvider;
import pt.inevo.encontra.common.Result;
import pt.inevo.encontra.descriptors.CompositeDescriptor;
import pt.inevo.encontra.descriptors.CompositeDescriptorExtractor;
import pt.inevo.encontra.drawing.DrawingFactory;
import pt.inevo.encontra.drawing.descriptors.TopogeoDescriptor;
import pt.inevo.encontra.drawing.descriptors.TopogeoDescriptorExtractor;
import pt.inevo.encontra.engine.SimpleIndexedObjectFactory;
import pt.inevo.encontra.image.descriptors.*;
import pt.inevo.encontra.index.IndexedObject;
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

public class WebAppEngine extends AbstractSearcher<DrawingModel> {

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

    public WebAppEngine() {
        super();

        setupEngine();
        initEngine();
        DrawingFactory.getInstance().setSimplified(true);
    }

    private void initEngine() {
        //A searcher for the image content (using the selected descriptors)
        AbstractSearcher imageSearcher = new ParallelNBTreeSearcher();
        imageSearcher.setQueryProcessor(new QueryProcessorDefaultParallelImpl());
        imageSearcher.setResultProvider(new DefaultResultProvider());
        imageSearcher.setIndex(new BTreeIndex(CommonInfo.CONFIG_FILE_INDEX_PATH, "webappImageDBTree", CompositeDescriptor.class));

//        getting the descriptors for image-based search
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
        getQueryProcessor().setIndexedObjectFactory(new SimpleIndexedObjectFactory());
        getQueryProcessor().setTopSearcher(this);
        setResultProvider(new DefaultResultProvider());
    }

    @Override
    protected Result<DrawingModel> getResultObject(Result<IEntry> entryresult) {
        Object result = storage.get(entryresult.getResultObject().getId());
        return new Result<DrawingModel>((DrawingModel) result);
    }
}
