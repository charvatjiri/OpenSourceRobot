#include "SerialCommand.h"
#include "Fingers.h"

SerialCommand::SerialCommand(Fingers& fingers, int verMajor, int verMinor, int verMicro)
    : m_fingers(fingers)
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
    m_motorF = f;
    m_motorC = c;
    m_motorH = h;
}

void SerialCommand::Process()
{
    while (Serial.available()) {
        char c = Serial.read();
        if (c == '\n' || c == '\r') {
            if (m_buffer.length() > 0) {
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

    if (cmd == "get version") {
        cmdGetVersion();
    }
    else if (cmd.startsWith("grab ")) {
        cmdGrab(cmd.substring(5));
    }
    else if (cmd.startsWith("motor ")) {
        String motor = cmd.substring(7,7);
        int position = cmd.substring(9).toInt();
        if (motor == "f") {
            cmdMotorPos(m_motorF, position);
        } else if (motor == "c") {
            cmdMotorPos(m_motorC, position);
        } else if (motor == "h") {
            cmdMotorPos(m_motorH, position);
        }
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
    }
}

void SerialCommand::cmdMotorPos(Servo& motor, int position)
{
    motor.write(position);
}
