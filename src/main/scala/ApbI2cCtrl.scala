package apb_i2c

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.apb._
import spinal.lib.io.TriState

// ============================================================================
// APB I2C Controller
// ============================================================================
// APB slave interface + I2C master controller with full protocol support
// ============================================================================

case class ApbI2cCtrlGenerics(
  clockFrequency: HertzNumber = 50 MHz,
  dataWidth: Int = 8,
  addrWidth: Int = 7,
  apbAddrWidth: Int = 5
) {
  require(dataWidth == 8, "I2C data width must be 8 bits")
  require(addrWidth == 7 || addrWidth == 10, "I2C address must be 7 or 10 bits")
}

// Register definitions
object ApbI2cCtrlRegs {
  val CTRL     = 0x00
  val STATUS   = 0x04
  val DATA     = 0x08
  val ADDR     = 0x0C
  val PRESCALE = 0x10
}

// Control register (32-bit)
case class CtrlReg() extends Bundle {
  val en  = Bool()      // Enable core
  val ien = Bool()      // Interrupt enable
  val sta = Bool()      // Generate START condition
  val sto = Bool()      // Generate STOP condition
  val rd  = Bool()      // Read operation
  val wr  = Bool()      // Write operation
  val ack = Bool()      // ACK/NACK for read (0=ACK, 1=NACK)
  val reserved = Bits(25 bits)
}

// Status register (32-bit)
case class StatusReg() extends Bundle {
  val rxack = Bool()    // Received ACK (0=ACK, 1=NACK)
  val busy  = Bool()    // Bus busy
  val tip   = Bool()    // Transfer in progress
  val ifl   = Bool()    // Interrupt flag
  val reserved = Bits(28 bits)
}

// I2C bus interface (open-drain bidirectional)
case class I2cBus() extends Bundle with IMasterSlave {
  // TriState: write (drive low), read (sample line)
  // Open-drain: writeEnable=1 when driving low, writeEnable=0 for high-Z
  val scl = TriState(Bool())
  val sda = TriState(Bool())

  override def asMaster(): Unit = {
    master(scl, sda)
  }

  override def asSlave(): Unit = {
    slave(scl, sda)
  }
}

// I2C state machine states
object I2cState extends SpinalEnum(binarySequential) {
  val IDLE, START_1, START_2, ADDR, ADDR_ACK, DATA_TX, DATA_RX, DATA_ACK, STOP_1, STOP_2 = newElement()
}

