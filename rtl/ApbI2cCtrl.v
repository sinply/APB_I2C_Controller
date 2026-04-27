// Generator : SpinalHDL v1.14.1    git head : e3230fe124f961bcb2b5fc35bcc23044a541c122
// Component : ApbI2cCtrl
// Git hash  : c4a9573091a7ff0e2902ea3a2353ef7b3181dc05

`timescale 1ns/1ps

module ApbI2cCtrl (
  input  wire [4:0]    io_apb_PADDR,
  input  wire [0:0]    io_apb_PSEL,
  input  wire          io_apb_PENABLE,
  output wire          io_apb_PREADY,
  input  wire          io_apb_PWRITE,
  input  wire [31:0]   io_apb_PWDATA,
  output reg  [31:0]   io_apb_PRDATA,
  output wire          io_apb_PSLVERROR,
  input  wire          io_i2c_scl_read,
  output wire          io_i2c_scl_write,
  output wire          io_i2c_scl_writeEnable,
  input  wire          io_i2c_sda_read,
  output wire          io_i2c_sda_write,
  output wire          io_i2c_sda_writeEnable,
  output wire          io_irq,
  input  wire          clk,
  input  wire          reset
);

  wire                i2cCore_io_iflClear;
  wire                i2cCore_io_status_rxack;
  wire                i2cCore_io_status_busy;
  wire                i2cCore_io_status_tip;
  wire                i2cCore_io_status_ifl;
  wire       [27:0]   i2cCore_io_status_reserved;
  wire       [7:0]    i2cCore_io_rxData;
  wire                i2cCore_io_i2c_scl_write;
  wire                i2cCore_io_i2c_scl_writeEnable;
  wire                i2cCore_io_i2c_sda_write;
  wire                i2cCore_io_i2c_sda_writeEnable;
  wire       [15:0]   _zz_io_apb_PRDATA;
  reg                 ctrlReg_en;
  reg                 ctrlReg_ien;
  reg                 ctrlReg_sta;
  reg                 ctrlReg_sto;
  reg                 ctrlReg_rd;
  reg                 ctrlReg_wr;
  reg                 ctrlReg_ack;
  reg        [24:0]   ctrlReg_reserved;
  reg                 ctrlPulse_en;
  reg                 ctrlPulse_ien;
  reg                 ctrlPulse_sta;
  reg                 ctrlPulse_sto;
  reg                 ctrlPulse_rd;
  reg                 ctrlPulse_wr;
  reg                 ctrlPulse_ack;
  reg        [24:0]   ctrlPulse_reserved;
  reg        [7:0]    txDataReg;
  reg        [7:0]    rxDataReg;
  reg        [6:0]    addrReg;
  reg        [15:0]   prescaleReg;
  reg                 cmdClear;
  wire       [2:0]    apbAddr;
  wire                apbWrite;
  wire                apbRead;
  wire       [31:0]   _zz_ctrlReg_en;

  assign _zz_io_apb_PRDATA = prescaleReg;
  I2cMasterCore i2cCore (
    .io_ctrl_en             (ctrlReg_en                      ), //i
    .io_ctrl_ien            (ctrlReg_ien                     ), //i
    .io_ctrl_sta            (ctrlReg_sta                     ), //i
    .io_ctrl_sto            (ctrlReg_sto                     ), //i
    .io_ctrl_rd             (ctrlReg_rd                      ), //i
    .io_ctrl_wr             (ctrlReg_wr                      ), //i
    .io_ctrl_ack            (ctrlReg_ack                     ), //i
    .io_ctrl_reserved       (ctrlReg_reserved[24:0]          ), //i
    .io_status_rxack        (i2cCore_io_status_rxack         ), //o
    .io_status_busy         (i2cCore_io_status_busy          ), //o
    .io_status_tip          (i2cCore_io_status_tip           ), //o
    .io_status_ifl          (i2cCore_io_status_ifl           ), //o
    .io_status_reserved     (i2cCore_io_status_reserved[27:0]), //o
    .io_txData              (txDataReg[7:0]                  ), //i
    .io_rxData              (i2cCore_io_rxData[7:0]          ), //o
    .io_slaveAddr           (addrReg[6:0]                    ), //i
    .io_prescale            (prescaleReg[15:0]               ), //i
    .io_i2c_scl_read        (io_i2c_scl_read                 ), //i
    .io_i2c_scl_write       (i2cCore_io_i2c_scl_write        ), //o
    .io_i2c_scl_writeEnable (i2cCore_io_i2c_scl_writeEnable  ), //o
    .io_i2c_sda_read        (io_i2c_sda_read                 ), //i
    .io_i2c_sda_write       (i2cCore_io_i2c_sda_write        ), //o
    .io_i2c_sda_writeEnable (i2cCore_io_i2c_sda_writeEnable  ), //o
    .io_cmdPulse_en         (ctrlPulse_en                    ), //i
    .io_cmdPulse_ien        (ctrlPulse_ien                   ), //i
    .io_cmdPulse_sta        (ctrlPulse_sta                   ), //i
    .io_cmdPulse_sto        (ctrlPulse_sto                   ), //i
    .io_cmdPulse_rd         (ctrlPulse_rd                    ), //i
    .io_cmdPulse_wr         (ctrlPulse_wr                    ), //i
    .io_cmdPulse_ack        (ctrlPulse_ack                   ), //i
    .io_cmdPulse_reserved   (ctrlPulse_reserved[24:0]        ), //i
    .io_iflClear            (i2cCore_io_iflClear             ), //i
    .clk                    (clk                             ), //i
    .reset                  (reset                           )  //i
  );
  assign io_i2c_scl_write = i2cCore_io_i2c_scl_write;
  assign io_i2c_scl_writeEnable = i2cCore_io_i2c_scl_writeEnable;
  assign io_i2c_sda_write = i2cCore_io_i2c_sda_write;
  assign io_i2c_sda_writeEnable = i2cCore_io_i2c_sda_writeEnable;
  assign io_irq = (i2cCore_io_status_ifl && ctrlReg_ien);
  assign apbAddr = io_apb_PADDR[4 : 2];
  assign apbWrite = ((io_apb_PSEL[0] && io_apb_PENABLE) && io_apb_PWRITE);
  assign apbRead = ((io_apb_PSEL[0] && io_apb_PENABLE) && (! io_apb_PWRITE));
  assign _zz_ctrlReg_en = io_apb_PWDATA;
  always @(*) begin
    io_apb_PRDATA = 32'h0;
    case(apbAddr)
      3'b000 : begin
        io_apb_PRDATA = {ctrlReg_reserved,{ctrlReg_ack,{ctrlReg_wr,{ctrlReg_rd,{ctrlReg_sto,{ctrlReg_sta,{ctrlReg_ien,ctrlReg_en}}}}}}};
      end
      3'b001 : begin
        io_apb_PRDATA = {i2cCore_io_status_reserved,{i2cCore_io_status_ifl,{i2cCore_io_status_tip,{i2cCore_io_status_busy,i2cCore_io_status_rxack}}}};
      end
      3'b010 : begin
        io_apb_PRDATA = {24'd0, rxDataReg};
      end
      3'b011 : begin
        io_apb_PRDATA = {25'd0, addrReg};
      end
      3'b100 : begin
        io_apb_PRDATA = {16'd0, _zz_io_apb_PRDATA};
      end
      default : begin
      end
    endcase
  end

  assign io_apb_PREADY = 1'b1;
  assign io_apb_PSLVERROR = 1'b0;
  assign i2cCore_io_iflClear = (apbRead && (apbAddr == 3'b001));
  always @(posedge clk or posedge reset) begin
    if(reset) begin
      ctrlReg_en <= 1'b0;
      ctrlReg_ien <= 1'b0;
      ctrlReg_sta <= 1'b0;
      ctrlReg_sto <= 1'b0;
      ctrlReg_rd <= 1'b0;
      ctrlReg_wr <= 1'b0;
      ctrlReg_ack <= 1'b0;
      ctrlReg_reserved <= 25'h0;
      ctrlPulse_en <= 1'b0;
      ctrlPulse_ien <= 1'b0;
      ctrlPulse_sta <= 1'b0;
      ctrlPulse_sto <= 1'b0;
      ctrlPulse_rd <= 1'b0;
      ctrlPulse_wr <= 1'b0;
      ctrlPulse_ack <= 1'b0;
      ctrlPulse_reserved <= 25'h0;
      txDataReg <= 8'h0;
      rxDataReg <= 8'h0;
      addrReg <= 7'h0;
      prescaleReg <= 16'h0;
      cmdClear <= 1'b0;
    end else begin
      if(cmdClear) begin
        ctrlReg_sta <= 1'b0;
        ctrlReg_sto <= 1'b0;
        ctrlReg_rd <= 1'b0;
        ctrlReg_wr <= 1'b0;
        ctrlReg_ack <= 1'b0;
        cmdClear <= 1'b0;
      end
      ctrlPulse_en <= ctrlReg_en;
      ctrlPulse_ien <= ctrlReg_ien;
      ctrlPulse_sta <= ctrlReg_sta;
      ctrlPulse_sto <= ctrlReg_sto;
      ctrlPulse_rd <= ctrlReg_rd;
      ctrlPulse_wr <= ctrlReg_wr;
      ctrlPulse_ack <= ctrlReg_ack;
      ctrlPulse_reserved <= ctrlReg_reserved;
      rxDataReg <= i2cCore_io_rxData;
      if(apbWrite) begin
        case(apbAddr)
          3'b000 : begin
            ctrlReg_en <= _zz_ctrlReg_en[0];
            ctrlReg_ien <= _zz_ctrlReg_en[1];
            ctrlReg_sta <= _zz_ctrlReg_en[2];
            ctrlReg_sto <= _zz_ctrlReg_en[3];
            ctrlReg_rd <= _zz_ctrlReg_en[4];
            ctrlReg_wr <= _zz_ctrlReg_en[5];
            ctrlReg_ack <= _zz_ctrlReg_en[6];
            ctrlReg_reserved <= _zz_ctrlReg_en[31 : 7];
            cmdClear <= 1'b1;
          end
          3'b010 : begin
            txDataReg <= io_apb_PWDATA[7 : 0];
          end
          3'b011 : begin
            addrReg <= io_apb_PWDATA[6 : 0];
          end
          3'b100 : begin
            prescaleReg <= io_apb_PWDATA[15 : 0];
          end
          default : begin
          end
        endcase
      end
    end
  end


endmodule

module I2cMasterCore (
  input  wire          io_ctrl_en,
  input  wire          io_ctrl_ien,
  input  wire          io_ctrl_sta,
  input  wire          io_ctrl_sto,
  input  wire          io_ctrl_rd,
  input  wire          io_ctrl_wr,
  input  wire          io_ctrl_ack,
  input  wire [24:0]   io_ctrl_reserved,
  output wire          io_status_rxack,
  output wire          io_status_busy,
  output wire          io_status_tip,
  output wire          io_status_ifl,
  output wire [27:0]   io_status_reserved,
  input  wire [7:0]    io_txData,
  output wire [7:0]    io_rxData,
  input  wire [6:0]    io_slaveAddr,
  input  wire [15:0]   io_prescale,
  input  wire          io_i2c_scl_read,
  output wire          io_i2c_scl_write,
  output wire          io_i2c_scl_writeEnable,
  input  wire          io_i2c_sda_read,
  output wire          io_i2c_sda_write,
  output wire          io_i2c_sda_writeEnable,
  input  wire          io_cmdPulse_en,
  input  wire          io_cmdPulse_ien,
  input  wire          io_cmdPulse_sta,
  input  wire          io_cmdPulse_sto,
  input  wire          io_cmdPulse_rd,
  input  wire          io_cmdPulse_wr,
  input  wire          io_cmdPulse_ack,
  input  wire [24:0]   io_cmdPulse_reserved,
  input  wire          io_iflClear,
  input  wire          clk,
  input  wire          reset
);
  localparam I2cState_IDLE = 4'd0;
  localparam I2cState_START_1 = 4'd1;
  localparam I2cState_START_2 = 4'd2;
  localparam I2cState_ADDR = 4'd3;
  localparam I2cState_ADDR_ACK = 4'd4;
  localparam I2cState_DATA_TX = 4'd5;
  localparam I2cState_DATA_RX = 4'd6;
  localparam I2cState_DATA_ACK = 4'd7;
  localparam I2cState_STOP_1 = 4'd8;
  localparam I2cState_STOP_2 = 4'd9;

  wire       [15:0]   _zz_prescaleCnt;
  wire       [2:0]    _zz_sdaEnable;
  wire       [2:0]    _zz_sdaEnable_1;
  reg        [3:0]    state;
  reg                 busy;
  reg                 tip;
  reg                 rxack;
  reg                 ifl;
  reg        [7:0]    shiftReg;
  reg        [7:0]    rxDataReg;
  reg        [3:0]    bitCnt;
  reg        [15:0]   prescaleCnt;
  wire                tick;
  reg        [1:0]    sclPhase;
  reg                 sclEnable;
  reg                 sdaEnable;
  reg                 sclPrev;
  wire                sclRising;
  wire                sclFalling;
  reg                 doStart;
  reg                 doStop;
  reg                 doRead;
  reg                 doWrite;
  reg                 ackVal;
  wire                when_ApbI2cCtrl_l144;
  wire                when_ApbI2cCtrl_l147;
  wire                when_ApbI2cCtrl_l150;
  wire                when_ApbI2cCtrl_l155;
  wire                when_ApbI2cCtrl_l162;
  wire                when_ApbI2cCtrl_l165;
  wire       [7:0]    addrByte;
  wire                when_ApbI2cCtrl_l237;
  wire                when_ApbI2cCtrl_l325;
  wire                when_ApbI2cCtrl_l358;
  wire                when_ApbI2cCtrl_l441;
  `ifndef SYNTHESIS
  reg [63:0] state_string;
  `endif


  assign _zz_prescaleCnt = (prescaleCnt - 16'h0001);
  assign _zz_sdaEnable = bitCnt[2:0];
  assign _zz_sdaEnable_1 = bitCnt[2:0];
  `ifndef SYNTHESIS
  always @(*) begin
    case(state)
      I2cState_IDLE : state_string = "IDLE    ";
      I2cState_START_1 : state_string = "START_1 ";
      I2cState_START_2 : state_string = "START_2 ";
      I2cState_ADDR : state_string = "ADDR    ";
      I2cState_ADDR_ACK : state_string = "ADDR_ACK";
      I2cState_DATA_TX : state_string = "DATA_TX ";
      I2cState_DATA_RX : state_string = "DATA_RX ";
      I2cState_DATA_ACK : state_string = "DATA_ACK";
      I2cState_STOP_1 : state_string = "STOP_1  ";
      I2cState_STOP_2 : state_string = "STOP_2  ";
      default : state_string = "????????";
    endcase
  end
  `endif

  assign tick = (prescaleCnt == 16'h0);
  assign io_i2c_scl_write = 1'b0;
  assign io_i2c_scl_writeEnable = sclEnable;
  assign io_i2c_sda_write = 1'b0;
  assign io_i2c_sda_writeEnable = sdaEnable;
  assign sclRising = (io_i2c_scl_read && (! sclPrev));
  assign sclFalling = ((! io_i2c_scl_read) && sclPrev);
  assign when_ApbI2cCtrl_l144 = ((io_cmdPulse_sta && io_ctrl_en) && (! busy));
  assign when_ApbI2cCtrl_l147 = ((io_cmdPulse_sto && io_ctrl_en) && busy);
  assign when_ApbI2cCtrl_l150 = ((io_cmdPulse_wr && io_ctrl_en) && (! tip));
  assign when_ApbI2cCtrl_l155 = ((io_cmdPulse_rd && io_ctrl_en) && (! tip));
  assign when_ApbI2cCtrl_l162 = ((state == I2cState_START_1) || (state == I2cState_START_2));
  assign when_ApbI2cCtrl_l165 = ((state == I2cState_STOP_1) || (state == I2cState_STOP_2));
  assign addrByte = {io_slaveAddr[6 : 0],(doRead ? 1'b1 : 1'b0)};
  assign when_ApbI2cCtrl_l237 = (bitCnt == 4'b0000);
  assign when_ApbI2cCtrl_l325 = (bitCnt == 4'b0000);
  assign when_ApbI2cCtrl_l358 = (bitCnt == 4'b0000);
  assign when_ApbI2cCtrl_l441 = (! io_ctrl_en);
  assign io_status_rxack = rxack;
  assign io_status_busy = busy;
  assign io_status_tip = tip;
  assign io_status_ifl = ifl;
  assign io_status_reserved = 28'h0;
  assign io_rxData = rxDataReg;
  always @(posedge clk or posedge reset) begin
    if(reset) begin
      state <= I2cState_IDLE;
      busy <= 1'b0;
      tip <= 1'b0;
      rxack <= 1'b0;
      ifl <= 1'b0;
      shiftReg <= 8'h0;
      rxDataReg <= 8'h0;
      bitCnt <= 4'b0000;
      prescaleCnt <= 16'h0;
      sclPhase <= 2'b00;
      sclEnable <= 1'b0;
      sdaEnable <= 1'b0;
      sclPrev <= 1'b1;
      doStart <= 1'b0;
      doStop <= 1'b0;
      doRead <= 1'b0;
      doWrite <= 1'b0;
      ackVal <= 1'b0;
    end else begin
      prescaleCnt <= (tick ? io_prescale : _zz_prescaleCnt);
      sclPrev <= io_i2c_scl_read;
      if(when_ApbI2cCtrl_l144) begin
        doStart <= 1'b1;
      end
      if(when_ApbI2cCtrl_l147) begin
        doStop <= 1'b1;
      end
      if(when_ApbI2cCtrl_l150) begin
        doWrite <= 1'b1;
        shiftReg <= io_txData;
        tip <= 1'b1;
      end
      if(when_ApbI2cCtrl_l155) begin
        doRead <= 1'b1;
        tip <= 1'b1;
        ackVal <= io_ctrl_ack;
      end
      if(when_ApbI2cCtrl_l162) begin
        doStart <= 1'b0;
      end
      if(when_ApbI2cCtrl_l165) begin
        doStop <= 1'b0;
      end
      if(tick) begin
        case(state)
          I2cState_IDLE : begin
            sclEnable <= 1'b0;
            sdaEnable <= 1'b0;
            bitCnt <= 4'b0000;
            sclPhase <= 2'b00;
            if(doStart) begin
              state <= I2cState_START_1;
              sdaEnable <= 1'b1;
              busy <= 1'b1;
            end
          end
          I2cState_START_1 : begin
            sclEnable <= 1'b1;
            state <= I2cState_START_2;
            shiftReg <= {io_slaveAddr[6 : 0],(doRead ? 1'b1 : 1'b0)};
            bitCnt <= 4'b0111;
          end
          I2cState_START_2 : begin
            state <= I2cState_ADDR;
            sclPhase <= 2'b00;
          end
          I2cState_ADDR : begin
            case(sclPhase)
              2'b00 : begin
                sdaEnable <= (! shiftReg[_zz_sdaEnable]);
                sclPhase <= 2'b01;
              end
              2'b01 : begin
                sclEnable <= 1'b0;
                sclPhase <= 2'b10;
              end
              2'b10 : begin
                sclPhase <= 2'b11;
              end
              default : begin
                sclEnable <= 1'b1;
                sclPhase <= 2'b00;
                if(when_ApbI2cCtrl_l237) begin
                  state <= I2cState_ADDR_ACK;
                  sdaEnable <= 1'b0;
                end else begin
                  bitCnt <= (bitCnt - 4'b0001);
                end
              end
            endcase
          end
          I2cState_ADDR_ACK : begin
            case(sclPhase)
              2'b00 : begin
                sdaEnable <= 1'b0;
                sclPhase <= 2'b01;
              end
              2'b01 : begin
                sclEnable <= 1'b0;
                sclPhase <= 2'b10;
              end
              2'b10 : begin
                rxack <= io_i2c_sda_read;
                sclPhase <= 2'b11;
              end
              default : begin
                sclEnable <= 1'b1;
                sclPhase <= 2'b00;
                if(rxack) begin
                  if(doStop) begin
                    state <= I2cState_STOP_1;
                  end else begin
                    state <= I2cState_IDLE;
                    busy <= 1'b0;
                    tip <= 1'b0;
                    ifl <= 1'b1;
                    doWrite <= 1'b0;
                    doRead <= 1'b0;
                  end
                end else begin
                  if(doRead) begin
                    state <= I2cState_DATA_RX;
                    bitCnt <= 4'b0111;
                    sdaEnable <= 1'b0;
                  end else begin
                    if(doWrite) begin
                      state <= I2cState_DATA_TX;
                      bitCnt <= 4'b0111;
                    end else begin
                      state <= I2cState_IDLE;
                      tip <= 1'b0;
                      ifl <= 1'b1;
                    end
                  end
                end
              end
            endcase
          end
          I2cState_DATA_TX : begin
            case(sclPhase)
              2'b00 : begin
                sdaEnable <= (! shiftReg[_zz_sdaEnable_1]);
                sclPhase <= 2'b01;
              end
              2'b01 : begin
                sclEnable <= 1'b0;
                sclPhase <= 2'b10;
              end
              2'b10 : begin
                sclPhase <= 2'b11;
              end
              default : begin
                sclEnable <= 1'b1;
                sclPhase <= 2'b00;
                if(when_ApbI2cCtrl_l325) begin
                  state <= I2cState_DATA_ACK;
                  sdaEnable <= 1'b0;
                end else begin
                  bitCnt <= (bitCnt - 4'b0001);
                end
              end
            endcase
          end
          I2cState_DATA_RX : begin
            case(sclPhase)
              2'b00 : begin
                sclEnable <= 1'b1;
                sclPhase <= 2'b01;
              end
              2'b01 : begin
                sclEnable <= 1'b0;
                shiftReg <= {shiftReg[6 : 0],io_i2c_sda_read};
                sclPhase <= 2'b10;
              end
              2'b10 : begin
                sclPhase <= 2'b11;
              end
              default : begin
                sclEnable <= 1'b1;
                sclPhase <= 2'b00;
                if(when_ApbI2cCtrl_l358) begin
                  rxDataReg <= shiftReg;
                  state <= I2cState_DATA_ACK;
                end else begin
                  bitCnt <= (bitCnt - 4'b0001);
                end
              end
            endcase
          end
          I2cState_DATA_ACK : begin
            case(sclPhase)
              2'b00 : begin
                sdaEnable <= (doRead && (! ackVal));
                sclPhase <= 2'b01;
              end
              2'b01 : begin
                sclEnable <= 1'b0;
                sclPhase <= 2'b10;
              end
              2'b10 : begin
                if(doWrite) begin
                  rxack <= io_i2c_sda_read;
                end
                sclEnable <= 1'b1;
                sclPhase <= 2'b00;
                sdaEnable <= 1'b0;
                if(doStop) begin
                  state <= I2cState_STOP_1;
                end else begin
                  state <= I2cState_IDLE;
                  busy <= 1'b0;
                  tip <= 1'b0;
                  ifl <= 1'b1;
                  doWrite <= 1'b0;
                  doRead <= 1'b0;
                end
              end
              default : begin
              end
            endcase
          end
          I2cState_STOP_1 : begin
            case(sclPhase)
              2'b00 : begin
                sdaEnable <= 1'b1;
                sclPhase <= 2'b01;
              end
              2'b01 : begin
                sclEnable <= 1'b0;
                sclPhase <= 2'b10;
              end
              2'b10 : begin
                sdaEnable <= 1'b0;
                state <= I2cState_STOP_2;
              end
              default : begin
              end
            endcase
          end
          default : begin
            state <= I2cState_IDLE;
            busy <= 1'b0;
            tip <= 1'b0;
            ifl <= 1'b1;
            doWrite <= 1'b0;
            doRead <= 1'b0;
          end
        endcase
      end
      if(when_ApbI2cCtrl_l441) begin
        state <= I2cState_IDLE;
        busy <= 1'b0;
        tip <= 1'b0;
        sclEnable <= 1'b0;
        sdaEnable <= 1'b0;
        doStart <= 1'b0;
        doStop <= 1'b0;
        doWrite <= 1'b0;
        doRead <= 1'b0;
      end
      if(io_iflClear) begin
        ifl <= 1'b0;
      end
    end
  end


endmodule
