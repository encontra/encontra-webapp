package pt.inevo.encontra.webapp.engine;

import pt.inevo.encontra.CommonInfo;
import pt.inevo.encontra.common.DefaultResultProvider;
import pt.inevo.encontra.common.Result;
import pt.inevo.encontra.common.ResultSet;
import pt.inevo.encontra.common.ResultSetDefaultImpl;
import pt.inevo.encontra.descriptors.DescriptorExtractor;
import pt.inevo.encontra.drawing.DrawingFactory;
import pt.inevo.encontra.drawing.descriptors.TopogeoDescriptor;
import pt.inevo.encontra.drawing.descriptors.TopogeoDescriptorExtractor;
import pt.inevo.encontra.engine.AnnotatedIndexedObjectFactory;
import pt.inevo.encontra.engine.SimpleIndexedObjectFactory;
import pt.inevo.encontra.image.descriptors.CEDDDescriptor;
import pt.inevo.encontra.image.descriptors.DominantColorDescriptor;
import pt.inevo.encontra.image.descriptors.EdgeHistogramDescriptor;
import pt.inevo.encontra.image.descriptors.FCTHDescriptor;
import pt.inevo.encontra.index.IndexedObject;
import pt.inevo.encontra.index.IndexedObjectFactory;
import pt.inevo.encontra.index.IndexingException;
import pt.inevo.encontra.index.annotation.Indexed;
import pt.inevo.encontra.index.search.AbstractSearcher;
import pt.inevo.encontra.index.search.Searcher;
import pt.inevo.encontra.nbtree.index.BTreeIndex;
import pt.inevo.encontra.nbtree.index.ParallelNBTreeSearcher;
import pt.inevo.encontra.query.QueryParserNode;
import pt.inevo.encontra.query.QueryProcessorDefaultImpl;
import pt.inevo.encontra.query.QueryProcessorDefaultParallelImpl;
import pt.inevo.encontra.storage.IEntity;
import pt.inevo.encontra.storage.IEntry;
import pt.inevo.encontra.storage.JPAObjectStorage;
import pt.inevo.encontra.webapp.loader.DrawingModel;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.logging.Level;

public class WebAppEngine extends AbstractSearcher<DrawingModel> {

    private static Map<String, DescriptorExtractor> extractors;

    static {
        extractors = new HashMap<String, DescriptorExtractor>();
        extractors.put("CEDD", new CEDDDescriptor<IndexedObject>());
        extractors.put("DominantColor", new DominantColorDescriptor<IndexedObject>());
        extractors.put("EdgeHistogram", new EdgeHistogramDescriptor<IndexedObject>());
        extractors.put("FCTH", new FCTHDescriptor<IndexedObject>());
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
        protected Result<O> getResultObject(Result<IEntry> entryresult) {
            return new Result<O>((O)new IndexedObject(entryresult.getResultObject().getId(), entryresult.getResultObject().getValue()));
        }
    }

    class ImageIndexedObjectFactory extends AnnotatedIndexedObjectFactory {

        @Override
        protected List<IndexedObject> createObjects(List<IndexedObjectFactory.IndexedField> indexedFields) {
            List<IndexedObject> result = new ArrayList<IndexedObject>();
            for (IndexedObjectFactory.IndexedField field : indexedFields) {
                assert (field.id != null);
                if (!field.name.equals("image")) {
                    continue;
                } else {
                    Set<Map.Entry<String, DescriptorExtractor>> entries = extractors.entrySet();
                    for (Map.Entry<String, DescriptorExtractor> entry : entries) {
                        result.add(new IndexedObject(field.id, field.name + "." + entry.getKey(), field.object, field.boost));
                    }
                }
            }
            return result;
        }
    }

    public class ImageEngineQueryProcessor<E extends IEntity> extends QueryProcessorDefaultImpl<E> {

        public class ImageModel implements IEntity<Long> {

            private Long id;
            private BufferedImage image;

            @Override
            public Long getId() {
                return id;
            }

            @Override
            public void setId(Long id) {
                this.id = id;
            }

            @Indexed
            public BufferedImage getImage() {
                return image;
            }

            public void setImage(BufferedImage image) {
                this.image = image;
            }
        }

        @Override
        protected ResultSet processSIMILAR(QueryParserNode node, boolean top) {
            ResultSet result = new ResultSetDefaultImpl();

            ImageModel objectModel = new ImageModel();
            objectModel.setImage((BufferedImage) node.fieldObject);

            List resultsParts = new ArrayList<ResultSetDefaultImpl<E>>();
            List<IndexedObject> indexedObjects = null;
            try {
                indexedObjects = indexedObjectFactory.processBean(objectModel);

                for (IndexedObject obj : indexedObjects) {
                    Searcher s = searcherMap.get(obj.getName());
                    ResultSet res = s.search(createObjectSubQuery(node));
                    resultsParts.add(res);
                }

                return combiner.join(resultsParts, node.distinct, node.limit, node.criteria);

            } catch (IndexingException e) {
                e.printStackTrace();
            }

            return result;
        }

        @Override
        public boolean insert(E object) {
            ImageModel objectModel = new ImageModel();
            objectModel.setId((Long) object.getId());
            objectModel.setImage((BufferedImage) ((IndexedObject) object).getValue());

            try {
                List<IndexedObject> indexedObjects = indexedObjectFactory.processBean(objectModel);
                for (IndexedObject obj : indexedObjects) {
                    insertObject((E) obj);
                }
            } catch (IndexingException e) {
                //log the exception and return false, because there was an error indexing the object.
                logger.log(Level.SEVERE, "Could not insert the object. Possible reason " + e.getMessage());
                return false;
            }
            return true;
        }
    }

    private void initEngine() {
        //A searcher for the image content (using the selected descriptors)
        AbstractSearcher imageSearcher = new ImageSearchEngine();
        imageSearcher.setQueryProcessor(new ImageEngineQueryProcessor());
        imageSearcher.setResultProvider(new DefaultResultProvider());
        imageSearcher.getQueryProcessor().setIndexedObjectFactory(new ImageIndexedObjectFactory());

        Set<Map.Entry<String, DescriptorExtractor>> entries = extractors.entrySet();
        for (Map.Entry<String, DescriptorExtractor> entry : entries) {
            AbstractSearcher entrySearcher = new ParallelNBTreeSearcher();
            entrySearcher.setQueryProcessor(new QueryProcessorDefaultParallelImpl());
            entrySearcher.setResultProvider(new DefaultResultProvider());
            entrySearcher.setIndex(new BTreeIndex(CommonInfo.CONFIG_FILE_INDEX_PATH, "image." + entry.getKey() + "DBTree", TopogeoDescriptor.class));
            entrySearcher.setDescriptorExtractor(entry.getValue());

            imageSearcher.getQueryProcessor().setSearcher("image." + entry.getKey(), entrySearcher);
        }

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
