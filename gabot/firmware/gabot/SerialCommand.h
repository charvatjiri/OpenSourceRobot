#ifndef SERIAL_COMMAND_H
#define SERIAL_COMMAND_H

#include <Arduino.h>
#include <Servo.h>

class Fingers;
class Servo;

typedef enum {
    SerialCmd_None = 0,
    SerialCmd_Success = 1,
    SerialCmd_Error = 2,
} SerialCmdResult;

class SerialCommand
{
public:
    SerialCommand(Fingers& fingers, int verMajor, int verMinor, int verMicro);
    ~SerialCommand();

    void setMotors(Servo& f, Servo& c, Servo& h, byte* valueC, byte* valueH);
    void setShoulderPins(byte lE, byte hE, byte lW, byte hW,
                         byte lU, byte hU, byte lD, byte hD);

    int Process();

private:

    int processCommand(String cmd);
    int cmdGetVersion();
    int cmdGrab(String args);
    int cmdRelease(String args);
    int cmdMotorPos(Servo& motor, byte* valuePtr, int position);
    int cmdShoulderHorizontal(String args);
    int cmdShoulderVertical(String args);

    Fingers& m_fingers;
    Servo* m_motorF;
    Servo* m_motorC;
    Servo* m_motorH;
    byte* m_motorC_value;
    byte* m_motorH_value;

    byte m_motLE, m_motHE, m_motLW, m_motHW;
    byte m_motLU, m_motHU, m_motLD, m_motHD;

    String m_buffer;
    int m_verMajor;
    int m_verMinor;
    int m_verMicro;
};

#endif
