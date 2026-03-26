#ifndef RADIO_CONTROL_H
#define RADIO_CONTROL_H

#include <Arduino.h>
#include <Servo.h>

// Radio element indices
#define WRIST_SERVO_C_RL 0
#define WRIST_SERVO_H_UD 1
#define ARM_SERVO_LR 2
#define ARM_SERVO_UD 3
#define WHEELS_RL 4
#define WHEELS_FB 5
#define FINGERS_GRAB 10
#define FINGERS_RELEASE 11

class Radio;
class Fingers;
class AngleSensor;
class OvercurrentProtection;
class BatteryMonitor;

struct MotorPins {
    byte LmotLF, LmotHF, LmotLB, LmotHB;
    byte RmotLF, RmotHF, RmotLB, RmotHB;
    byte motLE, motHE, motLW, motHW;
    byte motLU, motHU, motLD, motHD;
    byte buzzer;
    byte button;
    byte fotodiodeA, fotodiodeZ;
};

class RadioControl
{
public:
    RadioControl();
    ~RadioControl();

    void Init(Radio& radio, Fingers& fingers,
              AngleSensor& angle, OvercurrentProtection& overcurrent,
              BatteryMonitor& battery,
              Servo& motorC, Servo& motorH,
              byte* motorC_value, byte* motorH_value,
              const MotorPins& pins);

    void Process();

    char* GetElementPtr(int index);
    bool IsBuzRequested();
    void ClearBuzRequest();

private:
    void processRadioData();
    void processPhotodiodes();
    void processWristServos();
    void processShoulderLR();
    void processShoulderUD();
    void processWheels();
    void processFingers();

    Radio* m_radio;
    Fingers* m_fingers;
    AngleSensor* m_angle;
    OvercurrentProtection* m_overcurrent;
    BatteryMonitor* m_battery;
    Servo* m_motorC;
    Servo* m_motorH;
    byte* m_motorC_value;
    byte* m_motorH_value;

    MotorPins m_pins;

    byte m_data[2];
    char m_element[16];
    bool m_EL[16];

    word m_citRadio;
    bool m_radioOK;
    word m_radOKCounter;

    // Photodiode state
    word m_sensorLong;
    word m_sensorShort;
    bool m_holeA;
    bool m_holeZ;
    bool m_holeZA;
    int m_countA;
    int m_countZ;
    int m_countZA;
    int m_lastDir;
    int m_angle_val;
    byte m_cti;

    // Wrist servo timing
    unsigned long m_timeC;
    unsigned long m_timeH;

    // Shoulder/wheel state
    byte m_dirForw;
    boolean m_dirForwH;
    byte m_dirBack;
    boolean m_dirBackH;

    byte m_LdirForw;
    boolean m_LdirForwH;
    byte m_LdirBack;
    boolean m_LdirBackH;

    byte m_RdirForw;
    boolean m_RdirForwH;
    byte m_RdirBack;
    boolean m_RdirBackH;

    int m_part3;
    int m_slowRL;
    int m_slowFB;

    // Radio-triggered buzzer request
    bool m_buzOn;
};

#endif
