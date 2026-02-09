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
    bool IsOk();

private:
    RF24 m_radio;
    bool m_ok;
};
