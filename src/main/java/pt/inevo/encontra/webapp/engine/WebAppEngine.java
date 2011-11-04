package pt.inevo.encontra.webapp.engine;

import pt.inevo.encontra.CommonInfo;
import pt.inevo.encontra.common.DefaultResultProvider;
import pt.inevo.encontra.common.Result;
import pt.inevo.encontra.common.ResultSet;
import pt.inevo.encontra.descriptors.DescriptorExtractor;
import pt.inevo.encontra.drawing.DrawingFactory;
import pt.inevo.encontra.drawing.descriptors.TopogeoDescriptor;
import pt.inevo.encontra.drawing.descriptors.TopogeoDescriptorExtractor;
import pt.inevo.encontra.engine.AnnotatedIndexedObjectFactory;
import pt.inevo.encontra.engine.SimpleIndexedObjectFactory;
import pt.inevo.encontra.image.descriptors.*;
import pt.inevo.encontra.index.IndexedObject;
import pt.inevo.encontra.index.IndexedObjectFactory;
import pt.inevo.encontra.index.IndexingException;
import pt.inevo.encontra.index.search.AbstractSearcher;
import pt.inevo.encontra.index.search.Searcher;
import pt.inevo.encontra.nbtree.index.BTreeIndex;
import pt.inevo.encontra.nbtree.index.ParallelNBTreeSearcher;
import pt.inevo.encontra.query.*;
import pt.inevo.encontra.query.criteria.CriteriaBuilderImpl;
import pt.inevo.encontra.storage.IEntity;
import pt.inevo.encontra.storage.IEntry;
import pt.inevo.encontra.storage.JPAObjectStorage;
import pt.inevo.encontra.webapp.loader.DrawingModel;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.*;

public class WebAppEngine extends AbstractSearcher<DrawingModel> {

    private static Map<String, DescriptorExtractor> availableExtractors;
    private static Map<String, DescriptorExtractor> activeExtractors;
    private static Map<String, Searcher> activeSearchers;

    static {
        //initializing the required structures
        activeExtractors = new HashMap<String, DescriptorExtractor>();
        availableExtractors = new HashMap<String, DescriptorExtractor>();
        activeSearchers = new HashMap<String, Searcher>();

        availableExtractors.put("CEDD", new CEDDDescriptor<IndexedObject>());
        availableExtractors.put("DominantColor", new DominantColorDescriptor<IndexedObject>());
        availableExtractors.put("EdgeHistogram", new EdgeHistogramDescriptor<IndexedObject>());
        availableExtractors.put("FCTH", new FCTHDescriptor<IndexedObject>());
        availableExtractors.put("ScalableColor", new ScalableColorDescriptor<IndexedObject>());
        availableExtractors.put("ColorLayout", new ColorLayoutDescriptor<IndexedObject>());
    }

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

    public class ImageSearchEngine<O extends IEntity> extends AbstractSearcher<O> {
        @Override
        protected List<IndexedObject> getIndexedObjects(Object o) throws IndexingException {
            List<IndexedObject> idxObjects = new ArrayList<IndexedObject>();
            if (o instanceof IndexedObject) {
                IndexedObject obj = (IndexedObject) o;
                for (Map.Entry<String, Searcher> searcher : activeSearchers.entrySet()) {
                    idxObjects.add(new IndexedObject(obj.getId(), searcher.getKey(), obj.getValue(), obj.getBoost()));
                }
            } else {
                return super.getIndexedObjects(o);
            }
            return idxObjects;
        }

        @Override
        public ResultSet search(Query query) {

            CriteriaBuilderImpl cb = new CriteriaBuilderImpl();
            CriteriaQuery q = cb.createQuery();

            QueryParserNode node = queryProcessor.getQueryParser().parse(query);
            Set<Map.Entry<String, Searcher>> searchers = activeSearchers.entrySet();
            List<Predicate> subExpressions = new ArrayList<Predicate>();
            for (Map.Entry<String, Searcher> searcher : searchers) {
                subExpressions.add(cb.similar(searcher.getKey(), node.fieldObject));
            }

            ResultSet results = super.search(q.where(cb.or(subExpressions.toArray(new Predicate[]{}))).distinct(((CriteriaQuery) query).isDistinct()).limit(((CriteriaQuery) query).getLimit()));
            return results;
        }

