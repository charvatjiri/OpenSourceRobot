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

void SerialCommand::setMotors(Servo& f, Servo& c, Servo& h)
{   
    m_motorF = &f;
    m_motorC = &c;
    m_motorH = &h;
}

void SerialCommand::Process()
{
    while (Serial.available() > 0) {
        char c = Serial.read();
        if (c == '\n' || c == '\r') {
            if (m_buffer.length() > 0) {
                /*Serial.println();
                Serial.print("Processing command: ");
                Serial.println(m_buffer);
                Serial.println();*/
                processCommand(m_buffer);
                m_buffer = "";
            }
        } else {
            m_buffer += c;
        }
    }
}

void SerialCommand::processCommand(String cmd)
{
    cmd.trim();

    if (cmd.length() == 0) {
        return;
    }

    if (cmd.equalsIgnoreCase("get version")) {
        cmdGetVersion();
    }
    else if (cmd.startsWith("grab ") || cmd.startsWith("GRAB ")) {
        cmdGrab(cmd.substring(5));
    }
    else if (cmd.startsWith("motor ") || cmd.startsWith("MOTOR ")) {
        String motor = cmd.substring(6, 7);
        motor.toLowerCase();
        int position = cmd.substring(8).toInt();
        if (motor == "f" && m_motorF) {
            cmdMotorPos(*m_motorF, position);
        } else if (motor == "c" && m_motorC) {
            cmdMotorPos(*m_motorC, position);
        } else if (motor == "h" && m_motorH) {
            cmdMotorPos(*m_motorH, position);
        } else {
            Serial.println("ERR: unknown motor");
        }
    }
    else {
        Serial.print("ERR: unknown command: ");
        Serial.println(cmd);
    }
}

void SerialCommand::cmdGetVersion()
{
    Serial.print(m_verMajor);
    Serial.print(".");
    Serial.print(m_verMinor);
    Serial.print(".");
    Serial.println(m_verMicro);
}

void SerialCommand::cmdGrab(String args)
{
    args.trim();
    int value = args.toInt();
    if (value >= 0 && value <= 255) {
        m_fingers.DoGrab((byte)value);
        Serial.print("OK grab ");
        Serial.println(value);
    } else {
        Serial.println("ERR: grab value out of range");
    }
}

void SerialCommand::cmdMotorPos(Servo& motor, int position)
{
    motor.write(position);
    Serial.print("OK motor ");
    Serial.println(position);
}
