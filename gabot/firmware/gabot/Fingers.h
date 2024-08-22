#include <stdint.h>

class Fingers
{
public: 
    Fingers();
    ~Fingers();

    void DoGrab(byte value);
    void DoRelease(byte value);
    void TestPrint();

    void FingerMotors();

private:
    uint32_t m_open_percent; // 0 ... 100

    const byte m_motFR;  //release/grab motor
    const byte m_motFG;  //release/grab motor

    byte m_grab;
    byte m_release;
};
