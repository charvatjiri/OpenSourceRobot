#include <Arduino.h>
#include <RF24.h>
#include "Radio.h"

#define CE 49
#define CS 48
const byte vysilac[] = "TX001";
const byte prijimac[] = "RX001";
const int kanal = 120;

Radio::Radio() : m_radio(CE, CS), m_ok(false)
{
}

Radio::~Radio()
{
}

void Radio::Init()
{
  if (!m_radio.begin()) {
  Serial.println(F("radio hardware is not responding!!"));
    m_ok = false;
    return;
  }
  m_ok = true;
  m_radio.setDataRate(RF24_1MBPS);
  m_radio.setPALevel(RF24_PA_MIN);
  m_radio.setChannel(kanal);
  m_radio.openWritingPipe(prijimac);
  m_radio.openReadingPipe(1, vysilac);
  m_radio.startListening();
}

extern  word rad_OK_counter;
extern bool RadioOK;

bool Radio::Available()
{
  if (!m_ok) return false;
  return m_radio.available();
}

void Radio::Read(byte* result)
{
    if (!m_ok) return;
    m_radio.read(result, 2);
}

void Radio::Restart()
{
    if (!m_ok) return;
    m_radio.setChannel(kanal);
    m_radio.openWritingPipe(prijimac);
    m_radio.openReadingPipe(1, vysilac);
    m_radio.startListening();
    Serial.print("Radio restarted, rad_OK_counter= ");
    Serial.println(rad_OK_counter);
    rad_OK_counter = 0;
}

bool Radio::IsOk()
{
    return m_ok;
}
