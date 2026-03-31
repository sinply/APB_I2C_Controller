package apb_i2c

import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.amba3.apb._
import org.scalatest.FunSuite

class ApbI2cCtrlTest extends FunSuite {

  val generics = ApbI2cCtrlGenerics()

  def apbWrite(dut: ApbI2cCtrl, addr: Int, data: Long): Unit = {
    dut.io.apb.PADDR #= addr
    dut.io.apb.PSEL #= 1
    dut.io.apb.PWRITE #= true
    dut.io.apb.PWDATA #= data
    dut.clockDomain.waitSampling()
    dut.io.apb.PENABLE #= true
    dut.clockDomain.waitSampling()
    dut.io.apb.PENABLE #= false
    dut.io.apb.PSEL #= 0
  }

  def apbRead(dut: ApbI2cCtrl, addr: Int): Int = {
    dut.io.apb.PADDR #= addr
    dut.io.apb.PSEL #= 1
    dut.io.apb.PWRITE #= false
    dut.clockDomain.waitSampling()
    dut.io.apb.PENABLE #= true
    dut.clockDomain.waitSampling()
    val data = dut.io.apb.PRDATA.toInt
    dut.io.apb.PENABLE #= false
    dut.io.apb.PSEL #= 0
    data
  }

  def initI2cBus(dut: ApbI2cCtrl): Unit = {
    dut.io.i2c.scl.read #= true
    dut.io.i2c.sda.read #= true
  }

  def waitTicks(dut: ApbI2cCtrl, ticks: Int): Unit = {
    for (_ <- 0 until ticks) {
      dut.clockDomain.waitSampling()
    }
  }

  test("APB-01: Register reset values") {
    SimConfig.withWave.compile(ApbI2cCtrl(generics)).doSim { dut =>
      dut.clockDomain.forkStimulus(20)
      dut.clockDomain.assertReset()
      dut.clockDomain.waitSampling(5)
      dut.clockDomain.deassertReset()
      dut.clockDomain.waitSampling(5)

      initI2cBus(dut)
      dut.io.apb.PADDR #= 0
      dut.io.apb.PSEL #= 0
      dut.io.apb.PENABLE #= false
      dut.io.apb.PWRITE #= false

      val ctrlVal = apbRead(dut, ApbI2cCtrlRegs.CTRL)
      if (ctrlVal != 0) fail(s"CTRL reset should be 0, got $ctrlVal")

      val statusVal = apbRead(dut, ApbI2cCtrlRegs.STATUS)
      if ((statusVal & 0x0F) != 0) fail(s"STATUS reset should be 0, got $statusVal")

      val dataVal = apbRead(dut, ApbI2cCtrlRegs.DATA)
      if (dataVal != 0) fail(s"DATA reset should be 0, got $dataVal")

      val addrVal = apbRead(dut, ApbI2cCtrlRegs.ADDR)
      if (addrVal != 0) fail(s"ADDR reset should be 0, got $addrVal")

      val prescaleVal = apbRead(dut, ApbI2cCtrlRegs.PRESCALE)
      if (prescaleVal != 0) fail(s"PRESCALE reset should be 0, got $prescaleVal")
    }
  }

  test("APB-02: CTRL register read/write") {
    SimConfig.withWave.compile(ApbI2cCtrl(generics)).doSim { dut =>
      dut.clockDomain.forkStimulus(20)
      dut.clockDomain.assertReset()
      dut.clockDomain.waitSampling(5)
      dut.clockDomain.deassertReset()
      dut.clockDomain.waitSampling(5)

      initI2cBus(dut)
      dut.io.apb.PADDR #= 0
      dut.io.apb.PSEL #= 0
      dut.io.apb.PENABLE #= false
      dut.io.apb.PWRITE #= false

      apbWrite(dut, ApbI2cCtrlRegs.CTRL, 0x7F)
      waitTicks(dut, 2)
      val ctrlVal = apbRead(dut, ApbI2cCtrlRegs.CTRL)

      if ((ctrlVal & 0x03) != 0x03) fail(s"en/ien should remain, got ${ctrlVal & 0x03}")
      if ((ctrlVal & 0x7C) != 0) fail(s"cmd bits should clear, got ${ctrlVal & 0x7C}")
    }
  }

