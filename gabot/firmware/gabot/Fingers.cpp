#include <Arduino.h>
#include "Fingers.h"

Fingers::Fingers() : m_motFR(18), m_motFG(19)
{
    m_release = HIGH;
    m_grab = HIGH;

    pinMode(m_motFG, OUTPUT);
    digitalWrite(m_motFG, HIGH);
    pinMode(m_motFR, OUTPUT);
    digitalWrite(m_motFR, HIGH);
};

Fingers::~Fingers()
{
};

void Fingers::TestPrint()
{
  Serial.println("Fingers::TestPrint");
}

void Fingers::DoRelease(byte value)
{
  Serial.print("Grab releasing...  ");
  Serial.println(value);

    m_grab = HIGH; //grab OFF
    m_release = value; //release ON/OFF
}

void Fingers::DoGrab(byte value) {
    Serial.print("Fingers::DoGrab    ");
    Serial.println(value);

    m_release = HIGH; //release OFF
    m_grab = value; //grab ON/OFF
}
void Fingers::FingerMotors()
{
    digitalWrite(m_motFG, m_grab);
    digitalWrite(m_motFR, m_release);
}
