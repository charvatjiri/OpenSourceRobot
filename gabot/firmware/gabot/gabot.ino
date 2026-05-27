//GABOT2
//2.7.2021 * Jaroslav Kaspar
//version controll by radio NRF24L01
//version 4.0 from 6.2.2022 Extended time for re-establishing
//the radio connection from 500ms to 1000ms.
//arm angle 400°
//charge only by 12 ... 12.6V
//long beep (only after reset (and start) = baterry LOW (10V)
//short beep - loss of radio connection


//original program for nrf
//http://azuzula.blogspot.com/2017/03/arduino-nrf24l01-bezdratova-komunikace.html
#include <SPI.h>
#include <nRF24L01.h>
#include <RF24.h>
#include <Servo.h>
#include <avr/wdt.h>
#include <Wire.h>

void wdt_init(void) __attribute__((naked)) __attribute__((section(".init3")));
void wdt_init(void)
{
    MCUSR = 0;
    wdt_disable();
}

#include "Radio.h"
#include "Fingers.h"
#include "SerialCommand.h"
#include "AngleSensor.h"
#include "OvercurrentProtection.h"
#include "BatteryMonitor.h"
#include "RadioControl.h"

#define VER_MAJOR 3
#define VER_MINOR 1
#define VER_MICRO 4

//#define CE 9  //UNO
#define CE 49  //mega
//#define CS 10
#define CS 48
//MOSI = 11 mega51
//MISO = 12 mega50
//SCK = 13 mega52
//VCC = 3,3V!
Servo motorF; // ? fingers?
Servo motorC; // wrist Right-Left Rotate 0-360 angle degree
Servo motorH; // wrist Up-Down 0-180 angle degree

//definition input pins
byte osc = 23;
int voltage_input = A0;  //baterry
//definition output pins
byte LmotLF = 3;    //Left motor Forward
byte LmotHF = 22;   //Left motor Forward
byte LmotLB = 2;    //Left motor Backward
byte LmotHB = 34;   //Left motor Backward
byte motLE = 7;     //Shoulder motor Forward = East = Right
byte motHE = 40;    //Shoulder motor Forward = East = Right
byte motLW = 6;     //Shoulder motor Backward = West = Left
byte motHW = 42;    //Shoulder motor Backward = West = Left
byte motLU = 4;     //Arm motor up
byte motHU = 38;    //Arm motor up
byte motLD = 5;     //Arm motor down
byte motHD = 36;    //Arm motor down
byte RmotLB = 9;    //Right motor LOW Backward
byte RmotHB = 44;   //Right motor HIGH Backward
byte RmotLF = 8;    //Right motor LOW Forward
byte RmotHF = 46;   //Right motor HIGH Forward
byte motFPWM = 13;  //PWM
byte buzzer = A9;   //piezo-buzzer without generator

byte motorC_value;  //value of servo C
byte motorH_value;  //value of servo H

float baterry;           //baterry voltage
bool BUZ_STATE;
word buzz_count = 100;
unsigned long time_now;  //timer 100 ms

Fingers GabotFingers;
Radio GabotRadio;
SerialCommand GabotSerial(GabotFingers, VER_MAJOR, VER_MINOR, VER_MICRO);

// New modules from GABOT23
AngleSensor GabotAngle;
OvercurrentProtection GabotOvercurrent;
BatteryMonitor GabotBattery;

// Radio control
RadioControl GabotRC;

// Current sensor pins (from GABOT23)
#define CURRENT_PIN_L A6
#define CURRENT_PIN_R A3
#define CURRENT_PIN_UD A5
#define CURRENT_PIN_WE A4
#define CURRENT_PIN_F A1
#define VOLTAGE_PIN A0
#define BUZZER_PIN A9

