package vexriscv.demo

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi.Axi4ReadOnly
import spinal.lib.com.jtag.Jtag
import spinal.lib.eda.altera.{InterruptReceiverTag, QSysify, ResetEmitterTag}
import vexriscv.ip.{DataCacheConfig, InstructionCacheConfig}
import vexriscv.plugin.CsrAccess.WRITE_ONLY
import vexriscv.plugin._
import vexriscv.{VexRiscv, VexRiscvConfig, plugin}



object Indy{
  def main(args: Array[String]) {
    val report = SpinalVerilog{

      //CPU configuration
      val cpuConfig = VexRiscvConfig(
        plugins = List(
          new PcManagerSimplePlugin(0x00000000l, false),
//          new IBusSimplePlugin(
//            interfaceKeepData = false,
//            catchAccessFault = false
//          ),
//          new DBusSimplePlugin(
//            catchAddressMisaligned = false,
//            catchAccessFault = false
//          ),
          new IBusCachedPlugin(
            resetVector = null,
            prediction = STATIC,
            config = InstructionCacheConfig(
              cacheSize = 4096,
              bytePerLine =32,
              wayCount = 1,
              addressWidth = 32,
              cpuDataWidth = 32,
              memDataWidth = 32,
              catchIllegalAccess = true,
              catchAccessFault = true,
              catchMemoryTranslationMiss = true,
              asyncTagMemory = false,
              twoCycleRam = true,
              twoCycleCache = true
            )
            //            askMemoryTranslation = true,
            //            memoryTranslatorPortConfig = MemoryTranslatorPortConfig(
            //              portTlbSize = 4
            //            )
          ),
          new DBusCachedPlugin(
            config = new DataCacheConfig(
              cacheSize         = 4096,
              bytePerLine       = 32,
              wayCount          = 1,
              addressWidth      = 32,
              cpuDataWidth      = 32,
              memDataWidth      = 32,
              catchAccessError  = true,
              catchIllegal      = true,
              catchUnaligned    = true,
              catchMemoryTranslationMiss = true
            ),
            memoryTranslatorPortConfig = null
            //            memoryTranslatorPortConfig = MemoryTranslatorPortConfig(
            //              portTlbSize = 6
            //            )
          ),
          new StaticMemoryTranslatorPlugin(
            ioRange      = _(31 downto 28) === 0xF
          ),
          new DecoderSimplePlugin(
            catchIllegalInstruction = true
          ),
          new RegFilePlugin(
            regFileReadyKind = plugin.SYNC,
            zeroBoot = false
          ),
          new IntAluPlugin,
          new SrcPlugin(
            separatedAddSub = false,
            executeInsertion = true
          ),
          new FullBarrelShifterPlugin,
          new MulPlugin,
          new DivPlugin,
          new HazardSimplePlugin(
            bypassExecute           = true,
            bypassMemory            = true,
            bypassWriteBack         = true,
            bypassWriteBackBuffer   = true,
            pessimisticUseSrc       = false,
            pessimisticWriteRegFile = false,
            pessimisticAddressMatch = false
          ),
          new DebugPlugin(ClockDomain.current.clone(reset = Bool().setName("debugReset"))),
          new BranchPlugin(
            earlyBranch = false,
            catchAddressMisaligned = true
          ),
          new ExternalInterruptArrayPlugin(
            maskCsrId = 0xBC0,
            pendingsCsrId = 0xFC0
          ),
          new CsrPlugin(
            config = CsrPluginConfig.small(mtvecInit = null).copy(mtvecAccess = WRITE_ONLY)
          ),
//          new CsrPlugin(
//            config = CsrPluginConfig(
//              catchIllegalAccess = false,
//              mvendorid      = null,
//              marchid        = null,
//              mimpid         = null,
//              mhartid        = null,
//              misaExtensionsInit = 66,
//              misaAccess     = CsrAccess.NONE,
//              mtvecAccess    = CsrAccess.NONE,
//              mtvecInit      = 0x00000020l,
//              mepcAccess     = CsrAccess.READ_WRITE,
//              mscratchGen    = false,
//              mcauseAccess   = CsrAccess.READ_ONLY,
//              mbadaddrAccess = CsrAccess.READ_ONLY,
//              mcycleAccess   = CsrAccess.NONE,
//              minstretAccess = CsrAccess.NONE,
//              ecallGen       = false,
//              wfiGenAsWait         = false,
//              ucycleAccess   = CsrAccess.NONE
//            )
//         ),
          new YamlPlugin("cpu0.yaml")
        )
      )

      //CPU instanciation
      val cpu = new VexRiscv(cpuConfig)

      //CPU modifications to be an Wishbone one
      cpu.setDefinitionName("Indy")
      cpu.rework {
      
        for (plugin <- cpuConfig.plugins) plugin match {
          case plugin: IBusSimplePlugin => {
            plugin.iBus.setAsDirectionLess() //Unset IO properties of iBus
            master(plugin.iBus.toWishbone()).setName("iBusWishbone")
              .addTag(ClockDomainTag(ClockDomain.current)) //Specify a clock domain to the iBus (used by QSysify)
          }
          case plugin: IBusCachedPlugin => {
            plugin.iBus.setAsDirectionLess() //Unset IO properties of iBus
            master(plugin.iBus.toWishbone()).setName("iBusWishbone")
              .addTag(ClockDomainTag(ClockDomain.current)) //Specify a clock domain to the iBus (used by QSysify)
          }
          case plugin: DBusSimplePlugin => {
            plugin.dBus.setAsDirectionLess()
            master(plugin.dBus.toWishbone())
              .setName("dBusWishbone")
              .addTag(ClockDomainTag(ClockDomain.current))
          }
          case plugin: DBusCachedPlugin => {
            plugin.dBus.setAsDirectionLess()
            master(plugin.dBus.toWishbone())
              .setName("dBusWishbone")
              .addTag(ClockDomainTag(ClockDomain.current))
          }
          case plugin: DebugPlugin => plugin.debugClockDomain {
            plugin.io.bus.setAsDirectionLess()
            val jtag = slave(new Jtag())
              .setName("jtag")
            jtag <> plugin.io.bus.fromJtag()
            plugin.io.resetOut
              .addTag(ResetEmitterTag(plugin.debugClockDomain))
              .parent = null //Avoid the io bundle to be interpreted as a QSys conduit
          }
          case _ =>
        }
      }
      cpu
    }
  }
}
