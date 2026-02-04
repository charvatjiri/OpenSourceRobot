#include <Arduino.h>
#include "BatteryMonitor.h"

const float BatteryMonitor::LOW_BATTERY_THRESHOLD = 10.0f;

BatteryMonitor::BatteryMonitor()
    : m_voltagePin(A0)
    , m_buzzerPin(15)
    , m_voltage(12.0f)
    , m_batteryOK(true)
    , m_buzState(false)
    , m_buzzCount(100)
{
}

BatteryMonitor::~BatteryMonitor()
{
}

void BatteryMonitor::Init(uint8_t voltagePin, uint8_t buzzerPin)
{
    m_voltagePin = voltagePin;
    m_buzzerPin = buzzerPin;
    pinMode(m_buzzerPin, OUTPUT);
    digitalWrite(m_buzzerPin, LOW);

    // Initial battery check
    int raw = analogRead(m_voltagePin);
    m_voltage = (raw * 5.0f / 1023.0f) * 4.0f;  // Assuming 4:1 voltage divider
    m_batteryOK = m_voltage >= LOW_BATTERY_THRESHOLD;

    if (!m_batteryOK) {
        m_buzzCount = 1000;  // Long beep for low battery on startup
    }
}

void BatteryMonitor::Update()
{
    int raw = analogRead(m_voltagePin);
    m_voltage = (raw * 5.0f / 1023.0f) * 4.0f;  // Assuming 4:1 voltage divider
    m_batteryOK = m_voltage >= LOW_BATTERY_THRESHOLD;

    if (!m_batteryOK) {
        m_buzState = !m_buzState;
        digitalWrite(m_buzzerPin, m_buzState);
        m_buzzCount--;
        if (m_buzzCount == 0) {
            m_batteryOK = true;
            m_buzzCount = 100;
        }
    }
}

bool BatteryMonitor::IsBatteryLow()
{
    return !m_batteryOK;
}

float BatteryMonitor::GetVoltage()
{
    return m_voltage;
}
