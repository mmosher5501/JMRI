package jmri.jmrit.symbolicprog;

import java.io.File;
import java.io.IOException;
import javax.swing.JFrame;
import javax.swing.JLabel;

/**
 * Action to import the CV values from a LokProgrammer CV list file.
 *
 * @author Bob Jacobsen Copyright (C) 2003
 * @author Dave Heap Copyright (C) 2015
 */
public class LokProgImportAction extends GenericImportAction {

    public LokProgImportAction(String actionName, CvTableModel pModel, JFrame pParent, JLabel pStatus) {
        super(actionName, pModel, pParent, pStatus, "LokProgrammer CV list files", "txt", null);
    }

    @Override
    boolean launchImporter(File file, CvTableModel tableModel) {
            try {
                // ctor launches operation
                new LokProgImporter(file, mModel);
                return true;
            } catch (IOException ex) {
                return false;
            }
    }
}
