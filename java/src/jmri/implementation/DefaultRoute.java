package jmri.implementation;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import jmri.InstanceManager;
import jmri.JmriException;
import jmri.NamedBean;
import jmri.NamedBeanHandle;
import jmri.NamedBeanUsageReport;
import jmri.Route;
import jmri.Sensor;
import jmri.Turnout;
import jmri.jmrit.Sound;
import jmri.script.JmriScriptEngineManager;
import jmri.util.ThreadingUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class providing the basic logic of the Route interface.
 *
 * @see jmri.Route Route
 * @author Dave Duchamp Copyright (C) 2004
 * @author Bob Jacobsen Copyright (C) 2006, 2007
 * @author Simon Reader Copyright (C) 2008
 * @author Pete Cressman Copyright (C) 2009
 */
public class DefaultRoute extends AbstractNamedBean implements Route, java.beans.VetoableChangeListener {

    /**
     * Constructor for a Route instance with a given userName.
     *
     * @param systemName suggested system name
     * @param userName   provided user name
     */
    public DefaultRoute(String systemName, String userName) {
        super(systemName, userName);
    }

    /**
     * Constructor for a Route instance.
     *
     * @param systemName suggested system name
     */
    public DefaultRoute(String systemName) {
        super(systemName);
        log.debug("default Route {} created", systemName);
    }

    /** {@inheritDoc} */
    @Override
    public String getBeanType() {
        return Bundle.getMessage("BeanNameRoute");
    }

    /**
     * Persistant instance variables (saved between runs).
     */
    private String mControlTurnout = "";
    private NamedBeanHandle<Turnout> mControlNamedTurnout = null;
    private int mControlTurnoutState = jmri.Turnout.THROWN;
    private int mDelay = 0;
    private boolean mTurnoutFeedbackIsCommanded = false;

    private String mLockControlTurnout = "";
    private NamedBeanHandle<Turnout> mLockControlNamedTurnout = null;
    private int mLockControlTurnoutState = Turnout.THROWN;

    private String mTurnoutsAlignedSensor = "";
    private NamedBeanHandle<Sensor> mTurnoutsAlignedNamedSensor = null;

    private String soundFilename;
    private String scriptFilename;

    private final jmri.NamedBeanHandleManager nbhm = InstanceManager.getDefault(jmri.NamedBeanHandleManager.class);

    /**
     * Operational instance variables (not saved between runs).
     */
    private ArrayList<OutputSensor> _outputSensorList = new ArrayList<>();

    private class OutputSensor {

        NamedBeanHandle<Sensor> _sensor;
        int _state = Sensor.ACTIVE;

        OutputSensor(String name) throws IllegalArgumentException {
            Sensor sensor = InstanceManager.sensorManagerInstance().provideSensor(name);
            _sensor = nbhm.getNamedBeanHandle(name, sensor);
        }

        String getName() {
            if (_sensor != null) {
                return _sensor.getName();
            }
            return null;
        }

        boolean setState(int state) {
            if (_sensor == null) {
                return false;
            }
            if ((state != Sensor.ACTIVE) && (state != Sensor.INACTIVE) && (state != Route.TOGGLE)) {
                log.warn("Illegal Sensor state for Route: {}", getName());
                return false;
            }
            _state = state;
            return true;
        }

        int getState() {
            return _state;
        }

        Sensor getSensor() {
            if (_sensor != null) {
                return _sensor.getBean();
            }
            return null;
        }
    }

    private ArrayList<ControlSensor> _controlSensorList = new ArrayList<>();

    private class ControlSensor extends OutputSensor implements PropertyChangeListener {

        ControlSensor(String name) {
            super(name);
        }

        @Override
        boolean setState(int state) {
            if (_sensor == null) {
                return false;
            }
            _state = state;
            return true;
        }

        void addListener() {
            if (_sensor != null) {
                _sensor.getBean().addPropertyChangeListener(this, getName(),
                    "Route " + getDisplayName() + "Output Sensor");
            }
        }

        void removeListener() {
            if (_sensor != null) {
                _sensor.getBean().removePropertyChangeListener(this);
            }
        }

        @Override
        public void propertyChange(PropertyChangeEvent e) {
            if ( Sensor.PROPERTY_KNOWN_STATE.equals(e.getPropertyName())) {
                int now = ((Integer) e.getNewValue());
                int then = ((Integer) e.getOldValue());
                checkSensor(now, then, (Sensor) e.getSource());
            }
        }
    }

    private transient PropertyChangeListener mTurnoutListener = null;
    private transient PropertyChangeListener mLockTurnoutListener = null;

    private ArrayList<OutputTurnout> _outputTurnoutList = new ArrayList<>();

    private class OutputTurnout implements PropertyChangeListener {

        NamedBeanHandle<Turnout> _turnout;
        int _state;

        OutputTurnout(String name) throws IllegalArgumentException {
            Turnout turnout = InstanceManager.turnoutManagerInstance().provideTurnout(name);
            _turnout = nbhm.getNamedBeanHandle(name, turnout);

        }

