# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

**Always use `scripts/sbtw.sh` instead of `sbt` directly.**

### Generating Verilog
```bash
scripts/sbtw.sh run "runMain apb_i2c.ApbI2cCtrlGen"
```

### Running Tests
```bash
scripts/sbtw.sh test
```

### Other Commands
```bash
scripts/sbtw.sh run "clean"    # Clean build artifacts
scripts/sbtw.sh check          # Check environment status
scripts/sbtw.sh list           # List available modules
```

### Toolchain Versions
- SBT: 1.8.0
- Scala: 2.11.12
- SpinalHDL: 1.14.1

## Architecture Overview

**Current Status: Framework implementation.** The I2C state machine in `I2cMasterCore` is a skeleton that needs completion for full I2C protocol support.

### Module Hierarchy
- `ApbI2cCtrlGenerics`: Configuration parameters
  - `clockFrequency`: System clock (default: 50 MHz)
  - `dataWidth`: I2C data width (fixed: 8 bits)
  - `addrWidth`: I2C address width (7 or 10 bits)
  - `apbAddrWidth`: APB address width (default: 5 bits)
- `I2cBus`: I2C bus interface with `IMasterSlave` trait
- `I2cMasterCore`: I2C master controller core
- `ApbI2cCtrl`: Top-level with APB3 slave interface

### Register Map
| Offset | Register | Description |
|--------|----------|-------------|
| 0x00 | CTRL | Control: en, ien, sta, sto, rd, wr, ack |
| 0x04 | STATUS | Status: rxack, busy, tip, ifl |
| 0x08 | DATA | TX/RX data |
| 0x0C | ADDR | I2C slave address |
| 0x10 | PRESCALE | Baud rate divisor (16-bit) |

### APB Interface
- 32-bit data width
- 5-bit address width (32-byte register space)
- No wait states (`PREADY` always high)

### I/O Signals
| Signal | Direction | Description |
|--------|-----------|-------------|
| `apb` | slave | APB3 bus interface |
| `i2c.scl` | out | I2C clock (open-drain) |
| `i2c.sda` | out | I2C data (open-drain) |
| `irq` | out | Interrupt request |

## Implementation Notes

### TODO: I2C State Machine
The `I2cMasterCore` needs a complete state machine for:
- START condition generation
- Address transmission (7/10-bit)
- Data read/write with ACK/NACK
- STOP condition generation
- Clock stretching support

### Design Patterns
- **IMasterSlave trait:** `I2cBus` implements this for proper `master()` direction handling
- **Register initialization:** Use `RegInit` with `.getZero` or explicit values
- **APB addressing:** Use `PADDR(4 downto 2)` for word-aligned access (divide byte offset by 4)

## WSL Environment

`scripts/sbtw.sh` wraps Windows sbt via `cmd.exe`. See `scripts/ENVIRONMENT.md` for details.

## Output

- Generated Verilog: `rtl/ApbI2cCtrl.v`
- Simulation artifacts: `simWorkspace/` (gitignored)
