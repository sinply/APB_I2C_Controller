name := "APB_I2C_Controller"
version := "0.1.0"
scalaVersion := "2.11.12"

val spinalVersion = "1.14.1"

libraryDependencies += "com.github.spinalhdl" %% "spinalhdl-core" % spinalVersion
libraryDependencies += "com.github.spinalhdl" %% "spinalhdl-lib" % spinalVersion
libraryDependencies += "com.github.spinalhdl" %% "spinalhdl-sim" % spinalVersion
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % Test

addCompilerPlugin("com.github.spinalhdl" % "spinalhdl-idsl-plugin_2.11" % spinalVersion)

fork := true
