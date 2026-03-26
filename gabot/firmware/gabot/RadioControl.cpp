#include "RadioControl.h"
#include "Radio.h"
#include "Fingers.h"
#include "AngleSensor.h"
#include "OvercurrentProtection.h"
#include "BatteryMonitor.h"
#include <avr/wdt.h>

RadioControl::RadioControl()
    : m_radio(nullptr)
    , m_fingers(nullptr)
    , m_angle(nullptr)
    , m_overcurrent(nullptr)
    , m_battery(nullptr)
    , m_motorC(nullptr)
    , m_motorH(nullptr)
    , m_motorC_value(nullptr)
    , m_motorH_value(nullptr)
    , m_citRadio(0)
    , m_radioOK(false)
    , m_radOKCounter(0)
    , m_sensorLong(0)
    , m_sensorShort(0)
    , m_holeA(false)
    , m_holeZ(false)
    , m_holeZA(false)
    , m_countA(0)
    , m_countZ(0)
    , m_countZA(0)
    , m_lastDir(0)
    , m_angle_val(0)
    , m_cti(0)
    , m_timeC(0)
    , m_timeH(0)
    , m_dirForw(0)
    , m_dirForwH(LOW)
    , m_dirBack(0)
    , m_dirBackH(LOW)
    , m_LdirForw(0)
    , m_LdirForwH(LOW)
    , m_LdirBack(0)
    , m_LdirBackH(LOW)
    , m_RdirForw(0)
    , m_RdirForwH(LOW)
    , m_RdirBack(0)
    , m_RdirBackH(LOW)
    , m_part3(0)
    , m_slowRL(0)
    , m_slowFB(0)
    , m_buzOn(false)
{
    memset(m_data, 0, sizeof(m_data));
    memset(m_element, 0, sizeof(m_element));
    memset(m_EL, 0, sizeof(m_EL));
    memset(&m_pins, 0, sizeof(m_pins));
}

RadioControl::~RadioControl()
{
}

void RadioControl::Init(Radio& radio, Fingers& fingers,
                         AngleSensor& angle, OvercurrentProtection& overcurrent,
                         BatteryMonitor& battery,
                         Servo& motorC, Servo& motorH,
                         byte* motorC_value, byte* motorH_value,
                         const MotorPins& pins)
{
    m_radio = &radio;
    m_fingers = &fingers;
    m_angle = &angle;
    m_overcurrent = &overcurrent;
    m_battery = &battery;
    m_motorC = &motorC;
    m_motorH = &motorH;
    m_motorC_value = motorC_value;
    m_motorH_value = motorH_value;
    m_pins = pins;
}

char* RadioControl::GetElementPtr(int index)
{
    if (index >= 0 && index < 16) {
        return &m_element[index];
    }
    return nullptr;
}

bool RadioControl::IsBuzRequested()
{
    return m_buzOn;
}

void RadioControl::ClearBuzRequest()
{
    m_buzOn = false;
}

void RadioControl::Process()
{
    processRadioData();
    processPhotodiodes();
    processWristServos();
    processShoulderLR();
    processShoulderUD();
    processWheels();
    processFingers();
}

void RadioControl::processRadioData()
{
    if (m_radioOK == 1) {
        m_citRadio = 0;
    }
    m_radioOK = 0;
    m_citRadio++;
    if (m_citRadio > 6000) {  // ~0.5 s
        m_citRadio = 0;
        m_radio->Restart();
        m_buzOn = 1;
    }

    while (m_radio->Available()) {
        wdt_reset();  // needed: loop can iterate many times with queued packets
        m_radio->Read(m_data);
        if ((m_data[0] == 0x55) && (m_data[1] == 0x55)) {
            m_radOKCounter++;
            m_radioOK = 1;
        } else {
            m_data[0] = m_data[0] & 0x0F;
            m_EL[m_data[0]] = HIGH;
            m_element[m_data[0]] = m_data[1];
        }
    }

    while (digitalRead(m_pins.button) == 0) {
        // intentionally no wdt_reset() — button hold triggers WDT reset
    }
}

