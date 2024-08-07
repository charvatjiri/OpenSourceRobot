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
#include<avr/wdt.h> 

#include "Radio.h"


//#define CE 9  //UNO
#define CE 49  //mega
//#define CS 10
#define CS 48
//MOSI = 11 mega51
//MISO = 12 mega50
//SCK = 13 mega52
//VCC = 3,3V!
 Servo motorF;
 Servo motorC;
 Servo motorH;

byte cti;
 //definition input pins
// byte sensor_back = A15; //sensor for shoulder backward 
 byte fotodiodeZ = A15; //long gaps
// byte sensor_forw = A14; //sensor for shoulder forward
 byte fotodiodeA = A14; //short regular gaps
 word sensor_long;
 word sensor_short;
 bool holeA;
 bool holeZ;
 bool holeZA;
 int countA;
 int countZ;
 int countZA;
 int last_dir;
 byte osc = 23;
 byte but_B = A13; //button B
 byte but_A = A12; //button A 
 byte button = A8;  //now DEMO
 int current_L = A6; //100 to 200ms start pulz max.300mV,block=450mV
// boolean sensor_forw_state;
// boolean sensor_back_state;
// boolean sen_F_old;
// boolean sen_B_old;
 int angle; //position of arm  +/- 200°
 //for fuse value 65-70
 int current_F = A3; //I=current_F/215, 0.25A = 0.35V
 int voltage_input = A0;  //baterry
 //definition output pins
 byte LmotLF = 3;  //Left motor Forward
 byte LmotHF = 22;  //Left motor Forward
 byte LmotLB = 2;  //Left motor Backward
 byte LmotHB = 34;  //Left motor Backward
/* byte motLE = 6;  //Shoulder motor Forward = East = Right
 byte motHE = 42;  //Shoulder motor Forward = East = Right
 byte motLW = 7;  //Shoulder motor Backward = West = Left
 byte motHW = 40;  //Shoulder motor Backward = West = Left*/
 byte motLE = 7;  //Shoulder motor Forward = East = Right
 byte motHE = 40;  //Shoulder motor Forward = East = Right
 byte motLW = 6;  //Shoulder motor Backward = West = Left
 byte motHW = 42;  //Shoulder motor Backward = West = Left
 byte motLU = 4;  //Arm motor up
 byte motHU = 38;  //Arm motor up
 byte motLD = 5;  //Arm motor down
 byte motHD = 36;  //Arm motor down
 byte RmotLB = 9;  //Right motor LOW Backward
 byte RmotHB = 44;  //Right motor HIGH Backward
 byte RmotLF = 8;  //Right motor LOW Forward
 byte RmotHF = 46;  //Right motor HIGH Forward
 byte motFPWM = 13;   //PWM
 byte motFR = 18;  //release/grab motor
 byte motFG = 19;  //release/grab motor
 //byte FmotO12 = 22; //Finger motor Open 12V
 //byte FmotC12 = 24; //Finger motor Close 12V
 byte buzzer = A9; //piezo-buzzer without generator 
 byte motorF_req; //request value of servo F
 byte motorC_req; //request value of servo C
 byte motorH_req; //request value of servo H
 byte motorA_req; //request value of servo A
 byte motorF_value; //value of servo F
 byte motorF_val_I;  //value of servo F, limited current
 byte motorC_value; //value of servo C
 byte motorH_value; //value of servo H
 byte motorA_value; //value of servo A
 byte speed_req = 20;
 byte speedW;     //required speed of shoulder
 byte maxF;
 byte serial_in;
 byte part_number; //part number
 
 byte dir_forw; //dirrection forward
 boolean dir_forwH; //dirrection forward
 byte dir_back; //dirrection back
 boolean dir_backH; //dirrection back
 
 byte Ldir_forw; //dirrection forward
 boolean Ldir_forwH; //dirrection forward
 byte Ldir_back; //dirrection back
 boolean Ldir_backH; //dirrection back
 
 byte Rdir_forw; //dirrection forward
 boolean Rdir_forwH; //dirrection forward
 byte Rdir_back; //dirrection back
 boolean Rdir_backH; //dirrection back
 
 byte part1; //part of command
 byte part2;
 int part3;
 int rychlV;
 int rychlH;
 int v;
 int h;
 int sign_h;
 int sign_v;
 int lim_v;
 int lim_h;
 int slow_h;
 int slow_v;
 int h_a;
 int v_a;
 unsigned long timeC;
 unsigned long timeH;
 byte i;
 word oldprint;
 word citRadio;
 bool RadioOK;
 word rad_OK_counter;
 bool grab; //is set after button grab
 bool rls; //is set after button release
 byte countG; //counter-protection before long grab
 byte countR; //counter-protection before long release
 unsigned long time_now; //timer 100 ms
 float baterry;  //baterry voltage
 word buzz_count;  //
 bool BUZ_ON;
 bool BUZ_STATE;
 bool BAT_OK;

