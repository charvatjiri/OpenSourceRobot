#include <Arduino.h>
#include <RF24.h>
#include "Radio.h"

#define CE 49
#define CS 48
const byte vysilac[] = "TX001";
const byte prijimac[] = "RX001";
const int kanal = 120;

Radio::Radio() : m_radio(CE, CS)
{
}

Radio::~Radio()
{
}

void Radio::Init()
{
  if (!m_radio.begin()) {
  Serial.println(F("radio hardware is not responding!!"));
    while (1) {} // hold in infinite loop
  }
  //setting speed of communication
  //options: RF24_250KBPS, RF24_1MBPS, RF24_2MBPS
  m_radio.setDataRate(RF24_1MBPS);
  m_radio.setPALevel(RF24_PA_MIN);
  //setting channel
  m_radio.setChannel(kanal);
  // receiver
  m_radio.openWritingPipe(prijimac);
  m_radio.openReadingPipe(1, vysilac);
  m_radio.startListening();
  //m_radio.printDetails();
}

extern  word rad_OK_counter;
extern bool RadioOK;

bool Radio::Available()
{
  return m_radio.available();
}

void Radio::Read(byte* result)
{
    m_radio.read(result, 2);    //two bytes of signal to &data
    return;
}

void Radio::Restart()
{
    m_radio.setChannel(kanal);
    m_radio.openWritingPipe(prijimac);
    m_radio.openReadingPipe(1, vysilac);
    m_radio.startListening();
    Serial.print("Radio restarted, rad_OK_counter= ");
    Serial.println(rad_OK_counter);
    rad_OK_counter = 0;
}
