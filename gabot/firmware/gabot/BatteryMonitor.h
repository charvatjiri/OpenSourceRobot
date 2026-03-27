#ifndef BATTERY_MONITOR_H
#define BATTERY_MONITOR_H

#include <stdint.h>

class BatteryMonitor
{
public:
    BatteryMonitor();
    ~BatteryMonitor();

    void Init(uint8_t voltagePin, uint8_t buzzerPin);
    float Update();
    bool IsBatteryLow();
    float GetVoltage();

private:
    static const float LOW_BATTERY_THRESHOLD;  // 10V
    static const float BATTERY_CHANGE_THRESHOLD;

    uint8_t m_voltagePin;
    uint8_t m_buzzerPin;
    float m_voltage;
    float m_prev_voltage;
    bool m_batteryOK;
    bool m_buzState;
    uint16_t m_buzzCount;
};

#endif
