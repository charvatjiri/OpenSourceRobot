#include <Arduino.h>
#include "OvercurrentProtection.h"

OvercurrentProtection::OvercurrentProtection()
    : m_currentPinL(A6)
    , m_currentPinR(A3)
    , m_currentPinUD(A5)
    , m_currentPinWE(A4)
    , m_overL(0)
    , m_overR(0)
    , m_overUD(0)
    , m_overWE(0)
    , m_stopL(0)
    , m_stopR(0)
    , m_stopUD(0)
    , m_stopWE(0)
{
}

OvercurrentProtection::~OvercurrentProtection()
{
}

void OvercurrentProtection::Init(uint8_t currentPinL, uint8_t currentPinR, 
                                  uint8_t currentPinUD, uint8_t currentPinWE)
{
    m_currentPinL = currentPinL;
    m_currentPinR = currentPinR;
    m_currentPinUD = currentPinUD;
    m_currentPinWE = currentPinWE;
}

void OvercurrentProtection::Update()
{
    // Check left motor current
    if (analogRead(m_currentPinL) > OVERCURRENT_THRESHOLD) {
        m_overL++;
        if (m_overL > OVERCURRENT_TIME) {
            m_stopL = MOTOR_OFF_TIME;
        }
    } else {
        m_overL = 0;
    }

    // Check right motor current
    if (analogRead(m_currentPinR) > OVERCURRENT_THRESHOLD) {
        m_overR++;
        if (m_overR > OVERCURRENT_TIME) {
            m_stopR = MOTOR_OFF_TIME;
        }
    } else {
        m_overR = 0;
    }

    // Check up/down motor current
    if (analogRead(m_currentPinUD) > OVERCURRENT_THRESHOLD) {
        m_overUD++;
        if (m_overUD > OVERCURRENT_TIME) {
            m_stopUD = MOTOR_OFF_TIME;
        }
    } else {
        m_overUD = 0;
    }

    // Check west/east motor current
    if (analogRead(m_currentPinWE) > OVERCURRENT_THRESHOLD) {
        m_overWE++;
        if (m_overWE > OVERCURRENT_TIME) {
            m_stopWE = MOTOR_OFF_TIME;
        }
    } else {
        m_overWE = 0;
    }

    // Decrement stop counters
    if (m_stopL) m_stopL--;
    if (m_stopR) m_stopR--;
    if (m_stopUD) m_stopUD--;
    if (m_stopWE) m_stopWE--;
}

bool OvercurrentProtection::IsLeftStopped()
{
    return m_stopL > 0;
}

bool OvercurrentProtection::IsRightStopped()
{
    return m_stopR > 0;
}

bool OvercurrentProtection::IsUDStopped()
{
    return m_stopUD > 0;
}

bool OvercurrentProtection::IsWEStopped()
{
    return m_stopWE > 0;
}
