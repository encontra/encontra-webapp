package pt.inevo.encontra.webapp.loader;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorFactory;
import akka.dispatch.CompletableFuture;
import pt.inevo.encontra.common.Result;
import pt.inevo.encontra.index.search.Searcher;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImageLoaderActor extends UntypedActor {

    protected ImageModelLoader loader;
    protected ArrayList<ActorRef> producers;
    protected CompletableFuture future;
    protected Searcher e;

    public ImageLoaderActor() {
        producers = new ArrayList<ActorRef>();
    }

    public ImageLoaderActor(Searcher searcher) {
        this();
        this.e = searcher;
    }

    public ImageLoaderActor(ImageModelLoader loader) {
        this();
        this.loader = loader;
    }

    @Override
    public void onReceive(Object o) throws Exception {
        if (o instanceof Message) {
            Message message = (Message) o;
            if (message.operation.equals("PROCESSALL")) {
                loader = (ImageModelLoader) message.obj;

                for (int i = 0; i < 10; i++) {
                    ActorRef actor = UntypedActor.actorOf(new UntypedActorFactory() {

                        @Override
                        public UntypedActor create() {
                            return new ImageLoaderActor(loader);
                        }
                    }).start();
                    producers.add(actor);
                }

                if (getContext().getSenderFuture().isDefined()) {
                    future = (CompletableFuture) getContext().getSenderFuture().get();
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
                ImageModel model = loader.loadImage(f);

                Message answer = new Message();
                answer.operation = "ANSWER";
                answer.obj = model;
                getContext().replySafe(answer);
            } else if (message.operation.equals("ANSWER")) {
                ImageModel model = (ImageModel) message.obj;
                if (model != null) {    //save the object
                    e.insert(model);
                    model.getImage().flush();
                    model.getImage().getGraphics().dispose();
                    model.setImage(null);
                }

                if (loader.hasNext()) {
                    Message m = new Message();
                    m.operation = "PROCESSONE";
                    m.obj = loader.next(); //set here the real object
                    getContext().getSender().get().sendOneWay(m, getContext());
                } else {
                    Runtime r = Runtime.getRuntime();
                    long freeMem = r.freeMemory();
                    System.out.println("Free memory was: " + freeMem);

                    if (future != null) {
                        future.completeWithResult(true);
                    }
                }
            }
        }
    }
}
