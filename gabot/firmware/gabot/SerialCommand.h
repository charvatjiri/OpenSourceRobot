#ifndef SERIAL_COMMAND_H
#define SERIAL_COMMAND_H

#include <Arduino.h>
#include <Servo.h>

class Fingers;
class Servo;

class SerialCommand
{
public:
    SerialCommand(Fingers& fingers, int verMajor, int verMinor, int verMicro);
    ~SerialCommand();

    void setMotors(Servo& f, Servo& c, Servo& h);

    void Process();

private:

    void processCommand(String cmd);
    void cmdGetVersion();
    void cmdGrab(String args);
    void cmdMotorPos(Servo& motor, int position);

    Fingers& m_fingers;
    Servo& m_motorF;
    Servo& m_motorC;
    Servo& m_motorH;
    String m_buffer;
    int m_verMajor;
    int m_verMinor;
    int m_verMicro;
};

#endif
