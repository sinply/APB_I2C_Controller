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

      if (dut.io.i2c.scl.writeEnable.toBoolean) fail("SCL should be high-Z when idle")
      if (dut.io.i2c.sda.writeEnable.toBoolean) fail("SDA should be high-Z when idle")
      if (!dut.io.i2c.scl.write.toBoolean) fail("SCL write should be True when idle")
      if (!dut.io.i2c.sda.write.toBoolean) fail("SDA write should be True when idle")
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
}
