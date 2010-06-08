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

import com.vaadin.Application;
import com.vaadin.addon.colorpicker.ColorPicker;
import com.vaadin.addon.colorpicker.events.ColorChangeEvent;
import com.vaadin.terminal.FileResource;
import com.vaadin.ui.*;
import com.vaadin.ui.Button;
import com.vaadin.ui.TextField;
import com.vaadin.ui.Window;
import org.vaadin.peter.imagestrip.ImageStrip;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;


public class EnContRAApplication extends Application {

    @Override
    public void init() {
        Window main = new Window("EnContRA");
        setMainWindow(main);

        final VerticalLayout root = new VerticalLayout();
        main.setContent(root);


        final SVGCanvas canvas = new SVGCanvas();
        // Create the color picker
        ColorPicker cp = new ColorPicker("Our ColorPicker", Color.BLACK);
        cp.addListener(new ColorPicker.ColorChangeListener() {
            public void colorChanged(ColorChangeEvent event) {
                //canvas.setColor(event.getColor());
                getMainWindow().showNotification("Color changed!");
            }
        });
        final VerticalLayout canvasLayout = new VerticalLayout();
        canvasLayout.addComponent(cp);
        canvasLayout.addComponent(canvas);

        final ImageUploader uploader = new ImageUploader();


        final TabSheet tabsheet = new TabSheet();
        tabsheet.addTab(canvasLayout,"Sketch",null);
        tabsheet.addTab(uploader,"Picture",null);

        root.addComponent(tabsheet);

        HorizontalLayout h = new HorizontalLayout();
        TextField keywords = new TextField();
        keywords.setColumns(40);
        h.addComponent(keywords);

        Button b = new Button("Search");
        main.addComponent(b);

        final ImageStrip strip=new ImageStrip(ImageStrip.Alignment.HORIZONTAL);
        root.addComponent(strip);
        
        b.addListener(new Button.ClickListener() {
            public void buttonClick(Button.ClickEvent clickEvent) {
                File file = null;
                strip.removeAllImages();
                
                if(tabsheet.getSelectedTab().equals(canvasLayout)) {  // SKETCH
                    String svg=canvas.getSVG();
                    try {
                        file = File.createTempFile("encontra",".jpg");
                        new SVGConverter().convertToMimeType("image/jpeg",new ByteArrayInputStream(svg.getBytes()),new FileOutputStream(file));
                        
                    } catch (IOException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }

                } else {  // IMAGE
                    file=uploader.getFile();
                }

                if(file!=null) {

                        //BufferedImage img= ImageIO.read(file);

                    ArrayList<File> images=new ArrayList<File>();
                    for(int i=0;i<10;i++)
                        images.add(file);


                    strip.setMaxAllowed(10);
                    strip.setAnimated(true);

                    
                    int i=0;
                    for(File img : images) {
                        strip.addImage(
                            new FileResource(img, EnContRAApplication.this),"image_" + i++);
                    }
                    
                }
            }
        });
        h.addComponent(b);

        root.addComponent(h);

        setTheme("mytheme");
    }
}
