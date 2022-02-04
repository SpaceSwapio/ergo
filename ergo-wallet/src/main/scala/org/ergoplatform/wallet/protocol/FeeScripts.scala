package org.ergoplatform.wallet.protocol

import org.ergoplatform.{ErgoAddressEncoder, ErgoBox, ErgoScriptPredef}
import sigmastate.Values.ErgoTree

object FeeScripts extends App {
  def testnetAddr() = {
    val testnetScript = ErgoScriptPredef.feeProposition(72)
    ErgoAddressEncoder.apply(ErgoAddressEncoder.TestnetNetworkPrefix).fromProposition(testnetScript).get
  }

  def mainnetAddr(tree: ErgoTree) = {
    ErgoAddressEncoder.apply(ErgoAddressEncoder.MainnetNetworkPrefix).fromProposition(tree).get
  }

  val scriptVersion0 = 0: Byte
  val scriptVersion1 = 1: Byte

  val headerV0 = ErgoTree.headerWithVersion(scriptVersion0)
  val headerV1 = ErgoTree.headerWithVersion(scriptVersion1)

  val initialFeeScript = ErgoScriptPredef.feeProposition()

  val trueFeeScriptV0 = ErgoScriptPredef.TrueProp(headerV0)
  val trueFeeScriptV1 = ErgoScriptPredef.TrueProp(headerV1)

  val compilerV0 = ErgoAddressEncoder.apply(ErgoAddressEncoder.MainnetNetworkPrefix)
    .fromString("4MQyML64GnzMxZgm").get.script

  println("Initial fee proposition: " + mainnetAddr(initialFeeScript))
  println("Simplest true script(v0): " + mainnetAddr(trueFeeScriptV0))
  println("Simplest true script(v1): " + mainnetAddr(trueFeeScriptV1))
  println("Compiler-produced script(v0): 4MQyML64GnzMxZgm")

  println("Testnet script: " + testnetAddr())

  val initialFeeScriptBytes = initialFeeScript.bytes
  val trueFeeScriptV0Bytes = trueFeeScriptV0.bytes
  val trueFeeScriptV1Bytes = trueFeeScriptV1.bytes
  val compilerV0Bytes = compilerV0.bytes

  def isFeeBox(b: ErgoBox) = ???
}