Radio GabotRadio;

//#define IRQ_PIN 21 // this needs to be a digital input capable pin --- not used
volatile bool wait_for_event = false; // used to wait for an IRQ event to trigger
volatile char data[2];
//volatile char element_R[16];
//volatile bool EL_R[16];
char element[16];  //value for every element (joystick, button)
bool EL[16];       //HIGH = new value

// adresy a kanál
const byte vysilac[] = "TX001";
const byte prijimac[] = "RX001";
int kanal = 120;
volatile byte pokus;

//void interruptHandler(); // prototype to handle IRQ events

void setup(void) {
  Serial.begin(115200);
  Serial.println("RESET");  
  wdt_enable(WDTO_120MS);

 //   motorF.attach(8); //servoA - pin 8
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
   pinMode(motFG, OUTPUT);
   digitalWrite(motFG, HIGH);
   pinMode(motFR, OUTPUT);
   digitalWrite(motFR, HIGH);
   pinMode(motFPWM, OUTPUT);
//   pinMode(IRQ_PIN, INPUT);
   pinMode(button, INPUT);
   pinMode(buzzer, OUTPUT);
    grab = HIGH;
    rls = HIGH;
    countR = 0;
    countG = 0;   
    dir_forw = 0;
    dir_back = 0;
    dir_forwH = LOW;
    dir_backH = LOW;
    Ldir_forw = 0;
    Ldir_back = 0;
    Ldir_forwH = LOW;
    Ldir_backH = LOW;
    Rdir_forw = 0;
    Rdir_back = 0;
    Rdir_forwH = LOW;
    Rdir_backH = LOW;
    rad_OK_counter = 0;
//    pinMode(sensor_back, INPUT);
//    pinMode(sensor_forw, INPUT);
//    sensor_forw_state = digitalRead(sensor_forw);
//    sensor_back_state = digitalRead(sensor_back);
//    sen_B_old = sensor_back_state;
//    sen_F_old = sensor_forw_state;
//    angle = 0;
    slow_h = 0;
    slow_v = 0;
    motorH_value = 80;
    motorH.write(motorH_value);
  // initialize the transceiver on the SPI bus
  GabotRadio.Init();
 

  // setting power of nRF module,
  // options: RF24_PA_MIN, RF24_PA_LOW, RF24_PA_HIGH and RF24_PA_MAX,
  // external power 3.3V is need for HIGH and MAX 

  Serial.println("ready");
  baterry = analogRead(voltage_input);
  baterry = baterry / 80.46;
  if(baterry < 10){
    BAT_OK = LOW;
    buzz_count = 2000;  //long beep
  }
  else{
    BAT_OK = HIGH;
    buzz_count = 100;  //short beep (not from baterry)
  }
  Serial.print("baterry voltage = ");
  Serial.print(baterry);
  Serial.println(" V");   
//  GabotRadio.m_radio.maskIRQ(1, 1, 0); // args = "data_sent", "data_fail", "data_ready"
//    GabotRadio.m_radio.maskIRQ(1, 1, 1); // args = "data_sent", "data_fail", "data_ready"
  BUZ_ON = 1;
  buzz_count = 50;
}

