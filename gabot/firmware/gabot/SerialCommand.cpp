#include "SerialCommand.h"
#include "Fingers.h"

SerialCommand::SerialCommand(Fingers& fingers, int verMajor, int verMinor, int verMicro)
    : m_fingers(fingers)
    , m_motorF(nullptr)
    , m_motorC(nullptr)
    , m_motorH(nullptr)
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
