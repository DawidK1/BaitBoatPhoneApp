package com.urmom.baitboatphoneapp;

import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BoatProtocolParser {
    private final static String TAG = "BaitBoatPhoneApp";

    static final int TYPE_GPS = 0;
    static final int TYPE_VOLTAGE = 1;
    static final int TYPE_COMPASS = 2;
    static final int TYPE_PRESSURE = 3;
    static final int TYPE_TEMPERATURE = 4;
    static final int TYPE_INVALID = 4;

    static final int LONGEST_VALID_FRAME_SIZE = 19;


    private int slowAngularVelThreshold = 45;
    private double slowAngularCoeff = 0.4;
    private int maxAllowedMotorVal = 100;
    private double motorIneqalityCompensation = 1.0;
    ArrayList<Byte> rxBuffer = new ArrayList<Byte>();

    ConcurrentLinkedQueue<ReceivedBoatMessage> rxParsedCommands = new ConcurrentLinkedQueue<>();

    private class RxParseRule {
        int type;
        byte[] pattern;

        RxParseRule(int t, byte[] p) {
            type = t;
            pattern = p;
        }
    }

    public class ReceivedBoatMessage {

        int type;
        double val1;
        double val2;

        public ReceivedBoatMessage() {
            type = TYPE_INVALID;
            val1 = 0;
            val2 = 0;
        }
    }

    RxParseRule[] parsingRules = {new RxParseRule(TYPE_VOLTAGE, "ZXXXX_".getBytes()),
            new RxParseRule(TYPE_GPS, "NXXXXXXXX-XXXXXXXX_".getBytes()),
            new RxParseRule(TYPE_COMPASS, "MXX_".getBytes()),
            new RxParseRule(TYPE_PRESSURE, "OXXXX_".getBytes()),
            new RxParseRule(TYPE_TEMPERATURE, "PXXXX_".getBytes())};


    // from joy: full up is 100 power, 0 angle, full left -90 angle, full right 90 angle, full back 180/-180 angle
    byte[] generateMotorValuesFromJoystick(int power, int angle) {
        double motorLeft = 0;
        double motorRight = 0;
        double linearVel = 1.0;
        double angularVel = 1.0;

        int motorRightInt = 0;
        int motorLeftInt = 0;


        // reverse mode math
        if (Math.abs(angle) > 90) {
            power = -power;

            if (angle > 0) {
                angle = 180 - angle;
            } else {
                angle = -180 - angle;
            }
        }


        // mode for slow turning when going mostly forward/backward
        if (Math.abs(angle) < slowAngularVelThreshold) {
            angularVel = slowAngularCoeff;
        }

        // linear/angular
        linearVel = (maxAllowedMotorVal/100.0)*((90 - Math.abs(angle)) * power) / 90;
        angularVel *= (maxAllowedMotorVal/100.0)*(double) angle * 10.0 / 9.0;


        // values calc
        motorLeft = linearVel + angularVel;
        motorRight = linearVel - angularVel;

        motorLeft *= motorIneqalityCompensation;
        motorRight *= (2.0 - motorIneqalityCompensation);

        // Saturation check
        double maxMotorVal = Math.max(Math.abs(motorLeft), Math.abs((motorRight)));
        if (maxMotorVal > maxAllowedMotorVal) {
            motorLeft *= ((double) maxAllowedMotorVal) / maxMotorVal;
            motorRight *= ((double) maxAllowedMotorVal) / maxMotorVal;
        }

        motorLeftInt = (int) motorLeft;
        motorRightInt = (int) motorRight;
        Log.d(TAG, "Motors L: " + motorLeftInt + " R: " + motorRightInt);
        return encodeMotorValues(motorLeftInt, motorRightInt);
    }

    private byte[] encodeMotorValues(int left, int right) {
        return String.format("G%02X%02X_", left + 128, right + 128).getBytes();
    }

    byte[] getLoadDropRequest() {
        return "H_".getBytes();
    }

    byte[] getSetHomeRequest() {
        return "SY_".getBytes();
    }

    byte[] getGoHomeRequest() {
        return "YY_".getBytes();
    }

    static byte[] getSetLedRequest(int val) {
        return String.format("J%02X_", val).getBytes();
    }

    static byte[] getCalibVoltageRequest(double realVoltage) {
        return String.format("IIOO%04X_", (int)(realVoltage*100.0)).getBytes();
    }

    static byte[] getCalibPressureRequest(double realPressure) {
        return String.format("IIPP%04X_", (int)((realPressure - 900.0)*10.0)).getBytes();
    }

    static byte[] getCalibMotorCompensationRequest(int compensation) {
        return String.format("IINN%02X_", compensation + 100).getBytes();
    }


    byte[] getGoToPointRequest(double latitude, double longitude) {
        return String.format("I%08X-%08X_", (int)(latitude *1000000.0), (int)(longitude *1000000.0)).getBytes();
    }


// "MXX_" - sends compass orientation in hex. value 0 is 0 degrees and 240 is 360 degrees. 241-255 invalid
// "NXXXXXXXX-XXXXXXXX_" - sends GPS position in hex. first lattitude, second longitude. to parse back to double, divide both values by 1000000
// "OXXXX_" - sends pressure in hex. to parse back to hPa, parse it as uint16, then divide by 10, at the end add 900.
// "PXXXX_" - sends temperature in hex. to parse back to *C, parse it as uint16, then divide by 10, at the end add (-40).


    void addByte(byte b) {
        rxBuffer.add(new Byte(b));
    }


    void parseRxBuffer() {

        boolean anyMatch = true;
        while (anyMatch || (rxBuffer.size() > LONGEST_VALID_FRAME_SIZE)) {
            anyMatch = false;

            for (int i = 0; i < parsingRules.length; i++) {

                if (isRxMatch(parsingRules[i])) {
                    anyMatch = true;
                    parseCommand(parsingRules[i].type);
                    releaseRxBuffer(parsingRules[i].pattern.length);
                }
            }

            // if no matches found and there is more bytes than longest command, something is wrong, remove one byte and try again
            if ((anyMatch == false) && (rxBuffer.size() >= LONGEST_VALID_FRAME_SIZE)){
                rxBuffer.remove(0);
            }
        }

    }


    boolean isRxMatch(RxParseRule rule) {
        if (rxBuffer.size() < rule.pattern.length) {
            return false;
        }

        for (int i = 0; i < rule.pattern.length; i++) {
            switch (rule.pattern[i]) {
                case 'X':
                    if (false == isHexDigit(rxBuffer.get(i))) {
                        return false;
                    }
                    break;
                default:
                    if (rule.pattern[i] != rxBuffer.get(i)) {
                        return false;
                    }
                    break;
            }
        }
        return true;
    }

    boolean isHexDigit(byte digit) {
        return (digit >= '0' && digit <= '9') || (digit >= 'A' && digit <= 'F');
    }

    long getValueOfHexDigit(byte digit) {
        if (digit >= '0' && digit <= '9') {
            return digit - '0';
        } else {
            return digit - 'A' + 10;
        }
    }

    long parseHexInt(int startIdx, int size) {
        long val = 0;
        for (int i = 0; i < size; i++) {
            val += Math.pow(16, size - i - 1) * getValueOfHexDigit(rxBuffer.get(startIdx + i));
        }

        return val;
    }

    void releaseRxBuffer(int numOfBytes) {
        for (int i = 0; i < numOfBytes; i++) {
            rxBuffer.remove(0);
        }
    }


    void parseCommand(int commandType) {
        ReceivedBoatMessage msg = new ReceivedBoatMessage();
        msg.type = commandType;

        switch (commandType) {
            case TYPE_COMPASS:
                msg.val1 = parseHexInt(1, 2);
                msg.val1 *= 360.0 / 240.0;
                break;
            case TYPE_GPS:
                msg.val1 = parseHexInt(1, 8);
                msg.val2 = parseHexInt(10, 8);

                msg.val1 /= 1000000.0;
                msg.val2 /= 1000000.0;
                break;
            case TYPE_VOLTAGE:
                msg.val1 = parseHexInt(1, 4);
                msg.val1 /= 100.0;
                break;

            case TYPE_PRESSURE:
                msg.val1 = parseHexInt(1, 4);
                msg.val1 /= 10.0;
                msg.val1 += 900.0;
                break;
            case TYPE_TEMPERATURE:
                msg.val1 = parseHexInt(1, 4);
                msg.val1 /= 10.0;
                msg.val1 += -40.0;
                break;
            default:
                break;
        }

        rxParsedCommands.add(msg);
    }


    public boolean isRxCommandAvailable() {
        return rxParsedCommands.size() > 0;
    }

    public ReceivedBoatMessage getBoatMessage() {
        return rxParsedCommands.poll();
    }

    public String getDirectionName(int heading)
    {
        int angles[] = {22,    67,        112,   157,       202,  247,       292,     337 };
        String names[] = {"Pn", "Pn-Wsch", "Wsch", "Pd-Wsch", "Pd", "Pd-Zach", "Zach", "Pn-Zach"};

        for(int i = 0; i < angles.length; i++)
        {
            if(heading < angles[i])
            {
                return names[i];
            }
        }

        return names[0];
    }

    public void setMaxAllowedMotorVal(int val)
    {
        maxAllowedMotorVal = val;

    }

    public void setMotorCompensation(int compensationPercent)
    {
        motorIneqalityCompensation = 1.0 + (((double) compensationPercent)/100.0);
    }

}