// ============================================================================
// I2C Master Controller Core
// ============================================================================
case class I2cMasterCore(generics: ApbI2cCtrlGenerics) extends Component {
  val io = new Bundle {
    val ctrl = in(CtrlReg())
    val status = out(StatusReg())
    val txData = in Bits(generics.dataWidth bits)
    val rxData = out Bits(generics.dataWidth bits)
    val slaveAddr = in Bits(generics.addrWidth bits)
    val prescale = in UInt(16 bits)
    val i2c = master(I2cBus())
    val cmdPulse = in(CtrlReg())  // Command pulse input (auto-cleared by top-level)
    val iflClear = in Bool()      // Clear interrupt flag
  }

  // State machine
  val state = RegInit(I2cState.IDLE)

  // Status registers
  val busy = RegInit(False)
  val tip = RegInit(False)
  val rxack = RegInit(False)
  val ifl = RegInit(False)

  // Data path
  val shiftReg = Reg(Bits(generics.dataWidth bits)) init(0)
  val rxDataReg = Reg(Bits(generics.dataWidth bits)) init(0)
  val bitCnt = Reg(UInt(4 bits)) init(0)

  // Prescaler for I2C timing
  val prescaleCnt = Reg(UInt(16 bits)) init(0)
  val tick = prescaleCnt === 0
  prescaleCnt := Mux(tick, io.prescale, prescaleCnt - 1)

  // SCL phase counter (counts prescaler ticks)
  val sclPhase = Reg(UInt(2 bits)) init(0)

  // I2C outputs (open-drain: write = 0 to drive low, writeEnable = 1 when driving)
  val sclOut = RegInit(True)     // True = high-Z (released), False = drive low
  val sclEnable = RegInit(False) // Enable output driver
  val sdaOut = RegInit(True)
  val sdaEnable = RegInit(False)

  io.i2c.scl.write := sclOut
  io.i2c.scl.writeEnable := sclEnable
  io.i2c.sda.write := sdaOut
  io.i2c.sda.writeEnable := sdaEnable

  // Sample inputs
  val sclIn = io.i2c.scl.read
  val sdaIn = io.i2c.sda.read

  // Previous SCL for edge detection
  val sclPrev = RegNext(sclIn) init(True)
  val sclRising = sclIn && !sclPrev
  val sclFalling = !sclIn && sclPrev

  // Command register capture (one-shot)
  val doStart = RegInit(False)
  val doStop = RegInit(False)
  val doRead = RegInit(False)
  val doWrite = RegInit(False)
  val ackVal = RegInit(False)

  // Capture commands on pulse
  when(io.cmdPulse.sta && io.ctrl.en && !busy) {
    doStart := True
  }
  when(io.cmdPulse.sto && io.ctrl.en && busy) {
    doStop := True
  }
  when(io.cmdPulse.wr && io.ctrl.en && !tip) {
    doWrite := True
    shiftReg := io.txData
    tip := True
  }
  when(io.cmdPulse.rd && io.ctrl.en && !tip) {
    doRead := True
    tip := True
    ackVal := io.ctrl.ack
  }

  // Clear commands when processed
  when(state === I2cState.START_1 || state === I2cState.START_2) {
    doStart := False
  }
  when(state === I2cState.STOP_1 || state === I2cState.STOP_2) {
    doStop := False
  }

  // Address formatting
  val addrByte = Bits(8 bits)
  if (generics.addrWidth == 7) {
    addrByte := io.slaveAddr(6 downto 0) ## (doRead ? B"1" | B"0")
  } else {
    // 10-bit address: first byte is 11110XX + R/W, second byte is remaining 8 bits
    addrByte := B"11110" ## io.slaveAddr(9 downto 8) ## (doRead ? B"1" | B"0")
  }

  // State machine
  when(tick) {
    switch(state) {
      is(I2cState.IDLE) {
        sclOut := True
        sclEnable := False
        sdaOut := True
        sdaEnable := False
        bitCnt := 0
        sclPhase := 0

        when(doStart) {
          // START condition: SDA falls while SCL high
          state := I2cState.START_1
          sdaOut := False
          sdaEnable := True
          busy := True
        }
      }

      is(I2cState.START_1) {
        // SDA already low, now pull SCL low
        sclOut := False
        sclEnable := True
        state := I2cState.START_2
        // Load address for transmission
        if (generics.addrWidth == 7) {
          shiftReg := io.slaveAddr(6 downto 0) ## (doRead ? B"1" | B"0")
        } else {
          shiftReg := B"11110" ## io.slaveAddr(9 downto 8) ## (doRead ? B"1" | B"0")
        }
        bitCnt := 7
      }

      is(I2cState.START_2) {
        // Continue to address phase
        state := I2cState.ADDR
        sclPhase := 0
      }

      is(I2cState.ADDR) {
        // Transmit 8 bits (address + R/W)
        switch(sclPhase) {
          is(0) {
            // SCL low - setup data
            sdaOut := shiftReg(bitCnt.resized)
            sdaEnable := True
            sclPhase := 1
          }
          is(1) {
            // SCL high - data valid
            sclOut := True
            sclEnable := False
            sclPhase := 2
          }
          is(2) {
            // Wait for SCL high period
            sclPhase := 3
          }
          is(3) {
            // SCL low - end of bit
            sclOut := False
            sclEnable := True
            sclPhase := 0

            when(bitCnt === 0) {
              state := I2cState.ADDR_ACK
              sdaOut := True  // Release for ACK
              sdaEnable := False
            } otherwise {
              bitCnt := bitCnt - 1
            }
          }
        }
      }

      is(I2cState.ADDR_ACK) {
        // Sample ACK from slave
        switch(sclPhase) {
          is(0) {
            // SCL high - sample
            sclOut := True
            sclEnable := False
            sclPhase := 1
          }
          is(1) {
            // Sample SDA
            rxack := sdaIn  // 0 = ACK, 1 = NACK
            sclPhase := 2
          }
          is(2) {
            // SCL low
            sclOut := False
            sclEnable := True
            sclPhase := 0

            when(rxack) {
              // NACK - go to STOP or IDLE
              when(doStop) {
                state := I2cState.STOP_1
              } otherwise {
                state := I2cState.IDLE
                busy := False
                tip := False
                ifl := True
              }
            } otherwise {
              // ACK - proceed to data
              when(doRead) {
                state := I2cState.DATA_RX
                bitCnt := 7
                sdaOut := True
                sdaEnable := False
              } elsewhen(doWrite) {
                state := I2cState.DATA_TX
                bitCnt := 7
              } otherwise {
                // No data command - wait
                state := I2cState.IDLE
                tip := False
                ifl := True
              }
            }
          }
        }
      }

      is(I2cState.DATA_TX) {
        // Transmit 8 bits of data
        switch(sclPhase) {
          is(0) {
            // SCL low - setup data
            sdaOut := shiftReg(bitCnt.resized)
            sdaEnable := True
            sclPhase := 1
          }
          is(1) {
            // SCL high
            sclOut := True
            sclEnable := False
            sclPhase := 2
          }
          is(2) {
            // Wait
            sclPhase := 3
          }
          is(3) {
            // SCL low
            sclOut := False
            sclEnable := True
            sclPhase := 0

            when(bitCnt === 0) {
              state := I2cState.DATA_ACK
              sdaOut := True  // Release for ACK
              sdaEnable := False
            } otherwise {
              bitCnt := bitCnt - 1
            }
          }
        }
      }

      is(I2cState.DATA_RX) {
        // Receive 8 bits of data
        switch(sclPhase) {
          is(0) {
            // SCL high
            sclOut := True
            sclEnable := False
            sclPhase := 1
          }
          is(1) {
            // Sample data on rising edge
            shiftReg := shiftReg(6 downto 0) ## sdaIn
            sclPhase := 2
          }
          is(2) {
            // SCL low
            sclOut := False
            sclEnable := True
            sclPhase := 0

            when(bitCnt === 0) {
              rxDataReg := shiftReg(6 downto 0) ## sdaIn
              state := I2cState.DATA_ACK
            } otherwise {
              bitCnt := bitCnt - 1
            }
          }
        }
      }

      is(I2cState.DATA_ACK) {
        // Handle ACK/NACK
        switch(sclPhase) {
          is(0) {
            // SCL low - setup ACK/NACK
            sdaOut := ackVal  // 0 = ACK, 1 = NACK
            sdaEnable := True
            sclPhase := 1
          }
          is(1) {
            // SCL high
            sclOut := True
            sclEnable := False
            sclPhase := 2

            // For TX, sample slave ACK
            when(doWrite) {
              rxack := sdaIn
            }
          }
          is(2) {
            // SCL low
            sclOut := False
            sclEnable := True
            sclPhase := 0
            sdaOut := True
            sdaEnable := False

            when(doStop) {
              state := I2cState.STOP_1
            } otherwise {
              state := I2cState.IDLE
              busy := False
              tip := False
              ifl := True
              doWrite := False
              doRead := False
            }
          }
        }
      }

      is(I2cState.STOP_1) {
        // STOP condition: SDA rises while SCL high
        switch(sclPhase) {
          is(0) {
            // SCL low, SDA low
            sdaOut := False
            sdaEnable := True
            sclPhase := 1
          }
          is(1) {
            // SCL high
            sclOut := True
            sclEnable := False
            sclPhase := 2
          }
          is(2) {
            // SDA rises - STOP
            sdaOut := True
            sdaEnable := False
            state := I2cState.STOP_2
          }
        }
      }

      is(I2cState.STOP_2) {
        // Complete STOP, return to IDLE
        state := I2cState.IDLE
        busy := False
        tip := False
        ifl := True
        doWrite := False
        doRead := False
      }
    }
  }

  // Handle disable
  when(!io.ctrl.en) {
    state := I2cState.IDLE
    busy := False
    tip := False
    sclOut := True
    sclEnable := False
    sdaOut := True
    sdaEnable := False
    doStart := False
    doStop := False
    doWrite := False
    doRead := False
  }

  // Clear IFL on STATUS read
  when(io.iflClear) {
    ifl := False
  }

  // Status output
  io.status.rxack := rxack
  io.status.busy := busy
  io.status.tip := tip
  io.status.ifl := ifl
  io.status.reserved := 0

  // Data output
  io.rxData := rxDataReg
}