void loop(void) {
  if(RadioOK == 1){  //a special code has been received
    citRadio = 0;  //reset counter for radio watch dog
  }
  RadioOK = 0; //will be set after a special code
  citRadio++; //radio watch dog timer
//  if(citRadio > 3000){  //time about 1 s
  if(citRadio > 6000){  //time about 0.5 s
    citRadio = 0;  //reset counter for radio watch dog
    //new radio setting (after interference)
    GabotRadio.Restart();
    BUZ_ON = 1; //will be short beep
  }

  // while (GabotRadio.m_radio.available()) { //when a signal has been received
  //   GabotRadio.m_radio.read( data, 2);    //two bytes of signal to &data
  while (GabotRadio.Available()) { //when a signal has been received
    GabotRadio.Read(data);    //two bytes of signal to &data
    if((data[0] == 0x55)&&(data[1] == 0x55)){ //a special radio watch dog code
      rad_OK_counter++;
      RadioOK = 1;  //a special code has been received
    }
    else{
/*      Serial.println(" ");
      Serial.print("    data ");
      Serial.print(data[0], DEC);
      Serial.print(": ");
      Serial.print(data[1], DEC);*/
      data[0] = data[0] & 0x0F; //which element
      EL[data[0]] = HIGH;  //element will be changed
      element[data[0]] = data[1];  // new value of element  
    }
  }  
  
  while(digitalRead(button) == 0){
    //when button is pressed, program stopped and RESET by WD
    //i++;
    //Serial.println(i);
  }
  wdt_reset();
  
  sensor_short = analogRead(fotodiodeA);
  sensor_long = analogRead(fotodiodeZ);

  //value of fotosensors are from 0 to 60
  //the 360° disc has 24 A holes and 4 different Z holes
  cti = 0; //counter delay for print
  if((holeA == 0) && (sensor_short > 30)){
    holeA = 1;
//    digitalWrite(osc, 1);
    countA = countA + last_dir;  //countA counter A holes
    if(cti == 0){ 
      Serial.print(" angleA =");
      Serial.println((countA * 15));     
//    Serial.print(" holeA =");
//    Serial.println(holeA);
//    Serial.print(" countA =");
//    Serial.println(countA);
    }
    if(countZ){  //countering A holes during Z hole
      countZ = countZ + last_dir;  //relative
      countZA++; //absolute
      if(cti == 0){
//      Serial.print(" countZ =");
//      Serial.println(countZ);
      }
    }
  }  
  if((holeA == 1) && (sensor_short < 20)){
    holeA = 0;
  }
  if((holeZ == 0) && (sensor_long > 30)){
    holeZA = 1;
    holeZ = 1;
    countZ = countZ + last_dir;
    countZA = 1;
    if(cti == 0){
    }
  }  
  if((holeZ == 1) && (sensor_long < 20)){  //end of Z hole
/*      Serial.print(" countZA =");
      Serial.print(countZA);
      Serial.print("  countZ =");
      Serial.print(countZ);
      Serial.print("  last_dir =");
      Serial.println(last_dir);*/

//setting the angle according to the Z holes 
    if(countZA == abs(countZ)){ //setting is alowed only when hole Z went in one direction
      if(countZ == 2){
        angle = 15;
        countA = 1;
        countZA = 1;
      }
      if(countZ == 4){
        angle = -60;
        countA = -5;
        countZA = -5;
//        Serial.println(countA);
      }

      if(countZ == 3){
        angle = 120;
        countA = 8;
        countZA = 8;
      }
    }
    if(last_dir == -1){
      if(countZ == -2){
        angle = 0;
        countA = 0;
        countZA = 0;
      }
      if(countZ == -3){
        angle = 90;
        countA = 6;
        countZA = 6;
      }
      if(countZ == -4){
        angle = -105;
        countA = -7;
        countZA = -7;
      }
//the angle is only indicative, the offset of the holes is neglected      
//but angle is (countA * 15)
//    Serial.print(" angle =");
//    Serial.println(angle);        
    }
    holeZ = 0;    
  }  
  if(holeZ == 0){
    countZA = 0;
    countZ = 0;
    if(cti == 0){
//    Serial.print(" holeZ =");
//    Serial.println(holeZ);
    }
  }
/*  if(cti == 0){
      Serial.print(" angle =");
      Serial.println(angle);
  }*/
  cti++; //delay for print
    
  if(EL[0] == HIGH){ 
  }  
  if(millis() > timeC){ 
    timeC = millis()+(138 - abs(element[0]));
    if((element[0] > 0) && (motorC_value < 166)){
      motorC_value++;                                          
    }
    if((element[0] < 0) && (motorC_value > 0)){
      motorC_value--;                                          
    } 
      motorC.write(motorC_value);
  }
    if(EL[1] == HIGH){ //
    EL[1] = 0;
    }
  if(millis() > timeH){ 
    timeH = millis()+(138 - abs(element[1]));
    if((element[1] > 0) && (motorH_value < 166)){
      motorH_value++;
//      Serial.println(motorH_value);                                          
    }
    if((element[1] < 0) && (motorH_value > 0)){
      motorH_value--;
//      Serial.println(motorH_value);                                          
    } 
      motorH.write(motorH_value);
  }
  if(EL[2] == HIGH){ //
    EL[2] = 0;

    if(element[2] < 0){  //element[2]=arm left/right
      dir_forw = 0;
      dir_back = 1;
      dir_forwH = 0;
      dir_backH = 1;
//      part3 = element[2] * (-2);
      part3 = element[2] * (-1); //speed redused by half
      last_dir = -1; 
    }
    else if(element[2] == 0){
      dir_forw = 0;
      dir_back = 0;
      dir_forwH = 0;
      dir_backH = 0;
      part3 = 0;      
    }
    else{
      dir_forw = 1;
      dir_back = 0;
      dir_forwH = 1;
      dir_backH = 0;
      //part3 = element[2] * 2;
      part3 = element[2]; //speed redused by half
      last_dir = 1;      
    }
    digitalWrite(motHE, dir_forwH);
    analogWrite(motLE, part3 * dir_forw);
    digitalWrite(motHW, dir_backH);
    analogWrite(motLW, part3 * dir_back);                                      
  }
  if((countA * 15) > 190){
    analogWrite(motLE, 0);
    digitalWrite(motHE, 0);
    dir_forw = 0;
    dir_forwH = 0;
  }
  if((countA * 15) < -190){
    analogWrite(motLW, 0);
    digitalWrite(motHW, 0);
    dir_back = 0;
    dir_backH = 0;
  }  
  if(EL[3] == HIGH){
    EL[3] = 0;

    if(element[3] < 0){  //element[3]=arm down/up
      dir_forw = 0;
      dir_back = 1;
      dir_forwH = 0;
      dir_backH = 1;
      part3 = element[3] * (-2);
    }
    else if(element[3] == 0){
      dir_forw = 0;
      dir_back = 0;
      dir_forwH = 0;
      dir_backH = 0;
      part3 = 0;
    }
    else{
      dir_forw = 1;
      dir_back = 0;
      dir_forwH = 1;
      dir_backH = 0;
      part3 = element[3] * 2;      
    }
    digitalWrite(motHU, dir_forwH);
    analogWrite(motLU, part3 * dir_forw);
    digitalWrite(motHD, dir_backH);
    analogWrite(motLD, part3 * dir_back);                                      
  }
  if(EL[4] == HIGH){  //+right, - left
    EL[4] = 0; //now not used
                                   
  }
  if(EL[5] == HIGH){  //+forward, -back
    EL[5] = 0; //now not used                                    
  }
  if(element[4] > slow_h){  //element[4]=left/right from joystick
    slow_h++;  //value for motors is changing only for small steps
  }
  if(element[4] < slow_h){
    slow_h--;
  }
  if(element[5] > slow_v){  //element[5]=forward/back from joystick
    slow_v++;
  }
  if(element[5] < slow_v){
    slow_v--;
  }  
  
  h_a = abs(slow_h);  //h_a =a bs horizontal(left/right) value
  sign_h = h_a/slow_h; //memoring sign
  v_a = abs(slow_v);
  sign_v = v_a/slow_v;
  
  if((v_a + h_a) > 127){  //v & h are redused when (v_a + h_a) > 127
    v = (v_a * 127 / (v_a + h_a));
    h = (h_a * 127 / (v_a + h_a));  
  }
  else{
    v = v_a;
    h = h_a;  
  }
  v = sign_v * 2 * v; //from (0 to 127) to (-255 to 255)
  h = sign_h * 2 * h;

  if((v - h) > 0){  //setting switchs for motor directions
    Rdir_forw = 1;
    Rdir_back = 0;
    Rdir_forwH = 1;
    Rdir_backH = 0;
  }  
  else if((v - h) == 0){
    Rdir_forw = 0;
    Rdir_back = 0;
    Rdir_forwH = 0;
    Rdir_backH = 0;                  
  }
  else{
    Rdir_forw = 0;
    Rdir_back = 1;
    Rdir_forwH = 0;
    Rdir_backH = 1;        
  }
  if((v + h) > 0){
    Ldir_forw = 1;
    Ldir_back = 0;
    Ldir_forwH = 1;
    Ldir_backH = 0;        
  }
  else if((v + h) == 0){
    Ldir_forw = 0;
    Ldir_back = 0;
    Ldir_forwH = 0;
    Ldir_backH = 0;                    
  }
  else{
    Ldir_forw = 0;
    Ldir_back = 1;
    Ldir_forwH = 0;
    Ldir_backH = 1;        
  }
//left & right motor    
  digitalWrite(RmotHF, Rdir_forwH);
  analogWrite(RmotLF, abs(v - h) * Rdir_forw); //transfer values from joystick to motors
  digitalWrite(RmotHB, Rdir_backH);
  analogWrite(RmotLB, abs(v - h) * Rdir_back);//transfer values from joystick to motors
  digitalWrite(LmotHF, Ldir_forwH);
  analogWrite(LmotLF, abs(v + h) * Ldir_forw);//transfer values from joystick to motors
  digitalWrite(LmotHB, Ldir_backH);
  analogWrite(LmotLB, abs(v + h) * Ldir_back);//transfer values from joystick to motors
  if(EL[7] == HIGH){ //
    EL[7] = 0;
  }
  if(EL[8] == HIGH){ //
    EL[8] = 0;
  }
  if(EL[9] == HIGH){ //
    EL[9] = 0;
  }
  if(EL[10] == HIGH){ //
    EL[10] = 0;         
    rls = HIGH; //release OFF
    grab = data[1]; //grab ON/OFF
  }
  if(EL[11] == HIGH){ //release
    EL[11] = 0;      
    grab = HIGH; //grab OFF
    rls = data[1]; //release ON/OFF
  }
  digitalWrite(motFG, grab); //wanted condition on output
  digitalWrite(motFR, rls); //wanted condition on output

  if(BUZ_ON || !BAT_OK){
//  if(BUZ_ON){    
    BUZ_STATE = !BUZ_STATE;
    digitalWrite(buzzer, BUZ_STATE);
    buzz_count--;
    if(buzz_count == 0){
      BUZ_ON = 0;
      BAT_OK = 1;
      buzz_count = 100;
    }   
  }
  
  //TIMERS
  if(millis() - time_now > 100){  //every second go trough without owerfloat
    time_now = millis();
    //protective of motor grab/release before long run
    if(rls == LOW){ //if release active = LOW
      countR++;  //every 100ms inc counter      
    }
    else{
      countR = 0;  //if release not active
    }
    if(countR > 33){  //max time of release 3.3 s
      rls = HIGH;
      countR = 0;
    }
    //similary for grab  
    if(grab == LOW){
      countG++;        
    }
    else{
      countG = 0;
    }
    if(countG > 33){  //max time of grab 3.3 s
      grab = HIGH;
      countG = 0;
    }
  }  
}      