  test("APB-03: STATUS register is read-only") {
    SimConfig.withWave.compile(ApbI2cCtrl(generics)).doSim { dut =>
      dut.clockDomain.forkStimulus(20)
      dut.clockDomain.assertReset()
      dut.clockDomain.waitSampling(5)
      dut.clockDomain.deassertReset()
      dut.clockDomain.waitSampling(5)

      initI2cBus(dut)
      dut.io.apb.PADDR #= 0
      dut.io.apb.PSEL #= 0
      dut.io.apb.PENABLE #= false
      dut.io.apb.PWRITE #= false

      // Write to STATUS register - should have no effect
      // Use Long for unsigned 32-bit value
      apbWrite(dut, ApbI2cCtrlRegs.STATUS, 0xFFFFFFFFL)
      val statusVal = apbRead(dut, ApbI2cCtrlRegs.STATUS)
      if (statusVal != 0) fail(s"STATUS should be read-only, got $statusVal")
    }
  }

  test("APB-04: DATA register write") {
    SimConfig.withWave.compile(ApbI2cCtrl(generics)).doSim { dut =>
      dut.clockDomain.forkStimulus(20)
      dut.clockDomain.assertReset()
      dut.clockDomain.waitSampling(5)
      dut.clockDomain.deassertReset()
      dut.clockDomain.waitSampling(5)

      initI2cBus(dut)
      dut.io.apb.PADDR #= 0
      dut.io.apb.PSEL #= 0
      dut.io.apb.PENABLE #= false
      dut.io.apb.PWRITE #= false

      // DATA register is write-only for TX, read returns RX data (from I2C reads)
      // Test that we can write to DATA register without error
      apbWrite(dut, ApbI2cCtrlRegs.DATA, 0xA5)
      apbWrite(dut, ApbI2cCtrlRegs.DATA, 0x5A)

      // RX data register should be 0 until an I2C read completes
      val dataVal = apbRead(dut, ApbI2cCtrlRegs.DATA)
      // This is expected to be 0 since no I2C read has occurred
    }
  }

  test("APB-05: ADDR register read/write") {
    SimConfig.withWave.compile(ApbI2cCtrl(generics)).doSim { dut =>
      dut.clockDomain.forkStimulus(20)
      dut.clockDomain.assertReset()
      dut.clockDomain.waitSampling(5)
      dut.clockDomain.deassertReset()
      dut.clockDomain.waitSampling(5)

      initI2cBus(dut)
      dut.io.apb.PADDR #= 0
      dut.io.apb.PSEL #= 0
      dut.io.apb.PENABLE #= false
      dut.io.apb.PWRITE #= false

      apbWrite(dut, ApbI2cCtrlRegs.ADDR, 0x55)
      val addrVal = apbRead(dut, ApbI2cCtrlRegs.ADDR)
      if ((addrVal & 0x7F) != 0x55) fail(s"ADDR read failed, got $addrVal")

      apbWrite(dut, ApbI2cCtrlRegs.ADDR, 0x7F)
      val addrVal2 = apbRead(dut, ApbI2cCtrlRegs.ADDR)
      if ((addrVal2 & 0x7F) != 0x7F) fail(s"ADDR max failed, got $addrVal2")
    }
  }

