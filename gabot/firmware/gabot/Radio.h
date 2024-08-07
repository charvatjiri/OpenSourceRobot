#include <stdint.h>
#include <RF24.h>

class Radio
{
public: 
    Radio();
    ~Radio();

    void Init();
    void Read(byte* result);
    void Restart();
    bool Available();

private:
    RF24 m_radio;
};
