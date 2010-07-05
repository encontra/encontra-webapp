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

package pt.inevo.encontra.gwt.client.ui;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.*;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.Composite;
import com.vaadin.terminal.gwt.client.ApplicationConnection;
import com.vaadin.terminal.gwt.client.Console;
import com.vaadin.terminal.gwt.client.Paintable;
import com.vaadin.terminal.gwt.client.UIDL;
import org.vaadin.gwtgraphics.client.DrawingArea;
import org.vaadin.gwtgraphics.client.shape.Path;


public class VSVGCanvas extends Composite implements Paintable, MouseDownHandler, MouseMoveHandler, MouseUpHandler {

    Console log=ApplicationConnection.getConsole();
    private DrawingArea canvas;

    private String color="#000000";

    private int width = 400;
    private int height = 400;

    /** Set the CSS class name to allow styling. */
    public static final String CLASSNAME = "svgcanvas";

    /** Component identifier in UIDL communications. */
    String uidlId;

    /** Reference to the server connection object. */
    ApplicationConnection client;

    private Path path=null;

    /**
     * The constructor should first call super() to initialize the component and
     * then handle any initialization relevant to Vaadin.
     */
    public VSVGCanvas() {
        super();
        AbsolutePanel panel = new AbsolutePanel();


        canvas = new DrawingArea(width, height);

        canvas.addMouseDownHandler(this);

        canvas.addMouseMoveHandler(this);

        canvas.addMouseUpHandler(this);
        
        initWidget(canvas);

        setStyleName(CLASSNAME);
        DOM.setStyleAttribute(canvas.getElement(), "border", "1px solid black");

        log.log(canvas.getRendererString());
    }

    /**
     * This method must be implemented to update the client-side component from
     * UIDL data received from server.
     *
     * This method is called when the page is loaded for the first time, and
     * every time UI changes in the component are received from the server.
     */
    public void updateFromUIDL(UIDL uidl, ApplicationConnection client) {
        // This call should be made first. Ensure correct implementation,
        // and let the containing layout manage caption, etc.
        if (client.updateComponent(this, uidl, true)) {
            return;
        }

        // Save reference to server connection object to be able to send
        // user interaction later
        this.client = client;

        // Save the UIDL identifier for the component
        uidlId = uidl.getId();

        log.log("updateFromUIDL");



        color=uidl.getStringVariable("color");
        log.log("Color = "+color);

        //canvas.clear();


    }

     /**
     * Override the method to communicate the new value
     * to server.
     **/
    public void setSVG(String newsvg) {

        // Updating the state to the server can not be done
        // before the server connection is known, i.e., before
        // updateFromUIDL() has been called.
        if (uidlId == null || client == null)
            return;
        // Communicate the user interaction parameters to server.
        // This call will initiate an AJAX request to the server.
        log.log(newsvg);
        client.updateVariable(uidlId, "svg", newsvg, true);
    }

    public void onMouseUp(MouseUpEvent event) {
        log.log("MouseUp");
        if(path!=null){
            path.lineTo(event.getX(),event.getY());
            setSVG(canvas.getElement().getInnerHTML());

            path=null;
        }
    }

    public void onMouseDown(MouseDownEvent event) {


        log.log("MouseDown "+event.getX()+","+event.getY());
        path=new Path(event.getX(),event.getY());
        path.setFillColor("none");
        path.setStrokeColor(color);
        canvas.add(path);

        Element relativeElem = event.getRelativeElement();
        log.log(relativeElem.toString());
    }

    public void onMouseMove(MouseMoveEvent event) {
        if(path!=null){
            path.lineTo(event.getX(),event.getY());
            //path.setStep(path.getStepCount()+1,new LineTo(false,event.getX(),event.getY()));
        }
    }
}