void RadioControl::processPhotodiodes()
{
    m_sensorShort = analogRead(m_pins.fotodiodeA);
    m_sensorLong = analogRead(m_pins.fotodiodeZ);

    m_cti = 0;
    if ((m_holeA == 0) && (m_sensorShort > 30)) {
        m_holeA = 1;
        m_countA = m_countA + m_lastDir;
        if (m_cti == 0) {
            Serial.print(" angleA =");
            Serial.println((m_countA * 15));
        }
        if (m_countZ) {
            m_countZ = m_countZ + m_lastDir;
            m_countZA++;
        }
    }

    if ((m_holeA == 1) && (m_sensorShort < 20)) {
        m_holeA = 0;
    }
    if ((m_holeZ == 0) && (m_sensorLong > 30)) {
        m_holeZA = 1;
        m_holeZ = 1;
        m_countZ = m_countZ + m_lastDir;
        m_countZA = 1;
    }

    if ((m_holeZ == 1) && (m_sensorLong < 20)) {
        if (m_countZA == abs(m_countZ)) {
            if (m_countZ == 2) {
                m_angle_val = 15;
                m_countA = 1;
                m_countZA = 1;
            }
            if (m_countZ == 4) {
                m_angle_val = -60;
                m_countA = -5;
                m_countZA = -5;
            }
            if (m_countZ == 3) {
                m_angle_val = 120;
                m_countA = 8;
                m_countZA = 8;
            }
        }
        if (m_lastDir == -1) {
            if (m_countZ == -2) {
                m_angle_val = 0;
                m_countA = 0;
                m_countZA = 0;
            }
            if (m_countZ == -3) {
                m_angle_val = 90;
                m_countA = 6;
                m_countZA = 6;
            }
            if (m_countZ == -4) {
                m_angle_val = -105;
                m_countA = -7;
                m_countZA = -7;
            }
        }
        m_holeZ = 0;
    }
    if (m_holeZ == 0) {
        m_countZA = 0;
        m_countZ = 0;
    }
    m_cti++;
}

void RadioControl::processWristServos()
{
    // Wrist servo C (Right-Left Rotate)
    if (m_EL[WRIST_SERVO_C_RL] == HIGH) {
        m_EL[WRIST_SERVO_C_RL] = 0;
    }
    if (millis() > m_timeC) {
        m_timeC = millis() + (138 - abs(m_element[WRIST_SERVO_C_RL]));
        if ((m_element[WRIST_SERVO_C_RL] > 0) && (*m_motorC_value < 166)) {
            (*m_motorC_value)++;
        }
        if ((m_element[WRIST_SERVO_C_RL] < 0) && (*m_motorC_value > 0)) {
            (*m_motorC_value)--;
        }
        m_motorC->write(*m_motorC_value);
    }

    // Wrist servo H (Up-Down)
    if (m_EL[WRIST_SERVO_H_UD] == HIGH) {
        m_EL[WRIST_SERVO_H_UD] = 0;
    }
    if (millis() > m_timeH) {
        m_timeH = millis() + (138 - abs(m_element[WRIST_SERVO_H_UD]));
        if ((m_element[WRIST_SERVO_H_UD] > 0) && (*m_motorH_value < 166)) {
            (*m_motorH_value)++;
        }
        if ((m_element[WRIST_SERVO_H_UD] < 0) && (*m_motorH_value > 0)) {
            (*m_motorH_value)--;
        }
        m_motorH->write(*m_motorH_value);
    }
}

void RadioControl::processShoulderLR()
{
    if (m_EL[ARM_SERVO_LR] == HIGH) {
        m_EL[ARM_SERVO_LR] = 0;

        if (m_element[ARM_SERVO_LR] < 0) {
            m_dirForw = 0;
            m_dirBack = 1;
            m_dirForwH = 0;
            m_dirBackH = 1;
            m_part3 = m_element[ARM_SERVO_LR] * (-1);
            m_lastDir = -1;
        } else if (m_element[ARM_SERVO_LR] == 0) {
            m_dirForw = 0;
            m_dirBack = 0;
            m_dirForwH = 0;
            m_dirBackH = 0;
            m_part3 = 0;
        } else {
            m_dirForw = 1;
            m_dirBack = 0;
            m_dirForwH = 1;
            m_dirBackH = 0;
            m_part3 = m_element[ARM_SERVO_LR];
            m_lastDir = 1;
        }
        digitalWrite(m_pins.motHE, m_dirForwH);
        analogWrite(m_pins.motLE, m_part3 * m_dirForw);
        digitalWrite(m_pins.motHW, m_dirBackH);
        analogWrite(m_pins.motLW, m_part3 * m_dirBack);
    }

    // Angle limit protection using AS5600 sensor
    if (m_angle->IsAtEastLimit() || m_overcurrent->IsWEStopped()) {
        analogWrite(m_pins.motLE, 0);
        digitalWrite(m_pins.motHE, 0);
        m_dirForw = 0;
        m_dirForwH = 0;
    }
    if (m_angle->IsAtWestLimit() || m_overcurrent->IsWEStopped()) {
        analogWrite(m_pins.motLW, 0);
        digitalWrite(m_pins.motHW, 0);
        m_dirBack = 0;
        m_dirBackH = 0;
    }
}

void RadioControl::processShoulderUD()
{
    if (m_EL[ARM_SERVO_UD] == HIGH) {
        m_EL[ARM_SERVO_UD] = 0;

        if (m_element[ARM_SERVO_UD] < 0) {
            m_dirForw = 0;
            m_dirBack = 1;
            m_dirForwH = 0;
            m_dirBackH = 1;
            m_part3 = m_element[ARM_SERVO_UD] * (-2);
        } else if (m_element[ARM_SERVO_UD] == 0) {
            m_dirForw = 0;
            m_dirBack = 0;
            m_dirForwH = 0;
            m_dirBackH = 0;
            m_part3 = 0;
        } else {
            m_dirForw = 1;
            m_dirBack = 0;
            m_dirForwH = 1;
            m_dirBackH = 0;
            m_part3 = m_element[ARM_SERVO_UD] * 2;
        }
        // UD motor with overcurrent protection
        if (!m_overcurrent->IsUDStopped()) {
            digitalWrite(m_pins.motHU, m_dirForwH);
            analogWrite(m_pins.motLU, m_part3 * m_dirForw);
            digitalWrite(m_pins.motHD, m_dirBackH);
            analogWrite(m_pins.motLD, m_part3 * m_dirBack);
        } else {
            digitalWrite(m_pins.motHU, 0);
            analogWrite(m_pins.motLU, 0);
            digitalWrite(m_pins.motHD, 0);
            analogWrite(m_pins.motLD, 0);
        }
    }
}

