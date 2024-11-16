#define ST1 31
#define ST0 30
#define SEQ 29

int state = 0;
int reg = -1;

void setup() {
  // put your setup code here, to run once:
  Serial.begin(9600);
  Serial.println("Starting...");
  pinMode(ST1, INPUT);
  pinMode(ST0, INPUT);
  pinMode(SEQ, INPUT);
}

void loop() {
  // put your main code here, to run repeatedly:
  int seq = digitalRead(SEQ);
  if (reg == -1) {
    delay(200);
    Serial.println("Ready");
    reg = seq;
  }
  //Serial.print("Seq rec: ");
  //Serial.println(seq);
  if (seq != reg) {
    state = digitalRead(ST1) * 2 + digitalRead(ST0); //Formula for decoding 2 bit binary
    Serial.print("Exec state: ");
    Serial.println(state);
    if (state == 3) {
      //execute button 3 effect
    }
    else if (state == 2) {
      //execute button 2 effect
    }
    else if (state == 1) {
      //execute button 1 effect
    }
    else if (state == 0) {
      //execute nothing
    }
    else {
      Serial.print("Unexpected result! State = ");
      Serial.println(state);
    }
    reg = seq; // 0 or 1
    Serial.print("Set reg to: ");
    Serial.println(reg);
  }

  delay(200); //represents rest of code execution in Alarm System
  // position above code where the state switching would occur in existing system
  // execute same commands to switch state, whether it is setting a variable or series of variables
}