void setup(void) {
  MCUSR = 0;
  wdt_disable();

  Serial.begin(115200);
  Serial.println("RESET");

  motorC.attach(11);
  motorH.attach(10);
  motorF.attach(9);
  pinMode(osc, OUTPUT);
  pinMode(RmotHB, OUTPUT);
  digitalWrite(RmotHB, LOW);
  pinMode(RmotHF, OUTPUT);
  digitalWrite(RmotHF, LOW);
  pinMode(LmotHB, OUTPUT);
  digitalWrite(LmotHB, LOW);
  pinMode(LmotHF, OUTPUT);
  digitalWrite(LmotHF, LOW);
  pinMode(motHE, OUTPUT);
  digitalWrite(motHE, LOW);
  pinMode(motHW, OUTPUT);
  digitalWrite(motHW, LOW);
  pinMode(motHU, OUTPUT);
  digitalWrite(motHU, LOW);
  pinMode(motHD, OUTPUT);
  digitalWrite(motHD, LOW);
  pinMode(motFPWM, OUTPUT);
  pinMode(A8, INPUT);   // button
  pinMode(buzzer, OUTPUT);

  motorH_value = 80;
  motorH.write(motorH_value);

  // initialize the transceiver on the SPI bus
  GabotRadio.Init();
  GabotSerial.setMotors(motorF, motorC, motorH, &motorC_value, &motorH_value);
  GabotSerial.setShoulderPins(motLE, motHE, motLW, motHW,
                               motLU, motHU, motLD, motHD);

  // Initialize new modules from GABOT23
  GabotAngle.Init(4);  // direction pin
  GabotOvercurrent.Init(CURRENT_PIN_L, CURRENT_PIN_R, CURRENT_PIN_UD, CURRENT_PIN_WE);
  GabotBattery.Init(VOLTAGE_PIN, BUZZER_PIN);
  GabotFingers.Init(CURRENT_PIN_F);

  // Initialize RadioControl
  MotorPins pins;
  pins.LmotLF = LmotLF; pins.LmotHF = LmotHF;
  pins.LmotLB = LmotLB; pins.LmotHB = LmotHB;
  pins.RmotLF = RmotLF; pins.RmotHF = RmotHF;
  pins.RmotLB = RmotLB; pins.RmotHB = RmotHB;
  pins.motLE = motLE; pins.motHE = motHE;
  pins.motLW = motLW; pins.motHW = motHW;
  pins.motLU = motLU; pins.motHU = motHU;
  pins.motLD = motLD; pins.motHD = motHD;
  pins.buzzer = buzzer;
  pins.button = A8;
  pins.fotodiodeA = A14;
  pins.fotodiodeZ = A15;

  GabotRC.Init(GabotRadio, GabotFingers,
               GabotAngle, GabotOvercurrent, GabotBattery,
               motorC, motorH, &motorC_value, &motorH_value,
               pins);

  // Connect serial wheel commands to RadioControl elements
  GabotSerial.setWheelElements(GabotRC.GetElementPtr(WHEELS_RL),
                                GabotRC.GetElementPtr(WHEELS_FB));

  Serial.println("ready");
  baterry = analogRead(voltage_input);
  baterry = baterry / 80.46;
  if (baterry < 10) {
    Serial.println("WARNING: battery LOW");
  }
  Serial.print("baterry voltage = ");
  Serial.print(baterry);
  Serial.println(" V");
}

void loop(void) {
  wdt_reset();

  if (GabotSerial.Process() == SerialCmd_Success)
    return;
  wdt_reset();

  GabotRC.Process();

  // Buzzer - radio loss beep or low battery beep
  if (GabotRC.IsBuzRequested() || GabotBattery.IsBatteryLow()) {
    BUZ_STATE = !BUZ_STATE;
    digitalWrite(buzzer, BUZ_STATE);
    buzz_count--;
    if (buzz_count == 0) {
      GabotRC.ClearBuzRequest();
      buzz_count = 100;
    }
  }

  // Timers - every 100ms updates
  if (millis() - time_now > 100) {
    time_now = millis();

    GabotAngle.ReadAngle();
    GabotOvercurrent.Update();
    float voltage;
    if (voltage = GabotBattery.Update()) {
        Serial.print("baterry voltage = ");
        Serial.print(voltage);
        Serial.println(" V");
    }
    GabotFingers.Update();
  }
}