        String getName() {
            if (_turnout != null) {
                return _turnout.getName();
            }
            return null;
        }

        boolean setState(int state) {
            if (_turnout == null) {
                return false;
            }
            if ((state != Turnout.THROWN) && (state != Turnout.CLOSED) && (state != Route.TOGGLE)) {
                log.warn("Illegal Turnout state for Route: {}", getName());
                return false;
            }
            _state = state;
            return true;
        }

        int getState() {
            return _state;
        }

        Turnout getTurnout() {
            return (_turnout != null ? _turnout.getBean() : null);
        }

        void addListener() {
            if (_turnout != null) {
                _turnout.getBean().addPropertyChangeListener(this, getName(),
                    "Route " + getDisplayName() + " Output Turnout");
            }
        }

        void removeListener() {
            if (_turnout != null) {
                _turnout.getBean().removePropertyChangeListener(this);
            }
        }

        @Override
        public void propertyChange(PropertyChangeEvent e) {
            if ( Turnout.PROPERTY_KNOWN_STATE.equals(e.getPropertyName())
                    || Turnout.PROPERTY_COMMANDED_STATE.equals(e.getPropertyName())) {
                //check alignement of all turnouts in route
                checkTurnoutAlignment();
            }
        }
    }
    private boolean busy = false;
    private boolean _enabled = true;

    /** {@inheritDoc} */
    @Override
    public boolean getEnabled() {
        return _enabled;
    }

    /** {@inheritDoc} */
    @Override
    public void setEnabled(boolean v) {
        boolean old = _enabled;
        _enabled = v;
        if (old != v) {
            firePropertyChange(PROPERTY_ENABLED, old, v);
        }
    }

    private boolean _locked = false;

    /** {@inheritDoc} */
    @Override
    public boolean getLocked() {
        return _locked;
    }

