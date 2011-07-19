package pt.inevo.encontra.webapp.loader;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorFactory;
import akka.dispatch.CompletableFuture;
import pt.inevo.encontra.index.search.Searcher;

import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An actor-based drawing loader.
 */
public class DrawingLoaderActor extends UntypedActor {

    protected DrawingModelLoader loader;
    protected ArrayList<ActorRef> producers;
    protected CompletableFuture future;
    protected Searcher e;
    protected int indexed = 0, notIndexed = 0;
    protected Logger log = Logger.getLogger(DrawingLoaderActor.class.toString());

    public DrawingLoaderActor() {
        producers = new ArrayList<ActorRef>();
    }

    public DrawingLoaderActor(Searcher searcher) {
        this();
        this.e = searcher;
    }

    public DrawingLoaderActor(DrawingModelLoader loader) {
        this();
        this.loader = loader;
    }

    @Override
    public void onReceive(Object o) throws Exception {
        if (o instanceof Message) {
            Message message = (Message) o;
            if (message.operation.equals("PROCESSALL")) {
                loader = (DrawingModelLoader) message.obj;

                for (int i = 0; i < 10; i++) {
                    ActorRef actor = UntypedActor.actorOf(new UntypedActorFactory() {

                        @Override
                        public UntypedActor create() {
                            return new DrawingLoaderActor(loader);
                        }
                    }).start();
                    producers.add(actor);
                }

                if (getContext().getSenderFuture().isDefined()) {
                    future = getContext().getSenderFuture().get();
                }

                for (ActorRef producer : producers) {
                    if (loader.hasNext()) {
                        Message m = new Message();
                        m.operation = "PROCESSONE";
                        m.obj = loader.next(); //set here the real object
                        producer.sendOneWay(m, getContext());
                    }
                }

            } else if (message.operation.equals("PROCESSONE")) {
                File f = (File) message.obj;
                DrawingModel model = loader.loadImage(f);

                Message answer = new Message();
                answer.operation = "ANSWER";
                answer.obj = model;
                getContext().replySafe(answer);
            } else if (message.operation.equals("ANSWER")) {
                DrawingModel model = (DrawingModel) message.obj;
                if (model != null) {    //save the object
                    try {
                        e.insert(model);
                        indexed++;
                        log.info("Drawing " + model.getFilename() + " successfully indexed!");
                        System.out.println();
                    } catch (Exception e) {
                        notIndexed++;
                        log.log(Level.SEVERE, "Couldn't insert the file " + model.getFilename() + ". Possible reason: " + e.toString());
                    }
                }

                if (loader.hasNext()) {
                    Message m = new Message();
                    m.operation = "PROCESSONE";
                    m.obj = loader.next(); //set here the real object
                    getContext().getSender().get().sendOneWay(m, getContext());
                } else {
                    Runtime r = Runtime.getRuntime();
                    long freeMem = r.freeMemory();
                    log.info("Free memory was: " + freeMem);
                    log.info("Indexed " + indexed + " items, from a total of " + (indexed + notIndexed) + "!");

                    if (future != null) {
                        future.completeWithResult(true);
                    }
                }
            }
        }
    }
}
