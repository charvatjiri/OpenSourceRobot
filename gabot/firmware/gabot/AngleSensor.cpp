#include <Arduino.h>
#include <Wire.h>
#include "AS5600.h"
#include "AngleSensor.h"

AS5600 as5600;

AngleSensor::AngleSensor()
    : m_angle(0)
{
}

AngleSensor::~AngleSensor()
{
}

void AngleSensor::Init(uint8_t directionPin)
{
    Wire.begin();
    as5600.begin(directionPin);
    as5600.setDirection(AS5600_CLOCK_WISE);
}

int AngleSensor::ReadAngle()
{
    m_angle = as5600.readAngle();
    m_angle = m_angle - ANGLE_OFFSET;

    // normalize angle to -2047 to 2047
    if (m_angle < -2047) {
        m_angle = m_angle + 4096;
    }
    if (m_angle > 2047) {
        m_angle = m_angle - 4096;
    }

    return m_angle;
}

bool AngleSensor::IsAtLimit()
{
    return (m_angle < -ANGLE_LIMIT) || (m_angle > ANGLE_LIMIT);
}

bool AngleSensor::IsAtEastLimit()
{
    return m_angle > ANGLE_LIMIT;
}

bool AngleSensor::IsAtWestLimit()
{
    return m_angle < -ANGLE_LIMIT;
}