  test("APB-06: PRESCALE register read/write") {
    SimConfig.withWave.compile(ApbI2cCtrl(generics)).doSim { dut =>
      dut.clockDomain.forkStimulus(20)
      dut.clockDomain.assertReset()
      dut.clockDomain.waitSampling(5)
      dut.clockDomain.deassertReset()
      dut.clockDomain.waitSampling(5)

      initI2cBus(dut)
      dut.io.apb.PADDR #= 0
      dut.io.apb.PSEL #= 0
      dut.io.apb.PENABLE #= false
      dut.io.apb.PWRITE #= false

      apbWrite(dut, ApbI2cCtrlRegs.PRESCALE, 0x0062)
      val prescaleVal = apbRead(dut, ApbI2cCtrlRegs.PRESCALE)
      if ((prescaleVal & 0xFFFF) != 0x62) fail(s"PRESCALE read failed, got $prescaleVal")

      apbWrite(dut, ApbI2cCtrlRegs.PRESCALE, 0xFFFF)
      val prescaleVal2 = apbRead(dut, ApbI2cCtrlRegs.PRESCALE)
      if ((prescaleVal2 & 0xFFFF) != 0xFFFF) fail(s"PRESCALE max failed, got $prescaleVal2")
    }
  }

  test("I2C-01: I2C bus idle state") {
    SimConfig.withWave.compile(ApbI2cCtrl(generics)).doSim { dut =>
      dut.clockDomain.forkStimulus(20)
      dut.clockDomain.assertReset()
      dut.clockDomain.waitSampling(5)
      dut.clockDomain.deassertReset()
      dut.clockDomain.waitSampling(5)

      initI2cBus(dut)
      dut.io.apb.PADDR #= 0
      dut.io.apb.PSEL #= 0
      dut.io.apb.PENABLE #= false
      dut.io.apb.PWRITE #= false

      // In proper open-drain:
      // - writeEnable=False means high-Z (bus released, pull-up keeps it high)
      // - write should ALWAYS be False (never drive high)
      if (dut.io.i2c.scl.writeEnable.toBoolean) fail("SCL should be high-Z when idle (writeEnable=False)")
      if (dut.io.i2c.sda.writeEnable.toBoolean) fail("SDA should be high-Z when idle (writeEnable=False)")
      if (dut.io.i2c.scl.write.toBoolean) fail("SCL write should be False (open-drain never drives high)")
      if (dut.io.i2c.sda.write.toBoolean) fail("SDA write should be False (open-drain never drives high)")
    }
  }

  test("I2C-02: Busy flag after START") {
    SimConfig.withWave.compile(ApbI2cCtrl(generics)).doSim { dut =>
      dut.clockDomain.forkStimulus(20)
      dut.clockDomain.assertReset()
      dut.clockDomain.waitSampling(5)
      dut.clockDomain.deassertReset()
      dut.clockDomain.waitSampling(5)

      initI2cBus(dut)
      dut.io.apb.PADDR #= 0
      dut.io.apb.PSEL #= 0
      dut.io.apb.PENABLE #= false
      dut.io.apb.PWRITE #= false

      val statusBefore = apbRead(dut, ApbI2cCtrlRegs.STATUS)
      if ((statusBefore & 0x02) != 0) fail("BUSY should be 0 before START")

      apbWrite(dut, ApbI2cCtrlRegs.PRESCALE, 4)
      apbWrite(dut, ApbI2cCtrlRegs.CTRL, 0x05)

      waitTicks(dut, 20)

      val statusAfter = apbRead(dut, ApbI2cCtrlRegs.STATUS)
      if ((statusAfter & 0x02) == 0) fail("BUSY should be 1 after START")
    }
  }

  test("IRQ-01: IRQ output behavior") {
    SimConfig.withWave.compile(ApbI2cCtrl(generics)).doSim { dut =>
      dut.clockDomain.forkStimulus(20)
      dut.clockDomain.assertReset()
      dut.clockDomain.waitSampling(5)
      dut.clockDomain.deassertReset()
      dut.clockDomain.waitSampling(5)

      initI2cBus(dut)
      dut.io.apb.PADDR #= 0
      dut.io.apb.PSEL #= 0
      dut.io.apb.PENABLE #= false
      dut.io.apb.PWRITE #= false

      if (dut.io.irq.toBoolean) fail("IRQ should be low initially")

      apbWrite(dut, ApbI2cCtrlRegs.CTRL, 0x02)
      if (dut.io.irq.toBoolean) fail("IRQ should be low without IFL")

      apbWrite(dut, ApbI2cCtrlRegs.CTRL, 0x00)
      if (dut.io.irq.toBoolean) fail("IRQ should be low when disabled")
    }
  }

