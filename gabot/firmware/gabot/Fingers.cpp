#include <Arduino.h>
#include "Fingers.h"

Fingers::Fingers() 
    : m_FmotLG(A10)
    , m_FmotHG(33)
    , m_FmotLR(45)
    , m_FmotHR(32)
    , m_motFR(18)
    , m_motFG(19)
    , m_currentPin(A1)
    , m_useHBridge(true)  // Use H-bridge by default (GABOT23 style)
    , m_grab(HIGH)
    , m_release(HIGH)
    , m_countG(0)
    , m_countR(0)
    , m_oldCur(false)
{
    // H-bridge pins
    pinMode(m_FmotLG, OUTPUT);
    digitalWrite(m_FmotLG, LOW);
    pinMode(m_FmotHG, OUTPUT);
    digitalWrite(m_FmotHG, LOW);
    pinMode(m_FmotLR, OUTPUT);
    digitalWrite(m_FmotLR, LOW);
    pinMode(m_FmotHR, OUTPUT);
    digitalWrite(m_FmotHR, LOW);

    // Legacy relay pins
    pinMode(m_motFG, OUTPUT);
    digitalWrite(m_motFG, HIGH);
    pinMode(m_motFR, OUTPUT);
    digitalWrite(m_motFR, HIGH);
}

Fingers::~Fingers()
{
}

void Fingers::Init(uint8_t currentPin)
{
    m_currentPin = currentPin;
}

void Fingers::TestPrint()
{
    Serial.println("Fingers::TestPrint");
}

void Fingers::DoRelease(byte value)
{
    Serial.print("Grab releasing...  ");
    Serial.println(value);

    if (m_useHBridge) {
        digitalWrite(m_FmotHG, LOW);
        digitalWrite(m_FmotLG, LOW);
        digitalWrite(m_FmotHR, !value);
        digitalWrite(m_FmotLR, !value);
    }

    m_grab = HIGH;     // grab OFF
    m_release = value; // release ON/OFF
    m_countR = 0;
}

void Fingers::DoGrab(byte value)
{
    Serial.print("Fingers::DoGrab    ");
    Serial.println(value);

    if (m_useHBridge) {
        digitalWrite(m_FmotHR, LOW);
        digitalWrite(m_FmotLR, LOW);
        digitalWrite(m_FmotHG, !value);
        digitalWrite(m_FmotLG, !value);
    }

    m_release = HIGH;  // release OFF
    m_grab = value;    // grab ON/OFF
    m_countG = 0;
}

void Fingers::FingerMotors()
{
    if (!m_useHBridge) {
        // Legacy relay-style control
        digitalWrite(m_motFG, m_grab);
        digitalWrite(m_motFR, m_release);
    }
}

void Fingers::Update()
{
    // Overcurrent protection
    int fCurrent = analogRead(m_currentPin);
    if (fCurrent > OVERCURRENT_THRESHOLD) {
        if (m_oldCur) {
            // Two consecutive overcurrent readings - stop motors
            digitalWrite(m_FmotHG, LOW);
            digitalWrite(m_FmotLG, LOW);
            digitalWrite(m_FmotHR, LOW);
            digitalWrite(m_FmotLR, LOW);
            m_grab = HIGH;
            m_release = HIGH;
        }
        m_oldCur = true;
    } else {
        m_oldCur = false;
    }

    // Timeout protection for release
    if (m_release == LOW) {
        m_countR++;
    } else {
        m_countR = 0;
    }
    if (m_countR > MAX_RUN_TIME) {
        digitalWrite(m_FmotHG, LOW);
        digitalWrite(m_FmotLG, LOW);
        digitalWrite(m_FmotHR, LOW);
        digitalWrite(m_FmotLR, LOW);
        m_release = HIGH;
        m_countR = 0;
    }

    // Timeout protection for grab
    if (m_grab == LOW) {
        m_countG++;
    } else {
        m_countG = 0;
    }
    if (m_countG > MAX_RUN_TIME) {
        digitalWrite(m_FmotHG, LOW);
        digitalWrite(m_FmotLG, LOW);
        digitalWrite(m_FmotHR, LOW);
        digitalWrite(m_FmotLR, LOW);
        m_grab = HIGH;
        m_countG = 0;
    }
}
