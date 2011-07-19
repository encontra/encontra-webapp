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

import com.vaadin.data.Property;
import com.vaadin.terminal.PaintException;
import com.vaadin.terminal.PaintTarget;
import com.vaadin.ui.AbstractComponent;
import com.vaadin.ui.AbstractField;
import com.vaadin.ui.ClientWidget;
import pt.inevo.encontra.gwt.client.ui.VSVGCanvas;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.Map;

@SuppressWarnings("serial")
@ClientWidget(VSVGCanvas.class)
public class SVGCanvas extends AbstractComponent {
    public class SVGChangeEvent extends Event implements Property.ValueChangeEvent {

        /**
         * Constructs a new event object with the specified source field object.
         *
         * @param source
         *            the field that caused the event.
         */
        public SVGChangeEvent(SVGCanvas source) {
            super(source);
        }

        /**
         * Gets the Property which triggered the event.
         *
         * @return the Source Property of the event.
         */
        public Property getProperty() {
            return (Property) getSource();
        }
    }

    private static final Method VALUE_CHANGE_METHOD;

    static {
        try {
            VALUE_CHANGE_METHOD = Property.ValueChangeListener.class
                    .getDeclaredMethod("valueChange",
                            new Class[] { Property.ValueChangeEvent.class });
        } catch (final java.lang.NoSuchMethodException e) {
            // This should never happen
            throw new java.lang.RuntimeException(
                    "Internal error finding methods in AbstractField");
        }
    }

    public void addListener(Property.ValueChangeListener listener) {
        addListener(AbstractField.ValueChangeEvent.class, listener,VALUE_CHANGE_METHOD);
    }

    private Color color=Color.BLACK;
    private String svg;

    public SVGCanvas() {
        super();
    }


    /** Set the currently selected color. */
    public void setSVG(String newsvg) {
        this.svg=newsvg;
        fireEvent(new SVGChangeEvent(this));
    }

    /** Paint (serialize) the component for the client. */
    @Override
    public void paintContent(PaintTarget target) throws PaintException {
        // Superclass writes any common attributes in the paint target.
        super.paintContent(target);
        // Add the currently selected color as a variable in
        // the paint target.
        target.addVariable(this, "svg", getSVG());

        target.addVariable(this,"color", "#"+Integer.toHexString(color.getRed()) + Integer.toHexString(color.getGreen()) + Integer.toHexString(color.getBlue()));

    }

    public String getSVG() {

        // The SVG does not include a root namespace declaration....
        String doctype="<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\"\n" +
                " \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n";
        return doctype+svg;
    }

    /** Deserialize changes received from client. */
    @SuppressWarnings("unchecked")
    @Override
    public void changeVariables(Object source, Map variables) {
        // Sets the currently selected color
        if (variables.containsKey("svg") && !isReadOnly()) {
            final String newValue = (String) variables.get("svg");
            setSVG(newValue);
        }

    }

    public void setColor( Color color) {
        this.color=color;
        requestRepaint();
    }
    public Color getColor() {
        return color;
    }
}