    /** {@inheritDoc} */
    @Override
    public void setLocked(boolean v) {
        lockTurnouts(v);
        boolean old = _locked;
        _locked = v;
        if (old != v) {
            firePropertyChange(PROPERTY_ROUTE_LOCKED, old, v);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean canLock() {
        for ( OutputTurnout oto : _outputTurnoutList){
            Turnout to = oto.getTurnout();
            if ( to !=null && to.canLock(Turnout.CABLOCKOUT)) {
                return true;
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean addOutputTurnout(String turnoutName, int turnoutState) {
        OutputTurnout outputTurnout = new OutputTurnout(turnoutName);
        if (!outputTurnout.setState(turnoutState)) {
            return false;
        }
        _outputTurnoutList.add(outputTurnout);
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public void clearOutputTurnouts() {
        _outputTurnoutList = new ArrayList<>();
    }

    /** {@inheritDoc} */
    @Override
    public int getNumOutputTurnouts() {
        return _outputTurnoutList.size();
    }

    /** {@inheritDoc} */
    @Override
    @CheckForNull
    public String getOutputTurnoutByIndex(int index) {
        try {
            return _outputTurnoutList.get(index).getName();
        } catch (IndexOutOfBoundsException ioob) {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isOutputTurnoutIncluded(String turnoutName) throws IllegalArgumentException {
        Turnout t1 = InstanceManager.turnoutManagerInstance().provideTurnout(turnoutName);
        return isOutputTurnoutIncluded(t1);
    }

    private boolean isOutputTurnoutIncluded(Turnout t1) {
        for (int i = 0; i < _outputTurnoutList.size(); i++) {
            if (_outputTurnoutList.get(i).getTurnout() == t1) {
                // Found turnout
                return true;
            }
        }
        return false;
    }

    private void deleteOutputTurnout(Turnout t) {
        int index = -1;
        for (int i = 0; i < _outputTurnoutList.size(); i++) {
            if (_outputTurnoutList.get(i).getTurnout() == t) {
                index = i;
                break;
            }
        }
        if (index != -1) {
            _outputTurnoutList.remove(index);
        }
    }

    /** {@inheritDoc} */
    @Override
    public int getOutputTurnoutSetState(String name) throws IllegalArgumentException {
        Turnout t1 = InstanceManager.turnoutManagerInstance().provideTurnout(name);
        for (int i = 0; i < _outputTurnoutList.size(); i++) {
            if (_outputTurnoutList.get(i).getTurnout() == t1) {
                // Found turnout
                return _outputTurnoutList.get(i).getState();
            }
        }
        return -1;
    }

    /** {@inheritDoc} */
    @Override
    @CheckForNull
    public Turnout getOutputTurnout(int k) {
        try {
            return _outputTurnoutList.get(k).getTurnout();
        } catch (IndexOutOfBoundsException ioob) {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public int getOutputTurnoutState(int k) {
        try {
            return _outputTurnoutList.get(k).getState();
        } catch (IndexOutOfBoundsException ioob) {
            return -1;
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean addOutputSensor(String sensorName, int state) {
        OutputSensor outputSensor = new OutputSensor(sensorName);
        if (!outputSensor.setState(state)) {
            return false;
        }
        _outputSensorList.add(outputSensor);
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public void clearOutputSensors() {
        _outputSensorList = new ArrayList<>();
    }

    @Override
    public int getNumOutputSensors() {
        return _outputSensorList.size();
    }

    /** {@inheritDoc} */
    @Override
    @CheckForNull
    public String getOutputSensorByIndex(int index) {
        try {
            return _outputSensorList.get(index).getName();
        } catch (IndexOutOfBoundsException ioob) {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isOutputSensorIncluded(String sensorName) throws IllegalArgumentException {
        Sensor s1 = InstanceManager.sensorManagerInstance().provideSensor(sensorName);
        return isOutputSensorIncluded(s1);
    }

    private boolean isOutputSensorIncluded(Sensor s1) {
        for (int i = 0; i < _outputSensorList.size(); i++) {
            if (_outputSensorList.get(i).getSensor() == s1) {
                // Found turnout
                return true;
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public int getOutputSensorSetState(String name) throws IllegalArgumentException {
        Sensor s1 = InstanceManager.sensorManagerInstance().provideSensor(name);
        for (int i = 0; i < _outputSensorList.size(); i++) {
            if (_outputSensorList.get(i).getSensor() == s1) {
                // Found turnout
                return _outputSensorList.get(i).getState();
            }
        }
        return -1;
    }

    /** {@inheritDoc} */
    @Override
    @CheckForNull
    public Sensor getOutputSensor(int k) {
        try {
            return _outputSensorList.get(k).getSensor();
        } catch (IndexOutOfBoundsException ioob) {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public int getOutputSensorState(int k) {
        try {
            return _outputSensorList.get(k).getState();
        } catch (IndexOutOfBoundsException ioob) {
            return -1;
        }
    }

    private void removeOutputSensor(Sensor s) {
        int index = -1;
        for (int i = 0; i < _outputSensorList.size(); i++) {
            if (_outputSensorList.get(i).getSensor() == s) {
                index = i;
                break;
            }
        }
        if (index != -1) {
            _outputSensorList.remove(index);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setOutputScriptName(String filename) {
        scriptFilename = filename;
    }

    /** {@inheritDoc} */
    @Override
    @CheckForNull
    public String getOutputScriptName() {
        return scriptFilename;
    }

    /** {@inheritDoc} */
    @Override
    public void setOutputSoundName(String filename) {
        soundFilename = filename;
    }

    /** {@inheritDoc} */
    @Override
    @CheckForNull
    public String getOutputSoundName() {
        return soundFilename;
    }

    /** {@inheritDoc} */
    @Override
    public void setTurnoutsAlignedSensor(@CheckForNull String sensorName) throws IllegalArgumentException {
        log.debug("setTurnoutsAlignedSensor {} {}", getSystemName(), sensorName);

        mTurnoutsAlignedSensor = sensorName;
        if (mTurnoutsAlignedSensor == null || mTurnoutsAlignedSensor.isEmpty()) {
            mTurnoutsAlignedNamedSensor = null;
            return;
        }
        Sensor s = InstanceManager.sensorManagerInstance().provideSensor(mTurnoutsAlignedSensor);
        mTurnoutsAlignedNamedSensor = nbhm.getNamedBeanHandle(mTurnoutsAlignedSensor, s);
    }

    /** {@inheritDoc} */
    @Override
    @CheckForNull
    public String getTurnoutsAlignedSensor() {
        if (mTurnoutsAlignedNamedSensor != null) {
            return mTurnoutsAlignedNamedSensor.getName();
        }
        return mTurnoutsAlignedSensor;
    }

    /** {@inheritDoc} */
    @Override
    @CheckForNull
    public Sensor getTurnoutsAlgdSensor() throws IllegalArgumentException {
        if (mTurnoutsAlignedNamedSensor != null) {
            return mTurnoutsAlignedNamedSensor.getBean();
        } else if (mTurnoutsAlignedSensor != null && !mTurnoutsAlignedSensor.isEmpty()) {
            Sensor s = InstanceManager.sensorManagerInstance().provideSensor(mTurnoutsAlignedSensor);
            mTurnoutsAlignedNamedSensor = nbhm.getNamedBeanHandle(mTurnoutsAlignedSensor, s);
            return s;
        }
        return null;
    }
    // Inputs ----------------

    /** {@inheritDoc} */
    @Override
    public void clearRouteSensors() {
        _controlSensorList = new ArrayList<>();
    }

    /**
     * Method returns true if the sensor provided is already in the list of
     * control sensors for this route.
     *
     * @param sensor the sensor to check for
     * @return true if the sensor is found, false otherwise
     */
    private boolean isControlSensorIncluded(@Nonnull ControlSensor sensor) {
        for (int i = 0; i < _controlSensorList.size(); i++) {
            if (_controlSensorList.get(i).getName().equals(sensor.getName())
                    && _controlSensorList.get(i).getState() == sensor.getState()) {
                return true;
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean addSensorToRoute(String sensorName, int mode) {
        log.debug("addSensorToRoute({}, {}) as {} in {}", sensorName, mode, _controlSensorList.size(), getSystemName());

        ControlSensor sensor = new ControlSensor(sensorName);
        if (!sensor.setState(mode)) {
            return false;
        }
        if (isControlSensorIncluded(sensor)) {
            // this is a normal condition, but log in case
            log.debug("Not adding duplicate control sensor {} to route {}", sensorName, getSystemName());
        } else {
            _controlSensorList.add(sensor);
        }

        if (_controlSensorList.size() > MAX_CONTROL_SENSORS) {
            // reached maximum
            log.warn("Sensor {} exceeded maximum number of control Sensors for Route: {}", sensorName, getSystemName());
        }

        return true;
    }

    /** {@inheritDoc} */
    @Override
    @CheckForNull
    public String getRouteSensorName(int index) {
        try {
            return _controlSensorList.get(index).getName();
        } catch (IndexOutOfBoundsException ioob) {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    @CheckForNull
    public Sensor getRouteSensor(int index) {
        try {
            return _controlSensorList.get(index).getSensor();
        } catch (IndexOutOfBoundsException ioob) {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public int getRouteSensorMode(int index) {
        try {
            return _controlSensorList.get(index).getState();
        } catch (IndexOutOfBoundsException ioob) {
            return 0;
        }
    }

    boolean isRouteSensorIncluded(Sensor s) {
        for (int i = 0; i < _controlSensorList.size(); i++) {
            if (_controlSensorList.get(i).getSensor() == s) {
                // Found turnout
                return true;
            }
        }
        return false;
    }

    void removeRouteSensor(Sensor s) {
        int index = -1;
        for (int i = 0; i < _controlSensorList.size(); i++) {
            if (_controlSensorList.get(i).getSensor() == s) {
                index = i;
                break;
            }
        }
        if (index != -1) {
            _controlSensorList.remove(index);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setControlTurnout(@CheckForNull String turnoutName) throws IllegalArgumentException {
        mControlTurnout = turnoutName;
        if (mControlTurnout == null || mControlTurnout.isEmpty()) {
            mControlNamedTurnout = null;
            return;
        }
        Turnout t = InstanceManager.turnoutManagerInstance().provideTurnout(mControlTurnout);
        mControlNamedTurnout = nbhm.getNamedBeanHandle(mControlTurnout, t);
    }

    /** {@inheritDoc} */
    @Override
    @CheckForNull
    public String getControlTurnout() {
        if (mControlNamedTurnout != null) {
            return mControlNamedTurnout.getName();
        }
        return mControlTurnout;
    }

    /** {@inheritDoc} */
    @Override
    @CheckForNull
    public Turnout getCtlTurnout() throws IllegalArgumentException {
        if (mControlNamedTurnout != null) {
            return mControlNamedTurnout.getBean();
        } else if (mControlTurnout != null && !mControlTurnout.isEmpty()) {
            Turnout t = InstanceManager.turnoutManagerInstance().provideTurnout(mControlTurnout);
            mControlNamedTurnout = nbhm.getNamedBeanHandle(mControlTurnout, t);
            return t;
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void setLockControlTurnout(@CheckForNull String turnoutName) throws IllegalArgumentException {
        mLockControlTurnout = turnoutName;
        if (mLockControlTurnout == null || mLockControlTurnout.isEmpty()) {
            mLockControlNamedTurnout = null;
            return;
        }
        Turnout t = InstanceManager.turnoutManagerInstance().provideTurnout(mLockControlTurnout);
        mLockControlNamedTurnout = nbhm.getNamedBeanHandle(mLockControlTurnout, t);
    }

    /** {@inheritDoc} */
    @Override
    @CheckForNull
    public String getLockControlTurnout() {
        if (mLockControlNamedTurnout != null) {
            return mLockControlNamedTurnout.getName();
        }
        return mLockControlTurnout;
    }

    /** {@inheritDoc} */
    @Override
    @CheckForNull
    public Turnout getLockCtlTurnout() throws IllegalArgumentException {
        if (mLockControlNamedTurnout != null) {
            return mLockControlNamedTurnout.getBean();
        } else if (mLockControlTurnout != null && !mLockControlTurnout.isEmpty()) {
            Turnout t = InstanceManager.turnoutManagerInstance().provideTurnout(mLockControlTurnout);
            mLockControlNamedTurnout = nbhm.getNamedBeanHandle(mLockControlTurnout, t);
            return t;
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void setRouteCommandDelay(int delay) {
        if (delay >= 0) {
            mDelay = delay;
        }
    }

    /** {@inheritDoc} */
    @Override
    public int getRouteCommandDelay() {
        return mDelay;
    }

    /** {@inheritDoc} */
    @Override
    public void setControlTurnoutState(int turnoutState) {
        if ((turnoutState == Route.ONTHROWN)
                || (turnoutState == Route.ONCLOSED)
                || (turnoutState == Route.ONCHANGE)
                || (turnoutState == Route.VETOCLOSED)
                || (turnoutState == Route.VETOTHROWN)) {
            mControlTurnoutState = turnoutState;
        } else {
            log.error("Attempt to set invalid control Turnout state for Route.");
        }
    }

    /** {@inheritDoc} */
    @Override
    public int getControlTurnoutState() {
        return mControlTurnoutState;
    }

    /** {@inheritDoc} */
    @Override
    public void setControlTurnoutFeedback(boolean turnoutFeedbackIsCommanded) {
        mTurnoutFeedbackIsCommanded  = turnoutFeedbackIsCommanded;
    }

    /** {@inheritDoc} */
    @Override
    public boolean getControlTurnoutFeedback() {
        return mTurnoutFeedbackIsCommanded;
    }

    /** {@inheritDoc} */
    @Override
    public void setLockControlTurnoutState(int turnoutState) {
        if ((turnoutState == Route.ONTHROWN)
                || (turnoutState == Route.ONCLOSED)
                || (turnoutState == Route.ONCHANGE)) {
            mLockControlTurnoutState = turnoutState;
        } else {
            log.error("Attempt to set invalid lock control Turnout state for Route.");
        }
    }

    /** {@inheritDoc} */
    @Override
    public int getLockControlTurnoutState() {
        return mLockControlTurnoutState;
    }

    /**
     * Lock or unlock turnouts that are part of a route
     */
    private void lockTurnouts(boolean lock) {
        // determine if turnout should be locked
        for (int i = 0; i < _outputTurnoutList.size(); i++) {
            _outputTurnoutList.get(i).getTurnout().setLocked(
                Turnout.CABLOCKOUT + Turnout.PUSHBUTTONLOCKOUT, lock);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setRoute() {
        if ((!_outputTurnoutList.isEmpty())
                || (!_outputSensorList.isEmpty())
                || (soundFilename != null)
                || (scriptFilename != null)) {
            if (!busy) {
                log.debug("Setting route {}", this.getSystemName());
                setRouteBusy(true);
                SetRouteThread thread = new SetRouteThread(this);
                thread.setName("Route "+getDisplayName()+" setRoute");
                thread.start();
            } else {
                log.debug("Not setting route {} because busy", this.getSystemName());
            }
        } else {
            log.debug("Unable to set route {} because no turnouts or no sensors", this.getSystemName());
        }
    }

    /**
     * Handle sensor update event to see if it will set the route.
     * <p>
     * Called when a "KnownState" event is received, it assumes that only one
     * sensor is changing right now, so can use state calls for everything other
     * than this sensor.
     * <p>
     * This will fire the Route if the conditions are correct.
     * <p>
     * Returns nothing explicitly, but has the side effect of firing route.
     *
     * @param newState new state of control sensor
     * @param oldState former state
     * @param sensor   Sensor used as Route control sensor
     */
    protected void checkSensor(int newState, int oldState, @Nonnull Sensor sensor) {
        // check for veto of change
        if (isVetoed()) {
            return; // don't fire
        }
        String name = sensor.getSystemName();
        log.debug("check Sensor {} for {}", name, getSystemName());
        boolean fire = false;  // dont fire unless we find something
        for (int i = 0; i < _controlSensorList.size(); i++) {
            Sensor s = getRouteSensor(i);
            if (s !=null && s.equals(sensor)) {
                // here for match, check mode & handle onActive, onInactive
                int mode = getRouteSensorMode(i);
                log.debug("match mode: {} new state: {} old state: {}", mode, newState, oldState);

                // if in target mode, note whether to act
                if (((mode == ONACTIVE) && (newState == Sensor.ACTIVE))
                        || ((mode == ONINACTIVE) && (newState == Sensor.INACTIVE))
                        || ((mode == ONCHANGE) && (newState != oldState))) {
                    fire = true;
                }

                // if any other modes, just skip because
                // the sensor might be in list more than once
            }
        }

        log.debug("check activated");
        if (!fire) {
            return;
        }

        // and finally set the route
        log.debug("call setRoute for {}", getSystemName());
        setRoute();
    }

    /**
     * Turnout has changed, check to see if this fires.
     * <p>
     * Will fire Route if appropriate.
     *
     * @param newState new state of control turnout
     * @param oldState former state
     * @param t        Turnout used as Route control turnout
     */
    void checkTurnout(int newState, int oldState, Turnout t) {
        if (isVetoed()) {
            return; // skip setting route
        }
        switch (mControlTurnoutState) {
            case ONCLOSED:
                if (newState == Turnout.CLOSED) {
                    setRoute();
                }
                break;
            case ONTHROWN:
                if (newState == Turnout.THROWN) {
                    setRoute();
                }
                break;
            case ONCHANGE:
                if (newState != oldState) {
                    setRoute();
                }
                break;
            default:
                break; // not a firing state
        }
    }

    /**
     * Turnout has changed, check to see if this will lock or unlock route.
     *
     * @param newState  new state of lock turnout
     * @param oldState  former turnout state
     * @param t         Turnout used for locking the Route
     */
    void checkLockTurnout(int newState, int oldState, Turnout t) {
        switch (mLockControlTurnoutState) {
            case ONCLOSED:
                setLocked(newState == Turnout.CLOSED);
                break;
            case ONTHROWN:
                setLocked(newState == Turnout.THROWN);
                break;
            case ONCHANGE:
                if (newState != oldState) {
                    setLocked(!getLocked());
                }
                break;
            default: // if none, return
        }
    }

    /**
     * Method to check if the turnouts for this route are correctly aligned.
     * Sets turnouts aligned sensor (if there is one) to active if the turnouts
     * are aligned. Sets the sensor to inactive if they are not aligned
     */
    public void checkTurnoutAlignment() {

        //check each of the output turnouts in turn
        //turnouts are deemed not aligned if:
        // - commanded and known states don't agree
        // - non-toggle turnouts known state not equal to desired state
        // turnouts aligned sensor is then set accordingly
        Sensor sensor = this.getTurnoutsAlgdSensor();
        if (sensor != null) {
            try {
                // this method can be called multiple times while a route is
                // still going ACTIVE, so short-circut out as INCONSISTENT if
                // isRouteBusy() is true; this ensures nothing watching the
                // route shows it as ACTIVE when it may not really be
                if (this.isRouteBusy()) {
                    sensor.setKnownState(Sensor.INCONSISTENT);
                    return;
                }
                for (OutputTurnout ot : this._outputTurnoutList) {
                    Turnout turnout = ot.getTurnout();
                    int targetState = ot.getState();
                    if (!turnout.isConsistentState()) {
                        sensor.setKnownState(Sensor.INCONSISTENT);
                        return;
                    }
                    if (targetState != Route.TOGGLE && targetState != turnout.getKnownState()) {
                        sensor.setKnownState(Sensor.INACTIVE);
                        return;
                    }
                }
                sensor.setKnownState(Sensor.ACTIVE);
            } catch (JmriException ex) {
                log.warn("Exception setting sensor {} in route", getTurnoutsAlignedSensor());
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void activateRoute() {
        activatedRoute = true;

        String tas = getTurnoutsAlignedSensor();
        //register output turnouts to return Known State if a turnouts aligned sensor is defined
        if ( tas != null && !tas.isEmpty()) {

            for (int k = 0; k < _outputTurnoutList.size(); k++) {
                _outputTurnoutList.get(k).addListener();
            }
        }

        for (int k = 0; k < _controlSensorList.size(); k++) {
            _controlSensorList.get(k).addListener();
        }
        Turnout ctl = getCtlTurnout();
        if (ctl != null) {
            mTurnoutListener = e -> {
                String name = Turnout.PROPERTY_KNOWN_STATE;
                if (this.getControlTurnoutFeedback()) {
                    name = Turnout.PROPERTY_COMMANDED_STATE;
                }
                if (e.getPropertyName().equals(name)) {
                    int now = ((Integer) e.getNewValue());
                    int then = ((Integer) e.getOldValue());
                    checkTurnout(now, then, (Turnout) e.getSource());
                }
            };
            ctl.addPropertyChangeListener(mTurnoutListener, getControlTurnout(), "Route " + getDisplayName());
        }
        Turnout lockCtl = getLockCtlTurnout();
        if (lockCtl != null) {
            mLockTurnoutListener = e -> {
                if ( Turnout.PROPERTY_KNOWN_STATE.equals(e.getPropertyName())) {
                    int now = ((Integer) e.getNewValue());
                    int then = ((Integer) e.getOldValue());
                    checkLockTurnout(now, then, (Turnout) e.getSource());
                }
            };
            lockCtl.addPropertyChangeListener(mLockTurnoutListener,
                getLockControlTurnout(), "Route " + getDisplayName());
        }

        checkTurnoutAlignment();
        // register for updates to the Output Turnouts
    }

    /**
     * Internal method to check whether operation of the route has been vetoed
     * by a sensor or turnout setting.
     *
     * @return true if veto, i.e. don't fire route; false if no veto, OK to fire
     */
    boolean isVetoed() {
        log.debug("check for veto");
        // check this route not enabled
        if (!_enabled) {
            return true;
        }

        // check sensors
        for (int i = 0; i < _controlSensorList.size(); i++) {
            ControlSensor controlSensor = _controlSensorList.get(i);
            int s = controlSensor.getSensor().getKnownState();
            int mode = controlSensor.getState();
            if (((mode == VETOACTIVE) && (s == Sensor.ACTIVE))
                    || ((mode == VETOINACTIVE) && (s == Sensor.INACTIVE))) {
                return true;  // veto set
            }
        }
        // check control turnout
        Turnout ctl = getCtlTurnout();
        if (ctl != null) {
            int tstate = ctl.getKnownState();
            if (mControlTurnoutState == Route.VETOCLOSED && tstate == Turnout.CLOSED) {
                return true;
            }
            if (mControlTurnoutState == Route.VETOTHROWN && tstate == Turnout.THROWN) {
                return true;
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void deActivateRoute() {
        //Check that the route isn't already deactived.
        if (!activatedRoute) {
            return;
        }

        activatedRoute = false;
        // remove control turnout if there's one
        for (int k = 0; k < _controlSensorList.size(); k++) {
            _controlSensorList.get(k).removeListener();
        }
        if (mTurnoutListener != null) {
            Turnout ctl = getCtlTurnout();
            if (ctl != null) {
                ctl.removePropertyChangeListener(mTurnoutListener);
            }
            mTurnoutListener = null;
        }
        // remove lock control turnout if there's one
        if (mLockTurnoutListener != null) {
            Turnout lockCtl = getCtlTurnout();
            if (lockCtl != null) {
                lockCtl.removePropertyChangeListener(mLockTurnoutListener);
            }
            mLockTurnoutListener = null;
        }
        //remove listeners on output turnouts if there are any
        if (!mTurnoutsAlignedSensor.isEmpty()) {
            for (int k = 0; k < _outputTurnoutList.size(); k++) {
                _outputTurnoutList.get(k).removeListener();
            }
        }
    }

    private boolean activatedRoute = false;

    /**
     * Mark the Route as transitioning to an {@link jmri.Sensor#ACTIVE} state.
     *
     * @param busy true if Route should be busy.
     */
    protected void setRouteBusy(boolean busy) {
        this.busy = busy;
        this.checkTurnoutAlignment();
    }

    /**
     * Method to query if Route is busy (returns true if commands are being
     * issued to Route turnouts)
     *
     * @return true if the Route is transitioning to an
     *         {@link jmri.Sensor#ACTIVE} state, false otherwise.
     */
    protected boolean isRouteBusy() {
        return busy;
    }

    /** {@inheritDoc} */
    @Override
    public int getState() {
        Sensor s = getTurnoutsAlgdSensor();
        if (s != null) {
            return s.getKnownState();
        }
        return UNKNOWN;
    }

    /** {@inheritDoc} */
    @Override
    public void setState(int state) {
        setRoute();
    }

    /** {@inheritDoc} */
    @Override
    public void vetoableChange(PropertyChangeEvent evt) throws java.beans.PropertyVetoException {
        NamedBean nb = (NamedBean) evt.getOldValue();
        if ( jmri.Manager.PROPERTY_CAN_DELETE.equals(evt.getPropertyName())) {
            StringBuilder message = new StringBuilder();
            message.append("<b>").append(getDisplayName()).append("</b><ul>"); // NOI18N
            boolean found = false;
            if (nb instanceof Turnout) {
                if (isOutputTurnoutIncluded((Turnout) nb)) {
                    message.append(Bundle.getMessage("InUseRouteOutputTurnout")); // NOI18N
                    found = true;
                }
                if (nb.equals(getCtlTurnout())) {
                    message.append(Bundle.getMessage("InUseRouteControlTurnout")); // NOI18N
                    found = true;
                }
                if (nb.equals(getLockCtlTurnout())) {
                    message.append(Bundle.getMessage("InUseRouteLockTurnout")); // NOI18N
                    found = true;
                }
            } else if (nb instanceof Sensor) {
                if (isOutputSensorIncluded((Sensor) nb)) {
                    message.append(Bundle.getMessage("InUseRouteOutputSensor")); // NOI18N
                    found = true;
                }
                if (nb.equals(getTurnoutsAlgdSensor())) {
                    message.append(Bundle.getMessage("InUseRouteAlignSensor")); // NOI18N
                    found = true;
                }
                if (isRouteSensorIncluded((Sensor) nb)) {
                    message.append(Bundle.getMessage("InUseRouteSensor")); // NOI18N
                    found = true;
                }

            }
            if (found) {
                message.append("</ul>");
                throw new java.beans.PropertyVetoException(message.toString(), evt);
            }
        } else if (jmri.Manager.PROPERTY_DO_DELETE.equals(evt.getPropertyName())) {
            if (nb instanceof Turnout) {
                if (isOutputTurnoutIncluded((Turnout) nb)) {
                    deActivateRoute();
                    deleteOutputTurnout((Turnout) evt.getOldValue());
                }
                if (nb.equals(getCtlTurnout())) {
                    deActivateRoute();
                    setControlTurnout(null);
                }
                if (nb.equals(getLockCtlTurnout())) {
                    deActivateRoute();
                    setLockControlTurnout(null);
                }
            } else if (nb instanceof Sensor) {
                if (isOutputSensorIncluded((Sensor) nb)) {
                    deActivateRoute();
                    removeOutputSensor((Sensor) nb);
                }
                if (nb.equals(getTurnoutsAlgdSensor())) {
                    deActivateRoute();
                    setTurnoutsAlignedSensor(null);
                }
                if (isRouteSensorIncluded((Sensor) nb)) {
                    deActivateRoute();
                    removeRouteSensor((Sensor) nb);
                }
            }
            activateRoute();
        }
    }

    @Override
    public List<NamedBeanUsageReport> getUsageReport(NamedBean bean) {
        List<NamedBeanUsageReport> report = new ArrayList<>();
        if (bean != null) {
            for (int i = 0; i < getNumOutputTurnouts(); i++) {
                if (bean.equals(getOutputTurnout(i))) {
                    report.add(new NamedBeanUsageReport("RouteTurnoutOutput"));  // NOI18N
                }
            }
            for (int i = 0; i < getNumOutputSensors(); i++) {
                if (bean.equals(getOutputSensor(i))) {
                    report.add(new NamedBeanUsageReport("RouteSensorOutput"));  // NOI18N
                }
            }
            for (int i = 0; i < _controlSensorList.size(); i++) {
                if (bean.equals(getRouteSensor(i))) {
                    report.add(new NamedBeanUsageReport("RouteSensorControl"));  // NOI18N
                }
            }
            if (bean.equals(getTurnoutsAlgdSensor())) {
                report.add(new NamedBeanUsageReport("RouteSensorAligned"));  // NOI18N
            }
            if (bean.equals(getCtlTurnout())) {
                report.add(new NamedBeanUsageReport("RouteTurnoutControl"));  // NOI18N
            }
            if (bean.equals(getLockCtlTurnout())) {
                report.add(new NamedBeanUsageReport("RouteTurnoutLock"));  // NOI18N
            }
        }
        return report;
    }

    private static final Logger log = LoggerFactory.getLogger(DefaultRoute.class);

    /**
     * Class providing a thread to set route turnouts.
     */
    private static class SetRouteThread extends Thread {

        /**
         * Constructs the thread.
         *
         * @param aRoute DefaultRoute to set
         */
        private SetRouteThread(DefaultRoute aRoute) {
            r = aRoute;
        }

        /**
         * Runs the thread - performs operations in the order:
         * <ul>
         * <li>Run script (can run in parallel)
         * <li>Play Sound (runs in parallel)
         * <li>Set Sensors
         * <li>Set Turnouts
         * </ul>
         */
        @Override
        public void run() {
            runScript();
            playSound();
            setSensors();
            setTurnouts();
            // set route not busy
            r.setRouteBusy(false);
        }

        private void runScript() {
            // run script defined for start of route set
            String scriptName = r.getOutputScriptName();
            if ((scriptName != null) && (!scriptName.isEmpty())) {
                JmriScriptEngineManager.getDefault().runScript(
                    new File(jmri.util.FileUtil.getExternalFilename(scriptName)));
            }
        }

        private void playSound() {
            // play sound defined for start of route set
            var outputSoundName = r.getOutputSoundName();
            if (( outputSoundName != null) && (!outputSoundName.isEmpty())) {
                try {
                    (new Sound(outputSoundName)).play(true);
                } catch (NullPointerException ex) {
                    setRouteLog.error("Cannot find Sound file {} for Route {}", outputSoundName, r.getDisplayName());
                }
            }
        }

        private void setSensors() {
            // set sensors
            for (int k = 0; k < r.getNumOutputSensors(); k++) {
                Sensor t = r.getOutputSensor(k);
                if ( t==null ) {
                    setRouteLog.warn("Sensor {} not found for Route {}",k,r.getDisplayName());
                    continue;
                }
                int state = r.getOutputSensorState(k);
                if (state == Route.TOGGLE) {
                    int st = t.getKnownState();
                    if (st == Sensor.ACTIVE) {
                        state = Sensor.INACTIVE;
                    } else {
                        state = Sensor.ACTIVE;
                    }
                }
                final int toState = state;
                final Sensor setSensor = t;
                ThreadingUtil.runOnLayoutEventually(() -> { // eventually, even though timing here, should be soon
                    try {
                        setSensor.setKnownState(toState);
                    } catch (JmriException e) {
                        setRouteLog.warn("Exception setting sensor {} in route {} {}",
                            setSensor.getSystemName(), r.getDisplayName(), e.getMessage());
                    }
                });
                threadSleep(50);
            }
        }

        private void threadSleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // retain if needed later
            }
        }

        private void setTurnouts() {
            // set turnouts
            int delay = r.getRouteCommandDelay();

            for (int k = 0; k < r.getNumOutputTurnouts(); k++) {
                Turnout t = r.getOutputTurnout(k);
                if ( t == null ) {
                    log.error("No Turnout {} for Route {}",k,r.getDisplayName());
                    continue;
                }
                int state = r.getOutputTurnoutState(k);
                if (state == Route.TOGGLE) {
                    int st = t.getKnownState();
                    if (st == Turnout.CLOSED) {
                        state = Turnout.THROWN;
                    } else {
                        state = Turnout.CLOSED;
                    }
                }
                final int toState = state;
                final Turnout setTurnout = t;
                ThreadingUtil.runOnLayoutEventually(() -> // eventually, even though we have timing here
                    setTurnout.setCommandedStateAtInterval(toState)); // delayed by connection specific turnoutManager
                threadSleep(delay); // only the Route specific user defined delay is applied here
            }
        }

        private final DefaultRoute r;

        private static final Logger setRouteLog = LoggerFactory.getLogger(SetRouteThread.class);
    }

}