// ============================================================================
// APB I2C Controller Top Level
// ============================================================================
case class ApbI2cCtrl(generics: ApbI2cCtrlGenerics) extends Component {
  val io = new Bundle {
    val apb = slave(Apb3(addressWidth = generics.apbAddrWidth, dataWidth = 32))
    val i2c = master(I2cBus())
    val irq = out Bool()
  }

  // Control register with command auto-clear
  val ctrlReg = RegInit(CtrlReg().getZero)
  val ctrlPulse = RegInit(CtrlReg().getZero)

  // Data registers
  val txDataReg = RegInit(B(0, generics.dataWidth bits))
  val rxDataReg = RegInit(B(0, generics.dataWidth bits))
  val addrReg = RegInit(B(0, generics.addrWidth bits))
  val prescaleReg = RegInit(U(0, 16 bits))

  // Command auto-clear logic
  // Command bits (sta/sto/rd/wr/ack) are cleared after one cycle
  val cmdClear = RegInit(False)
  when(cmdClear) {
    ctrlReg.sta := False
    ctrlReg.sto := False
    ctrlReg.rd := False
    ctrlReg.wr := False
    ctrlReg.ack := False
    cmdClear := False
  }

  // Capture command pulses
  ctrlPulse := ctrlReg

  // I2C Core
  val i2cCore = I2cMasterCore(generics)
  i2cCore.io.ctrl := ctrlReg
  i2cCore.io.txData := txDataReg
  i2cCore.io.slaveAddr := addrReg
  i2cCore.io.prescale := prescaleReg
  i2cCore.io.cmdPulse := ctrlPulse

  // I2C bus connections
  io.i2c.scl <> i2cCore.io.i2c.scl
  io.i2c.sda <> i2cCore.io.i2c.sda
  rxDataReg := i2cCore.io.rxData

  // IRQ
  io.irq := i2cCore.io.status.ifl && ctrlReg.ien

  // APB register access
  val apbAddr = io.apb.PADDR(4 downto 2)
  val apbWrite = io.apb.PSEL(0) && io.apb.PENABLE && io.apb.PWRITE
  val apbRead = io.apb.PSEL(0) && io.apb.PENABLE && !io.apb.PWRITE

  // APB write logic
  when(apbWrite) {
    switch(apbAddr) {
      is(ApbI2cCtrlRegs.CTRL / 4) {
        ctrlReg := io.apb.PWDATA.as(CtrlReg())
        cmdClear := True
      }
      is(ApbI2cCtrlRegs.DATA / 4) {
        txDataReg := io.apb.PWDATA(7 downto 0)
      }
      is(ApbI2cCtrlRegs.ADDR / 4) {
        addrReg := io.apb.PWDATA(generics.addrWidth - 1 downto 0)
      }
      is(ApbI2cCtrlRegs.PRESCALE / 4) {
        prescaleReg := io.apb.PWDATA(15 downto 0).asUInt
      }
      default {}
    }
  }

  // APB read logic
  io.apb.PRDATA := 0
  switch(apbAddr) {
    is(ApbI2cCtrlRegs.CTRL / 4) {
      io.apb.PRDATA := ctrlReg.asBits.resized
    }
    is(ApbI2cCtrlRegs.STATUS / 4) {
      io.apb.PRDATA := i2cCore.io.status.asBits.resized
    }
    is(ApbI2cCtrlRegs.DATA / 4) {
      io.apb.PRDATA := rxDataReg.resized
    }
    is(ApbI2cCtrlRegs.ADDR / 4) {
      io.apb.PRDATA := addrReg.resized
    }
    is(ApbI2cCtrlRegs.PRESCALE / 4) {
      io.apb.PRDATA := prescaleReg.asBits.resized
    }
    default {}
  }

  io.apb.PREADY := True
  io.apb.PSLVERROR := False

  // IFL clear on STATUS read
  i2cCore.io.iflClear := apbRead && apbAddr === ApbI2cCtrlRegs.STATUS / 4
}

// Verilog generation
object ApbI2cCtrlGen extends App {
  val generics = ApbI2cCtrlGenerics()
  SpinalConfig(targetDirectory = "rtl").generateVerilog(ApbI2cCtrl(generics))
}
