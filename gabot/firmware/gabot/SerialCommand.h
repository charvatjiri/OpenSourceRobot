#ifndef SERIAL_COMMAND_H
#define SERIAL_COMMAND_H

#include <Arduino.h>

class Fingers;

class SerialCommand
{
public:
    SerialCommand(Fingers& fingers, int verMajor, int verMinor, int verMicro);
    ~SerialCommand();

    void Process();

private:
    void processCommand(String cmd);
    void cmdGetVersion();
    void cmdGrab(String args);

    Fingers& m_fingers;
    String m_buffer;
    int m_verMajor;
    int m_verMinor;
    int m_verMicro;
};

#endif
