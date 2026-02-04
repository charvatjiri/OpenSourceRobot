#ifndef FINGERS_H
#define FINGERS_H

#include <stdint.h>

class Fingers
{
public: 
    Fingers();
    ~Fingers();

    void Init(uint8_t currentPin);
    void DoGrab(byte value);
    void DoRelease(byte value);
    void TestPrint();

    void FingerMotors();
    void Update();  // Call every 100ms for timeout and current protection

private:
    static const uint8_t MAX_RUN_TIME = 33;      // 3.3s max run time
    static const uint8_t OVERCURRENT_THRESHOLD = 35;

    uint32_t m_open_percent; // 0 ... 100

    // H-bridge control pins (GABOT23 style)
    const byte m_FmotLG;  // Finger Motor Low Grab
    const byte m_FmotHG;  // Finger Motor High Grab
    const byte m_FmotLR;  // Finger Motor Low Release
    const byte m_FmotHR;  // Finger Motor High Release

    // Legacy relay-style pins (GABOT2 fallback)
    const byte m_motFR;  // release/grab motor
    const byte m_motFG;  // release/grab motor

    uint8_t m_currentPin;
    bool m_useHBridge;

    byte m_grab;
    byte m_release;

    byte m_countG;  // counter for grab timeout
    byte m_countR;  // counter for release timeout
    bool m_oldCur;  // previous overcurrent state
};

#endif