void RadioControl::processWheels()
{
    if (m_EL[WHEELS_RL] == HIGH) {
        m_EL[WHEELS_RL] = 0;
    }
    if (m_EL[WHEELS_FB] == HIGH) {
        m_EL[WHEELS_FB] = 0;
    }
    if (m_element[WHEELS_RL] > m_slowRL) {
        m_slowRL++;
    }
    if (m_element[WHEELS_RL] < m_slowRL) {
        m_slowRL--;
    }
    if (m_element[WHEELS_FB] > m_slowFB) {
        m_slowFB++;
    }
    if (m_element[WHEELS_FB] < m_slowFB) {
        m_slowFB--;
    }

    int rl_a = abs(m_slowRL);
    int sign_rl = (m_slowRL != 0) ? (rl_a / m_slowRL) : 0;
    int fb_a = abs(m_slowFB);
    int sign_fb = (m_slowFB != 0) ? (fb_a / m_slowFB) : 0;

    int fb, rl;
    if ((fb_a + rl_a) > 127) {
        fb = (fb_a * 127 / (fb_a + rl_a));
        rl = (rl_a * 127 / (fb_a + rl_a));
    } else {
        fb = fb_a;
        rl = rl_a;
    }
    fb = sign_fb * 2 * fb;
    rl = sign_rl * 2 * rl;

    if ((fb - rl) > 0) {
        m_RdirForw = 1; m_RdirBack = 0;
        m_RdirForwH = 1; m_RdirBackH = 0;
    } else if ((fb - rl) == 0) {
        m_RdirForw = 0; m_RdirBack = 0;
        m_RdirForwH = 0; m_RdirBackH = 0;
    } else {
        m_RdirForw = 0; m_RdirBack = 1;
        m_RdirForwH = 0; m_RdirBackH = 1;
    }
    if ((fb + rl) > 0) {
        m_LdirForw = 1; m_LdirBack = 0;
        m_LdirForwH = 1; m_LdirBackH = 0;
    } else if ((fb + rl) == 0) {
        m_LdirForw = 0; m_LdirBack = 0;
        m_LdirForwH = 0; m_LdirBackH = 0;
    } else {
        m_LdirForw = 0; m_LdirBack = 1;
        m_LdirForwH = 0; m_LdirBackH = 1;
    }

    // Left motor with overcurrent protection
    if (m_overcurrent->IsLeftStopped()) {
        digitalWrite(m_pins.LmotHF, 0);
        analogWrite(m_pins.LmotLF, 0);
        digitalWrite(m_pins.LmotHB, 0);
        analogWrite(m_pins.LmotLB, 0);
    } else {
        digitalWrite(m_pins.LmotHF, m_LdirForwH);
        analogWrite(m_pins.LmotLF, abs(fb + rl) * m_LdirForw);
        digitalWrite(m_pins.LmotHB, m_LdirBackH);
        analogWrite(m_pins.LmotLB, abs(fb + rl) * m_LdirBack);
    }

    // Right motor with overcurrent protection
    if (m_overcurrent->IsRightStopped()) {
        digitalWrite(m_pins.RmotHF, 0);
        analogWrite(m_pins.RmotLF, 0);
        digitalWrite(m_pins.RmotHB, 0);
        analogWrite(m_pins.RmotLB, 0);
    } else {
        digitalWrite(m_pins.RmotHF, m_RdirForwH);
        analogWrite(m_pins.RmotLF, abs(fb - rl) * m_RdirForw);
        digitalWrite(m_pins.RmotHB, m_RdirBackH);
        analogWrite(m_pins.RmotLB, abs(fb - rl) * m_RdirBack);
    }

    if (m_EL[7] == HIGH) { m_EL[7] = 0; }
    if (m_EL[8] == HIGH) { m_EL[8] = 0; }
    if (m_EL[9] == HIGH) { m_EL[9] = 0; }
}

void RadioControl::processFingers()
{
    if (m_EL[FINGERS_GRAB] == HIGH) {
        m_EL[FINGERS_GRAB] = 0;
        m_fingers->DoGrab(m_data[1]);
    }
    if (m_EL[FINGERS_RELEASE] == HIGH) {
        m_EL[FINGERS_RELEASE] = 0;
        m_fingers->DoRelease(m_data[1]);
    }

    m_fingers->FingerMotors();
}