  test("I2C-03: Open-drain never drives high") {
    SimConfig.withWave.compile(ApbI2cCtrl(generics)).doSim { dut =>
      dut.clockDomain.forkStimulus(20)
      dut.clockDomain.assertReset()
      dut.clockDomain.waitSampling(5)
      dut.clockDomain.deassertReset()
      dut.clockDomain.waitSampling(5)

      initI2cBus(dut)
      dut.io.apb.PADDR #= 0
      dut.io.apb.PSEL #= 0
      dut.io.apb.PENABLE #= false
      dut.io.apb.PWRITE #= false

      apbWrite(dut, ApbI2cCtrlRegs.PRESCALE, 2)
      apbWrite(dut, ApbI2cCtrlRegs.CTRL, 0x01)

      for (_ <- 0 until 100) {
        dut.clockDomain.waitSampling()

        if (dut.io.i2c.scl.writeEnable.toBoolean && dut.io.i2c.scl.write.toBoolean) {
          fail("SCL should never drive high")
        }
        if (dut.io.i2c.sda.writeEnable.toBoolean && dut.io.i2c.sda.write.toBoolean) {
          fail("SDA should never drive high")
        }
      }
    }
  }

  // ===========================================================================
  // Bug Condition Exploration Tests
  // These tests encode EXPECTED (correct) behavior.
  // They should FAIL on the current unfixed code, confirming bugs exist.
  // ===========================================================================

  /** Helper: standard DUT init for bug exploration tests */
  def initDut(dut: ApbI2cCtrl): Unit = {
    dut.clockDomain.forkStimulus(20)
    dut.clockDomain.assertReset()
    dut.clockDomain.waitSampling(5)
    dut.clockDomain.deassertReset()
    dut.clockDomain.waitSampling(5)
    initI2cBus(dut)
    dut.io.apb.PADDR #= 0
    dut.io.apb.PSEL #= 0
    dut.io.apb.PENABLE #= false
    dut.io.apb.PWRITE #= false
  }

  /** Helper: start an I2C write transaction (START + enable + write cmd) */
  def startI2cWriteTransaction(dut: ApbI2cCtrl, slaveAddr: Int, txData: Int, prescale: Int): Unit = {
    apbWrite(dut, ApbI2cCtrlRegs.PRESCALE, prescale)
    apbWrite(dut, ApbI2cCtrlRegs.ADDR, slaveAddr)
    apbWrite(dut, ApbI2cCtrlRegs.DATA, txData)
    // Enable core + START + WRITE: en=1, sta=1, wr=1 => bits 0,2,5 = 0x25
    apbWrite(dut, ApbI2cCtrlRegs.CTRL, 0x25)
  }

  /** Helper: start an I2C read transaction (START + enable + read cmd) */
  def startI2cReadTransaction(dut: ApbI2cCtrl, slaveAddr: Int, prescale: Int): Unit = {
    apbWrite(dut, ApbI2cCtrlRegs.PRESCALE, prescale)
    apbWrite(dut, ApbI2cCtrlRegs.ADDR, slaveAddr)
    // Enable core + START + READ + NACK: en=1, sta=1, rd=1, ack=1 => bits 0,2,4,6 = 0x55
    apbWrite(dut, ApbI2cCtrlRegs.CTRL, 0x55)
  }

