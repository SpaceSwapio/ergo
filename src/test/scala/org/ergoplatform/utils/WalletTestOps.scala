package org.ergoplatform.utils

import org.ergoplatform.ErgoBox.{NonMandatoryRegisterId, R4, TokenId}
import org.ergoplatform.modifiers.ErgoFullBlock
import org.ergoplatform.modifiers.mempool.ErgoTransaction
import org.ergoplatform.nodeView.state.{ErgoState, StateType, UtxoState}
import org.ergoplatform.nodeView.wallet.{BalancesSnapshot, ErgoAddress, ErgoWallet}
import org.ergoplatform.settings.ErgoSettings
import org.ergoplatform.{ErgoBox, ErgoBoxCandidate, Input}
import scorex.crypto.hash.Digest32
import scorex.util.{ModifierId, bytesToId}
import sigmastate.Values.{EvaluatedValue, LongConstant, TrueLeaf, Value}
import sigmastate.interpreter.{ContextExtension, ProverResult}
import sigmastate.{SBoolean, SLong}

import scala.concurrent.Await
import scala.concurrent.duration._

trait WalletTestOps extends NodeViewBaseOps {

  def emptyProverResult: ProverResult = ProverResult(Array.emptyByteArray, ContextExtension.empty)
  def newAssetIdStub: TokenId = Digest32 @@ Array.emptyByteArray

  override def initSettings: ErgoSettings = {
    val settings = NodeViewTestConfig(StateType.Utxo, verifyTransactions = true, popowBootstrap = false).toSettings
    settings.copy(walletSettings = settings.walletSettings.copy(scanningInterval = 15.millis))
  }

  def withFixture[T](test: WalletFixture => T): T = {
    new WalletFixture(settings, getCurrentView(_).vault).apply(test)
  }

  def wallet(implicit w: WalletFixture): ErgoWallet = w.wallet

  def getTrackedAddresses(implicit w: WalletFixture): Seq[ErgoAddress] =
    Await.result(w.wallet.trackedAddresses(), awaitDuration)

  def getConfirmedBalances(implicit w: WalletFixture): BalancesSnapshot =
    Await.result(w.wallet.confirmedBalances(), awaitDuration)

  def getBalancesWithUnconfirmed(implicit w: WalletFixture): BalancesSnapshot =
    Await.result(w.wallet.balancesWithUnconfirmed(), awaitDuration)

  def scanningInterval(implicit ctx: Ctx): Long = ctx.settings.walletSettings.scanningInterval.toMillis
  def scanTime(block: ErgoFullBlock)(implicit ctx: Ctx): Long = scanTime(block.transactions.flatMap(_.outputs).size)
  def scanTime(boxCount: Int)(implicit ctx: Ctx): Long = boxCount * scanningInterval + 1000
  def offchainScanTime(tx: ErgoTransaction): Long = tx.outputs.size * 100 + 300

  def sum(boxes: Seq[ErgoBox]): Long = boxes.map(_.value).sum

  def assetSum(boxes: Seq[ErgoBox]): Map[ModifierId, Long] = {
    boxes
      .map(_.additionalTokens)
      .map { _.map { case (tokenId, value) => bytesToId(tokenId) -> value }.toMap }
      .reduce(_ ++ _)
  }

  def boxesAvailable(block: ErgoFullBlock, script: Value[SBoolean.type]): Seq[ErgoBox] = {
    block.transactions.flatMap(boxesAvailable(_, script))
  }

  def boxesAvailable(tx: ErgoTransaction, script: Value[SBoolean.type]): Seq[ErgoBox] = {
    tx.outputs.filter(_.proposition == script)
  }

  def boxAssets(boxes: Seq[ErgoBoxCandidate]): Map[ModifierId, Long] = {
    boxes
      .flatMap { _.additionalTokens }
      .groupBy { case (digest, _) => bytesToId(digest) }
      .map { case (id, pairs) => id -> pairs.map(_._2).sum }
  }

  def getUtxoState(implicit ctx: Ctx): UtxoState = getCurrentState.asInstanceOf[UtxoState]

  def getHeightOf(state: ErgoState[_])(implicit ctx: Ctx): Option[Int] = {
    getHistory.heightOf(scorex.core.versionToId(state.version))
  }

  def makeGenesisBlock(script: Value[SBoolean.type])(implicit ctx: Ctx): ErgoFullBlock = {
    makeNextBlock(getUtxoState, Seq(makeGenesisTx(script)))
  }

  def makeGenesisTx(script: Value[SBoolean.type], assets: Seq[(TokenId, Long)] = Seq.empty): ErgoTransaction = {
    //ErgoMiner.createCoinbase(Some(genesisEmissionBox), 0, Seq.empty, script, emission)
    val emissionBox = genesisEmissionBox
    val height = 0
    val emissionAmount = emission.emissionAtHeight(height)
    val newEmissionAmount = emissionBox.value - emissionAmount
    val emissionRegs = Map[NonMandatoryRegisterId, EvaluatedValue[SLong.type]](R4 -> LongConstant(height))
    val inputs = IndexedSeq(new Input(emissionBox.id, ProverResult(Array.emptyByteArray, ContextExtension.empty)))
    val newEmissionBox = new ErgoBoxCandidate(newEmissionAmount, emissionBox.proposition, Seq.empty, emissionRegs)
    val minerBox = new ErgoBoxCandidate(emissionAmount, script, replaceNewAssetStub(assets, inputs), Map.empty)
    ErgoTransaction(inputs, IndexedSeq(newEmissionBox, minerBox))
  }

  def makeSpendingTx(boxesToSpend: Seq[ErgoBox],
                     addressToSpend: ErgoAddress,
                     balanceToReturn: Long = 0,
                     assets: Seq[(TokenId, Long)] = Seq.empty): ErgoTransaction = {
    val proof = ProverResult(addressToSpend.contentBytes, ContextExtension.empty)
    makeTx(boxesToSpend, proof, balanceToReturn, addressToSpend.script, assets)
  }

  def makeTx(boxesToSpend: Seq[ErgoBox],
             proofToSpend: ProverResult,
             balanceToReturn: Long,
             scriptToReturn: Value[SBoolean.type],
             assets: Seq[(TokenId, Long)] = Seq.empty): ErgoTransaction = {
    val inputs = boxesToSpend.map(box => Input(box.id, proofToSpend))
    val balanceToSpend = boxesToSpend.map(_.value).sum - balanceToReturn
    def creatingCandidate = new ErgoBoxCandidate(balanceToReturn, scriptToReturn, replaceNewAssetStub(assets, inputs))
    val spendingOutput = if (balanceToSpend > 0) Some(new ErgoBoxCandidate(balanceToSpend, TrueLeaf)) else None
    val creatingOutput = if (balanceToReturn > 0) Some(creatingCandidate) else None
    ErgoTransaction(inputs.toIndexedSeq, spendingOutput.toIndexedSeq ++ creatingOutput.toIndexedSeq)
  }

  private def replaceNewAssetStub(assets: Seq[(TokenId, Long)], inputs: Seq[Input]): Seq[(TokenId, Long)] = {
    val (createdAsset, spentAssets) = assets.partition(_._1 sameElements newAssetIdStub)
    createdAsset.map(Digest32 @@ inputs.head.boxId -> _._2) ++ spentAssets
  }

  def randomAssets: Seq[(TokenId, Long)] = Seq(newAssetIdStub -> assetGen.sample.value._2)
}
