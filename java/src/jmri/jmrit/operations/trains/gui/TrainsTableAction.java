package jmri.jmrit.operations.trains.gui;

import java.awt.Frame;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Swing action to create and register a TrainTableFrame object.
 *
 * @author Bob Jacobsen Copyright (C) 2001
 * @author Daniel Boudreau Copyright (C) 2008
 */
public class TrainsTableAction extends AbstractAction {

    public TrainsTableAction() {
        super(Bundle.getMessage("MenuTrains")); // NOI18N
    }

    static TrainsTableFrame trainsTableFrame = null;

    @Override
    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", justification = "Show only one TrainsTableFrame")
    public void actionPerformed(ActionEvent e) {
        // create a train table frame
        if (trainsTableFrame == null || !trainsTableFrame.isVisible()) {
            trainsTableFrame = new TrainsTableFrame();
        }
        trainsTableFrame.setExtendedState(Frame.NORMAL);
        trainsTableFrame.setVisible(true); // this also brings the frame into focus
    }
}


