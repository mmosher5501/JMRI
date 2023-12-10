package jmri.jmrix.easydcc.serialdriver;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import jmri.jmrix.easydcc.EasyDccPortController;
import jmri.jmrix.easydcc.EasyDccSystemConnectionMemo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements SerialPortAdapter for the EasyDCC system.
 * <p>
 * This connects an EasyDCC command station via a serial com port.
 * Normally controlled by the SerialDriverFrame class.
 * <p>
 * The current implementation only handles the 9,600 baud rate, and does not use
 * any other options at configuration time.
 *
 * @author Bob Jacobsen Copyright (C) 2001, 2002
 */
public class SerialDriverAdapter extends EasyDccPortController {

    public SerialDriverAdapter() {
        super(new EasyDccSystemConnectionMemo("E", "EasyDCC via Serial")); // pass customized user name
        setManufacturer(jmri.jmrix.easydcc.EasyDccConnectionTypeList.EASYDCC);
    }

    @Override
    public String openPort(String portName, String appName) {

        // get and open the primary port
        activeSerialPort = activatePort(portName, log);
        if (activeSerialPort == null) {
            log.error("failed to connect SPROG to {}", portName);
            return Bundle.getMessage("SerialPortNotFound", portName);
        }
        log.info("Connecting EasyDCC to {} {}", portName, activeSerialPort);
        
        // try to set it for communication via SerialDriver
        // baud rate is fixed at 9600
        setBaudRate(activeSerialPort, 9600);
        configureLeads(activeSerialPort, true, true);
        setFlowControl(activeSerialPort, FlowControl.NONE);

        // report status
        reportPortStatus(log, portName);

        opened = true;

        return null; // indicates OK return
    }

    /**
     * Set up all of the other objects to operate with an EasyDCC command
     * station connected to this port.
     */
    @Override
    public void configure() {
        // connect to the traffic controller, which is provided via the memo
        log.debug("set tc for memo {}", getSystemConnectionMemo().getUserName());

        getSystemConnectionMemo().getTrafficController().connectPort(this);

        // do the common manager config
        getSystemConnectionMemo().configureManagers();
    }

    // Base class methods for the EasyDccPortController interface

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean status() {
        return opened;
    }

    /**
     * {@inheritDoc}
     * Currently only 9,600 bps.
     */
    @Override
    public String[] validBaudRates() {
        return new String[]{Bundle.getMessage("Baud9600")};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int[] validBaudNumbers() {
        return new int[]{9600};
    }

    @Override
    public int defaultBaudIndex() {
        return 0;
    }

    // private control members
    private boolean opened = false;

    private final static Logger log = LoggerFactory.getLogger(SerialDriverAdapter.class);

}
