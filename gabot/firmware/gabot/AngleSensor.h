#ifndef ANGLE_SENSOR_H
#define ANGLE_SENSOR_H

#include <stdint.h>

class AngleSensor
{
public:
    AngleSensor();
    ~AngleSensor();

    void Init(uint8_t directionPin);
    int ReadAngle();
    bool IsAtLimit();
    bool IsAtEastLimit();
    bool IsAtWestLimit();

private:
    static const int ANGLE_OFFSET = 1328;  // angle correction when arm is forward
    static const int ANGLE_LIMIT = 2000;   // limit for arm movement
    int m_angle;
};

#endif
