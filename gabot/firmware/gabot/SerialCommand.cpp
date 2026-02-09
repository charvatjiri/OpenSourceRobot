#include "SerialCommand.h"
#include "Fingers.h"

SerialCommand::SerialCommand(Fingers& fingers, int verMajor, int verMinor, int verMicro)
    : m_fingers(fingers)
    , m_motorF(nullptr)
    , m_motorC(nullptr)
    , m_motorH(nullptr)
    , m_motLE(0), m_motHE(0), m_motLW(0), m_motHW(0)
    , m_motLU(0), m_motHU(0), m_motLD(0), m_motHD(0)
    , m_buffer("")
    , m_verMajor(verMajor)
    , m_verMinor(verMinor)
    , m_verMicro(verMicro)
{
}

SerialCommand::~SerialCommand()
{
}

void SerialCommand::setMotors(Servo& f, Servo& c, Servo& h, byte* valueC, byte* valueH)
{   
    m_motorF = &f;
    m_motorC = &c;
    m_motorH = &h;
    m_motorC_value = valueC;
    m_motorH_value = valueH;
}

void SerialCommand::setShoulderPins(byte lE, byte hE, byte lW, byte hW,
                                     byte lU, byte hU, byte lD, byte hD)
{
    m_motLE = lE; m_motHE = hE; m_motLW = lW; m_motHW = hW;
    m_motLU = lU; m_motHU = hU; m_motLD = lD; m_motHD = hD;
}

int SerialCommand::Process()
{
    int returnVal = SerialCmd_None;
    while (Serial.available() > 0) {
        char c = Serial.read();
        if (c == '\n' || c == '\r') {
            if (m_buffer.length() > 0) {
                /*Serial.println();
                Serial.print("Processing command: ");
                Serial.println(m_buffer);
                Serial.println();*/
                returnVal = processCommand(m_buffer);
                m_buffer = "";
            }
        } else {
            m_buffer += c;
        }
    }
    return returnVal;
}

int SerialCommand::processCommand(String cmd)
{
    cmd.trim();

    if (cmd.length() == 0) {
        return SerialCmd_None;
    }

    int returnVal = SerialCmd_None;
    if (cmd.equalsIgnoreCase("get version")) {
        returnVal = cmdGetVersion();
    }
    else if (cmd.startsWith("grab ") || cmd.startsWith("GRAB ")) {
        returnVal = cmdGrab(cmd.substring(5));
    }
    else if (cmd.startsWith("release ") || cmd.startsWith("RELEASE ")) {
        returnVal = cmdRelease(cmd.substring(7));
    }
    else if (cmd.startsWith("wrist horizontal ") || cmd.startsWith("WRIST HORIZONTAL ")) {
        int position = cmd.substring(17).toInt();
        returnVal = cmdMotorPos(*m_motorC, m_motorC_value, position);
    }
    else if (cmd.startsWith("wrist vertical ") || cmd.startsWith("WRIST VERTICAL ")) {
        int position = cmd.substring(12).toInt();
        returnVal = cmdMotorPos(*m_motorH, m_motorH_value, position);
    }
    else if (cmd.startsWith("motor f") || cmd.startsWith("MOTOR F")) {
        int position = cmd.substring(8).toInt();
        returnVal = cmdMotorPos(*m_motorF, nullptr, position);
    }
    else if (cmd.startsWith("shoulder horizontal ") || cmd.startsWith("SHOULDER HORIZONTAL ")) {
        returnVal = cmdShoulderHorizontal(cmd.substring(20));
    }
    else if (cmd.startsWith("shoulder vertical ") || cmd.startsWith("SHOULDER VERTICAL ")) {
        returnVal = cmdShoulderVertical(cmd.substring(18));
    }
    else {
        returnVal = SerialCmd_Error;
        Serial.print("ERR: unknown command: ");
        Serial.println(cmd);
    }
    return returnVal;
}

int SerialCommand::cmdGetVersion()
{
    Serial.print(m_verMajor);
    Serial.print(".");
    Serial.print(m_verMinor);
    Serial.print(".");
    Serial.println(m_verMicro);
    return SerialCmd_Success;
}

int SerialCommand::cmdGrab(String args)
{
    args.trim();
    int value = args.toInt();
    if (value >= 0 && value <= 255) {
        m_fingers.DoGrab((byte)value);
        Serial.print("OK grab ");
        Serial.println(value);
        return SerialCmd_Success;
    } else {
        Serial.println("ERR: grab value out of range");
        return SerialCmd_Error;
    }
}

int SerialCommand::cmdRelease(String args)
{
    args.trim();
    int value = args.toInt();
    if (value >= 0 && value <= 255) {
        m_fingers.DoRelease((byte)value);
        Serial.print("OK release ");
        Serial.println(value);
        return SerialCmd_Success;
    } else {
        Serial.println("ERR: grab value out of range");
        return SerialCmd_Error;
    }
}

int SerialCommand::cmdMotorPos(Servo& motor, byte* valuePtr, int position)
{
    motor.write(position);
    if (valuePtr != nullptr) {
        *valuePtr = (byte)position;
    }
    Serial.print("OK motor ");
    Serial.println(position);
    return SerialCmd_Success;
}

int SerialCommand::cmdShoulderHorizontal(String args)
{
    args.trim();
    int speed = args.toInt();
    if (speed < -255 || speed > 255) {
        Serial.println("ERR: shoulder horizontal value out of range (-255..255)");
        return SerialCmd_Error;
    }
    if (speed > 0) {
        digitalWrite(m_motHW, LOW);
        analogWrite(m_motLW, 0);
        digitalWrite(m_motHE, HIGH);
        analogWrite(m_motLE, speed);
    } else if (speed < 0) {
        digitalWrite(m_motHE, LOW);
        analogWrite(m_motLE, 0);
        digitalWrite(m_motHW, HIGH);
        analogWrite(m_motLW, -speed);
    } else {
        digitalWrite(m_motHE, LOW);
        analogWrite(m_motLE, 0);
        digitalWrite(m_motHW, LOW);
        analogWrite(m_motLW, 0);
    }
    Serial.print("OK shoulder horizontal ");
    Serial.println(speed);
    return SerialCmd_Success;
}

int SerialCommand::cmdShoulderVertical(String args)
{
    args.trim();
    int speed = args.toInt();
    if (speed < -255 || speed > 255) {
        Serial.println("ERR: shoulder vertical value out of range (-255..255)");
        return SerialCmd_Error;
    }
    if (speed > 0) {
        digitalWrite(m_motHD, LOW);
        analogWrite(m_motLD, 0);
        digitalWrite(m_motHU, HIGH);
        analogWrite(m_motLU, speed);
    } else if (speed < 0) {
        digitalWrite(m_motHU, LOW);
        analogWrite(m_motLU, 0);
        digitalWrite(m_motHD, HIGH);
        analogWrite(m_motLD, -speed);
    } else {
        digitalWrite(m_motHU, LOW);
        analogWrite(m_motLU, 0);
        digitalWrite(m_motHD, LOW);
        analogWrite(m_motLD, 0);
    }
    Serial.print("OK shoulder vertical ");
    Serial.println(speed);
    return SerialCmd_Success;
}