  /**
   * Bug 1.1: Open-drain violation during complete I2C transaction.
   * Validates: Requirements 2.1
   *
   * Expected behavior: In proper open-drain I2C, the write (drive) value should
   * always be False (drive low when enabled). Releasing the bus is done by setting
   * writeEnable=False. The write signal should NEVER be True, because that would
   * mean "drive high" which violates open-drain protocol.
   *
   * Current bug: sclOut is initialized to True (RegInit(True)) and explicitly set
   * to True in IDLE, ADDR sclPhase=1, DATA_TX sclPhase=1, STOP_1 sclPhase=1, etc.
   * While writeEnable=False prevents actual driving in most cases, the semantic
   * violation means write=True exists in the design, which is incorrect for open-drain.
   */
  test("BUG-1.1: Open-drain violation - write=True AND writeEnable=True during I2C transaction") {
    SimConfig.withWave.compile(ApbI2cCtrl(generics)).doSim { dut =>
      initDut(dut)

      // Use a small prescale for faster simulation
      startI2cWriteTransaction(dut, slaveAddr = 0x50, txData = 0xA5, prescale = 2)

      var violationFound = false
      var violationDetail = ""

      // Monitor for 2000 cycles - enough for START + ADDR + ACK + DATA phases
      for (cycle <- 0 until 2000) {
        dut.clockDomain.waitSampling()

        val sclWrite = dut.io.i2c.scl.write.toBoolean
        val sclEnable = dut.io.i2c.scl.writeEnable.toBoolean
        val sdaWrite = dut.io.i2c.sda.write.toBoolean
        val sdaEnable = dut.io.i2c.sda.writeEnable.toBoolean

        // Simulate slave ACK
        dut.io.i2c.scl.read #= !sclEnable
        dut.io.i2c.sda.read #= false  // slave ACK

        // Check: when writeEnable is True (actively driving), write must be False
        // In open-drain, you can only drive LOW. Driving HIGH is a violation.
        if (sclWrite && sclEnable && !violationFound) {
          violationFound = true
          violationDetail = s"cycle=$cycle: SCL write=True AND writeEnable=True (actively driving high)"
        }
        if (sdaWrite && sdaEnable && !violationFound) {
          violationFound = true
          violationDetail = s"cycle=$cycle: SDA write=True AND writeEnable=True (actively driving high)"
        }
      }

      if (violationFound) {
        fail(s"Open-drain violation detected: $violationDetail")
      }
    }
  }

  /**
   * Bug 1.2: DATA_RX uses only 3 sclPhase instead of 4.
   * Validates: Requirements 2.2
   *
   * Expected behavior: Each bit in DATA_RX should use 4 prescaler ticks
   * (sclPhase 0,1,2,3), symmetric with DATA_TX.
   *
   * Current bug: DATA_RX only uses 3 sclPhase (0,1,2), so each bit only
   * gets 3 prescaler ticks instead of 4.
   */
  test("BUG-1.2: DATA_RX sclPhase count per bit should be 4") {
    SimConfig.withWave.compile(ApbI2cCtrl(generics)).doSim { dut =>
      initDut(dut)
      startI2cReadTransaction(dut, slaveAddr = 0x50, prescale = 2)

      // Slave always ACKs and sends data 0x00
      dut.io.i2c.sda.read #= false

      var dataRxTickCount = 0
      var wasInDataRx = false
      var done = false

      for (cycle <- 0 until 8000 if !done) {
        dut.clockDomain.waitSampling()

        val sclEnable = dut.io.i2c.scl.writeEnable.toBoolean
        dut.io.i2c.scl.read #= !sclEnable
        dut.io.i2c.sda.read #= false  // slave ACK + data=0

        // Read internal state
        val stateVal = dut.i2cCore.state.toEnum
        val tickVal = dut.i2cCore.tick.toBoolean

        if (stateVal == I2cState.DATA_RX) {
          if (!wasInDataRx) {
            wasInDataRx = true
            dataRxTickCount = 0
          }
          if (tickVal) {
            dataRxTickCount += 1
          }
        } else if (wasInDataRx && !done) {
          done = true
          // Just left DATA_RX - calculate ticks per bit
          // 8 bits received, so ticks per bit = totalTicks / 8
          val ticksPerBitVal = dataRxTickCount.toDouble / 8.0
          if (ticksPerBitVal < 3.5) {
            fail(s"DATA_RX uses only $dataRxTickCount prescaler ticks for 8 bits " +
              s"(${ticksPerBitVal} per bit), expected 4 per bit (32 total)")
          }
        }
      }

      if (!wasInDataRx) {
        fail("DATA_RX state was never reached during the I2C read transaction")
      }
    }
  }