        @Override
        protected Result<O> getResultObject(Result<IEntry> entryresult) {
            return new Result<O>((O) new IndexedObject(entryresult.getResultObject().getId(), entryresult.getResultObject().getValue()));
        }
    }

    class ImageIndexedObjectFactory extends AnnotatedIndexedObjectFactory {

        @Override
        protected List<IndexedObject> createObjects(List<IndexedObjectFactory.IndexedField> indexedFields) {
            List<IndexedObject> result = new ArrayList<IndexedObject>();
            for (IndexedObjectFactory.IndexedField field : indexedFields) {
                assert (field.id != null);
                if (!field.name.equals("image")) {
                    result.add(new IndexedObject(field.id, field.name, field.object, field.boost));
                } else {
                    Set<Map.Entry<String, DescriptorExtractor>> entries = activeExtractors.entrySet();
                    for (Map.Entry<String, DescriptorExtractor> entry : entries) {
                        result.add(new IndexedObject(field.id, field.name + "." + entry.getKey(), field.object, field.boost));
                    }
                }
            }
            return result;
        }
    }

    private void initEngine() {
        //A searcher for the image content (using the selected descriptors)
        AbstractSearcher imageSearcher = new ImageSearchEngine();
        imageSearcher.setQueryProcessor(new QueryProcessorDefaultImpl());
        imageSearcher.setResultProvider(new DefaultResultProvider());
        imageSearcher.setIndexedObjectFactory(new ImageIndexedObjectFactory());

        Set<Map.Entry<String, DescriptorExtractor>> entries = availableExtractors.entrySet();
        for (Map.Entry<String, DescriptorExtractor> entry : entries) {
            AbstractSearcher entrySearcher = new ParallelNBTreeSearcher();
            entrySearcher.setQueryProcessor(new QueryProcessorDefaultParallelImpl());
            entrySearcher.setResultProvider(new DefaultResultProvider());
            entrySearcher.setIndex(new BTreeIndex(CommonInfo.CONFIG_FILE_INDEX_PATH, "image." + entry.getKey() + "DBTree", entry.getValue().getClass()));
            entrySearcher.setDescriptorExtractor(entry.getValue());

            imageSearcher.setSearcher("image." + entry.getKey(), entrySearcher);
        }

        setSearcher("image", imageSearcher);

        //A searcher for the vectorial content (using the selected descriptors)
        AbstractSearcher vectorialSearcher = new ParallelNBTreeSearcher();
        vectorialSearcher.setQueryProcessor(new QueryProcessorDefaultParallelImpl());
        vectorialSearcher.setResultProvider(new DefaultResultProvider());
        vectorialSearcher.setIndex(new BTreeIndex(CommonInfo.CONFIG_FILE_INDEX_PATH, "webappVectorialDBTree", TopogeoDescriptor.class));

        TopogeoDescriptorExtractor topogeoDescriptorExtractor = new TopogeoDescriptorExtractor();
        vectorialSearcher.setDescriptorExtractor(topogeoDescriptorExtractor);
        setSearcher("drawing", vectorialSearcher);
    }

    /**
     * Initializes the necessary properties for the engine to work.
     */
    private void setupEngine() {
        setObjectStorage(new DrawingStorage());
        setQueryProcessor(new QueryProcessorDefaultImpl());
        setIndexedObjectFactory(new SimpleIndexedObjectFactory());
        getQueryProcessor().setTopSearcher(this);
        setResultProvider(new DefaultResultProvider());
    }

    /**
     * Sets the active descriptor extractors, by using the name of the descriptors.
     * @param extractors
     */
    public void setActiveExtractors(List<String> extractors) {
        //first clears the descriptor extractors
        activeExtractors.clear();
        activeSearchers.clear();
        //then adds the extractors to the active list, but also updates the active Searchers
        for (String extractorName: extractors) {
            activeExtractors.put(extractorName, availableExtractors.get(extractorName));
            if (!extractorName.equals("Topogeo"))
                activeSearchers.put("image." + extractorName, searcherMap.get("image." + extractorName));
            else activeSearchers.put("drawing", searcherMap.get("drawing"));
        }
    }

    @Override
    protected Result<DrawingModel> getResultObject(Result<IEntry> entryresult) {
        Object result = storage.get(entryresult.getResultObject().getId());
        return new Result<DrawingModel>((DrawingModel) result);
    }
}
