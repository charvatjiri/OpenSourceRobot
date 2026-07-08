#include "SerialCommand.h"
#include "Fingers.h"
#include <limits.h>
#include <stdlib.h>

SerialCommand::SerialCommand(Fingers& fingers, int verMajor, int verMinor, int verMicro)
    : m_fingers(fingers)
    , m_motorF(nullptr)
    , m_motorC(nullptr)
    , m_motorH(nullptr)
    , m_motorC_value(nullptr)
    , m_motorH_value(nullptr)
    , m_motLE(0), m_motHE(0), m_motLW(0), m_motHW(0)
    , m_motLU(0), m_motHU(0), m_motLD(0), m_motHD(0)
    , m_elementRL(nullptr)
    , m_elementFB(nullptr)
    , m_buffer("")
    , m_discardInput(false)
    , m_verMajor(verMajor)
    , m_verMinor(verMinor)
    , m_verMicro(verMicro)
{
    m_buffer.reserve(MAX_COMMAND_LENGTH);
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

void SerialCommand::setWheelElements(char* elementRL, char* elementFB)
{
    m_elementRL = elementRL;
    m_elementFB = elementFB;
}

int SerialCommand::Process()
{
    int returnVal = SerialCmd_None;
    while (Serial.available() > 0) {
        char c = Serial.read();
        if (c == '\n' || c == '\r') {
            if (m_discardInput) {
                m_discardInput = false;
                m_buffer = "";
            } else if (m_buffer.length() > 0) {
                returnVal = processCommand(m_buffer);
                m_buffer = "";
            }
        } else if (m_discardInput) {
            continue;
        } else if (c < 0x20 || c > 0x7e) {
            Serial.println(F("ERR: command contains invalid character"));
            m_buffer = "";
            m_discardInput = true;
            returnVal = SerialCmd_Error;
        } else if (m_buffer.length() >= MAX_COMMAND_LENGTH) {
            Serial.println(F("ERR: command too long"));
            m_buffer = "";
            m_discardInput = true;
            returnVal = SerialCmd_Error;
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

    String normalized = cmd;
    normalized.toLowerCase();

    int returnVal = SerialCmd_None;
    if (normalized == "get version" || normalized == "version") {
        returnVal = cmdGetVersion();
    }
    else if (normalized.startsWith("grab ")) {
        returnVal = cmdGrab(cmd.substring(5));
    }
    else if (normalized.startsWith("release ")) {
        returnVal = cmdRelease(cmd.substring(8));
    }
    else if (normalized.startsWith("wrist horizontal ")) {
        int position;
        if (!m_motorC || !parseInteger(cmd.substring(17), position)) {
            Serial.println(F("ERR: invalid wrist horizontal value"));
            return SerialCmd_Error;
        }
        returnVal = cmdMotorPos(*m_motorC, m_motorC_value, position);
    }
    else if (normalized.startsWith("wrist vertical ")) {
        int position;
        if (!m_motorH || !parseInteger(cmd.substring(15), position)) {
            Serial.println(F("ERR: invalid wrist vertical value"));
            return SerialCmd_Error;
        }
        returnVal = cmdMotorPos(*m_motorH, m_motorH_value, position);
    }
    else if (normalized.startsWith("motor f ")) {
        int position;
        if (!m_motorF || !parseInteger(cmd.substring(8), position)) {
            Serial.println(F("ERR: invalid motor f value"));
            return SerialCmd_Error;
        }
        returnVal = cmdMotorPos(*m_motorF, nullptr, position);
    }
    else if (normalized.startsWith("shoulder horizontal ")) {
        returnVal = cmdShoulderHorizontal(cmd.substring(20));
    }
    else if (normalized.startsWith("shoulder vertical ")) {
        returnVal = cmdShoulderVertical(cmd.substring(18));
    }
    else if (normalized.startsWith("wheels rl ")) {
        returnVal = cmdWheelsRL(cmd.substring(10));
    }
    else if (normalized.startsWith("wheels fb ")) {
        returnVal = cmdWheelsFB(cmd.substring(10));
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
    Serial.println("OK get version");
    Serial.print(m_verMajor);
    Serial.print(".");
    Serial.print(m_verMinor);
    Serial.print(".");
    Serial.println(m_verMicro);
    return SerialCmd_Success;
}

int SerialCommand::cmdGrab(String args)
{
    int value;
    if (!parseInteger(args, value)) {
        Serial.println(F("ERR: invalid grab value"));
        return SerialCmd_Error;
    }
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
    int value;
    if (!parseInteger(args, value)) {
        Serial.println(F("ERR: invalid release value"));
        return SerialCmd_Error;
    }
    if (value >= 0 && value <= 255) {
        m_fingers.DoRelease((byte)value);
        Serial.print("OK release ");
        Serial.println(value);
        return SerialCmd_Success;
    } else {
        Serial.println(F("ERR: release value out of range"));
        return SerialCmd_Error;
    }
}

int SerialCommand::cmdMotorPos(Servo& motor, byte* valuePtr, int position)
{
    if (position < 0 || position > 180) {
        Serial.println(F("ERR: motor position out of range (0..180)"));
        return SerialCmd_Error;
    }
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
    int speed;
    if (!parseInteger(args, speed)) {
        Serial.println(F("ERR: invalid shoulder horizontal value"));
        return SerialCmd_Error;
    }
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
    int speed;
    if (!parseInteger(args, speed)) {
        Serial.println(F("ERR: invalid shoulder vertical value"));
        return SerialCmd_Error;
    }
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

int SerialCommand::cmdWheelsRL(String args)
{
    int speed;
    if (!parseInteger(args, speed)) {
        Serial.println(F("ERR: invalid wheels rl value"));
        return SerialCmd_Error;
    }
    if (speed < -127 || speed > 127) {
        Serial.println("ERR: wheels rl value out of range (-127..127)");
        return SerialCmd_Error;
    }
    if (!m_elementRL) {
        Serial.println(F("ERR: wheels rl control is unavailable"));
        return SerialCmd_Error;
    }
    *m_elementRL = (char)speed;
    Serial.print("OK wheels rl ");
    Serial.println(speed);
    return SerialCmd_Success;
}

int SerialCommand::cmdWheelsFB(String args)
{
    int speed;
    if (!parseInteger(args, speed)) {
        Serial.println(F("ERR: invalid wheels fb value"));
        return SerialCmd_Error;
    }
    if (speed < -127 || speed > 127) {
        Serial.println("ERR: wheels fb value out of range (-127..127)");
        return SerialCmd_Error;
    }
    if (!m_elementFB) {
        Serial.println(F("ERR: wheels fb control is unavailable"));
        return SerialCmd_Error;
    }
    *m_elementFB = (char)speed;
    Serial.print("OK wheels fb ");
    Serial.println(speed);
    return SerialCmd_Success;
}

bool SerialCommand::parseInteger(const String& args, int& value)
{
    String input = args;
    input.trim();
    if (input.length() == 0) {
        return false;
    }

    const char* start = input.c_str();
    char* end = nullptr;
    long parsed = strtol(start, &end, 10);
    if (end == start || *end != '\0' || parsed < INT_MIN || parsed > INT_MAX) {
        return false;
    }

    value = (int)parsed;
    return true;
}