  /**
   * Bug 1.3: ctrlPulse command bits persist for 2 cycles after CTRL write.
   * Validates: Requirements 2.3
   *
   * Expected behavior: After writing to CTRL register, command bits in
   * ctrlPulse should only be active for exactly 1 clock cycle.
   *
   * Current bug: ctrlPulse is a register that copies ctrlReg. Since cmdClear
   * takes effect one cycle after the write, ctrlPulse carries command bits
   * for 2 cycles.
   */
  test("BUG-1.3: ctrlPulse command bits should only last one cycle") {
    SimConfig.withWave.compile(ApbI2cCtrl(generics)).doSim { dut =>
      initDut(dut)

      // Set prescale first
      apbWrite(dut, ApbI2cCtrlRegs.PRESCALE, 4)

      // Wait a few cycles to ensure clean state
      waitTicks(dut, 5)

      // Write CTRL with sta=1 (bit 2): en=1, sta=1 => 0x05
      apbWrite(dut, ApbI2cCtrlRegs.CTRL, 0x05)

      // Now monitor ctrlPulse.sta for the next several cycles
      // It should be True for exactly 1 cycle, then False
      var cyclesWithStaActive = 0
      var monitoringStarted = false

      for (cycle <- 0 until 20) {
        dut.clockDomain.waitSampling()

        // Access internal ctrlPulse signal
        val staPulse = dut.ctrlPulse.sta.toBoolean

        if (staPulse) {
          cyclesWithStaActive += 1
        }
      }

      if (cyclesWithStaActive > 1) {
        fail(s"ctrlPulse.sta was active for $cyclesWithStaActive cycles, expected exactly 1 cycle")
      } else if (cyclesWithStaActive == 0) {
        fail("ctrlPulse.sta was never active - command pulse not generated")
      }
    }
  }

  /**
   * Bug 1.4: ADDR_ACK uses only 3 sclPhase instead of 4.
   * Validates: Requirements 2.4
   *
   * Expected behavior: ADDR_ACK should use 4 sclPhase stages (0,1,2,3),
   * consistent with ADDR and DATA_TX.
   *
   * Current bug: ADDR_ACK only uses 3 sclPhase (0,1,2), missing the
   * 4th phase for proper SCL low hold time.
   */
  test("BUG-1.4: ADDR_ACK sclPhase count should be 4") {
    SimConfig.withWave.compile(ApbI2cCtrl(generics)).doSim { dut =>
      initDut(dut)

      // Start a write transaction to trigger ADDR + ADDR_ACK
      startI2cWriteTransaction(dut, slaveAddr = 0x50, txData = 0xA5, prescale = 2)

      // Slave ACKs
      dut.io.i2c.sda.read #= false

      var addrAckTickCount = 0
      var wasInAddrAck = false
      var addrAckCompleted = false

      for (cycle <- 0 until 8000 if !addrAckCompleted) {
        dut.clockDomain.waitSampling()

        val sclEnable = dut.io.i2c.scl.writeEnable.toBoolean
        dut.io.i2c.scl.read #= !sclEnable
        dut.io.i2c.sda.read #= false  // slave ACK

        val stateVal = dut.i2cCore.state.toEnum
        val tickVal = dut.i2cCore.tick.toBoolean

        if (stateVal == I2cState.ADDR_ACK) {
          if (!wasInAddrAck) {
            wasInAddrAck = true
            addrAckTickCount = 0
          }
          if (tickVal) {
            addrAckTickCount += 1
          }
        } else if (wasInAddrAck && !addrAckCompleted) {
          addrAckCompleted = true
          if (addrAckTickCount < 4) {
            fail(s"ADDR_ACK used only $addrAckTickCount prescaler ticks, expected 4")
          }
        }
      }

      if (!wasInAddrAck) {
        fail("ADDR_ACK state was never reached during the I2C transaction")
      }
      if (!addrAckCompleted) {
        fail("ADDR_ACK state never completed (stuck in ADDR_ACK)")
      }
    }
  }
}
