#ifndef OVERCURRENT_PROTECTION_H
#define OVERCURRENT_PROTECTION_H

#include <stdint.h>

class OvercurrentProtection
{
public:
    OvercurrentProtection();
    ~OvercurrentProtection();

    void Init(uint8_t currentPinL, uint8_t currentPinR, uint8_t currentPinUD, uint8_t currentPinWE);
    void Update();

    bool IsLeftStopped();
    bool IsRightStopped();
    bool IsUDStopped();
    bool IsWEStopped();

private:
    static const uint8_t OVERCURRENT_THRESHOLD = 112;  // 0.5A
    static const uint8_t OVERCURRENT_TIME = 4;         // 400ms before stop
    static const uint8_t MOTOR_OFF_TIME = 20;          // 2s motor stop time

    uint8_t m_currentPinL;
    uint8_t m_currentPinR;
    uint8_t m_currentPinUD;
    uint8_t m_currentPinWE;

    uint8_t m_overL;
    uint8_t m_overR;
    uint8_t m_overUD;
    uint8_t m_overWE;

    uint8_t m_stopL;
    uint8_t m_stopR;
    uint8_t m_stopUD;
    uint8_t m_stopWE;
};

#endif
