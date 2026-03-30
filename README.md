# APB I2C Controller

A fully-featured I2C master controller with APB3 slave interface, written in SpinalHDL.

## Features

- **APB3 Slave Interface**: 32-bit data width, 5-bit address width
- **I2C Master Controller**: Complete protocol support
  - START/STOP condition generation
  - 7-bit and 10-bit slave addressing
  - Read/Write operations with ACK/NACK handling
  - Programmable clock prescaler
- **Open-Drain Bus Interface**: Proper bidirectional TriState signals
- **Interrupt Support**: Configurable IRQ output
- **Comprehensive Test Suite**: 10 tests covering APB registers and I2C protocol

## Architecture

```
┌─────────────────────────────────────────────────┐
│                  ApbI2cCtrl                      │
│  ┌─────────────┐      ┌──────────────────────┐  │
│  │ APB Slave   │      │   I2C Master Core    │  │
│  │ Interface   │─────▶│                      │  │
│  │             │      │  ┌────────────────┐  │  │
│  │ - CTRL      │      │  │ State Machine  │  │  │
│  │ - STATUS    │      │  │ IDLE -> START  │  │  │
│  │ - DATA      │      │  │   -> ADDR ->   │  │  │
│  │ - ADDR      │      │  │   ACK -> DATA  │  │  │
│  │ - PRESCALE  │      │  │   -> STOP      │  │  │
│  └─────────────┘      │  └────────────────┘  │  │
│                       └──────────────────────┘  │
└─────────────────────────────────────────────────┘
          │                              │
          ▼                              ▼
      APB Bus                         I2C Bus
```

## Register Map

| Offset | Register  | R/W | Description                          |
|--------|-----------|-----|--------------------------------------|
| 0x00   | CTRL      | R/W | Control register                     |
| 0x04   | STATUS    | R   | Status register (read-only)          |
| 0x08   | DATA      | R/W | TX/RX data register                  |
| 0x0C   | ADDR      | R/W | Slave address register               |
| 0x10   | PRESCALE  | R/W | Clock prescaler (16-bit)             |

### CTRL Register (0x00)

| Bit | Name | Description |
|-----|------|-------------|
| 0   | en   | Core enable |
| 1   | ien  | Interrupt enable |
| 2   | sta  | Generate START condition (auto-clear) |
| 3   | sto  | Generate STOP condition (auto-clear) |
| 4   | rd   | Read operation (auto-clear) |
| 5   | wr   | Write operation (auto-clear) |
| 6   | ack  | ACK/NACK for read: 0=ACK, 1=NACK (auto-clear) |
| 7-31| -    | Reserved |

### STATUS Register (0x04)

| Bit | Name  | Description |
|-----|-------|-------------|
| 0   | rxack | Received ACK: 0=ACK, 1=NACK |
| 1   | busy  | Bus busy flag |
| 2   | tip   | Transfer in progress |
| 3   | ifl   | Interrupt flag (cleared on STATUS read) |
| 4-31| -     | Reserved |

## Build Requirements

- SBT 1.8.0+
- Scala 2.11.12
- SpinalHDL 1.14.1
- Verilator (for simulation)

## Build Commands

Use `scripts/sbtw.sh` instead of `sbt` directly (WSL environment wrapper).

```bash
# Generate Verilog
scripts/sbtw.sh run "runMain apb_i2c.ApbI2cCtrlGen"

# Run tests
scripts/sbtw.sh test

# Clean build artifacts
scripts/sbtw.sh run "clean"
```

## Generated RTL

After running the Verilog generation, the output is located at:

```
rtl/ApbI2cCtrl.v
```

## I/O Signals

| Signal | Direction | Description |
|--------|-----------|-------------|
| `clk` | input | System clock |
| `reset` | input | Active-high reset |
| `io_apb_*` | APB slave | APB3 bus interface |
| `io_i2c_scl_*` | I2C | SCL (open-drain) |
| `io_i2c_sda_*` | I2C | SDA (open-drain) |
| `io_irq` | output | Interrupt request |

## Usage Example

### Write Operation

1. Set prescaler: `PRESCALE = (Fclk / (5 * Fi2c)) - 1`
2. Set slave address: `ADDR = 0x55`
3. Write data: `DATA = 0xAA`
4. Start transfer: `CTRL = en | sta | wr`
5. Wait for completion: poll `STATUS.tip == 0`
6. Generate STOP: `CTRL = en | sto`

### Read Operation

1. Set prescaler and address
2. Start read: `CTRL = en | sta | rd`
3. Wait for completion
4. Read data from `DATA`
5. Send ACK/NACK and STOP

## License

MIT License
