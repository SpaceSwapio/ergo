package org.ergoplatform.utils

import java.math.BigInteger

import org.bouncycastle.util.BigIntegers
import org.ergoplatform.mining.AutolykosSolution
import org.ergoplatform.modifiers.history.Header
import org.ergoplatform.{ErgoBox, Input, UnsignedInput}
import org.ergoplatform.modifiers.mempool.{ErgoBoxSerializer, ErgoTransaction, UnsignedErgoTransaction}
import org.ergoplatform.nodeView.ErgoContext
import org.ergoplatform.nodeView.state.{ErgoStateContext, VotingData}
import org.ergoplatform.settings.{ErgoValidationSettings, LaunchParameters, VotingSettings}
import org.ergoplatform.wallet.interpreter.{ErgoInterpreter, ErgoProvingInterpreter}
import org.ergoplatform.wallet.protocol.context.{ErgoLikeParameters, TransactionContext}
import scorex.crypto.authds.ADDigest
import scorex.crypto.hash.{Blake2b256, Digest32}
import sigmastate.interpreter.{ContextExtension, HintsBag, OtherCommitment}
import sigmastate.eval.{IRContext, RuntimeIRContext}
import sigmastate.lang.Terms._
import sigmastate.interpreter._
import scorex.util.bytesToId
import scorex.util.encode.Base16
import sigmastate.basics.DLogProtocol.{DLogInteractiveProver, DLogProverInput, FirstDLogProverMessage, ProveDlog}
import sigmastate.serialization.{GroupElementSerializer, SigmaSerializer}


class OldProvingInterpreter(seed: String,
                            params: ErgoLikeParameters,
                            hintsBag: HintsBag)
                           (implicit IR: IRContext) extends
  ErgoProvingInterpreter(IndexedSeq(), params: ErgoLikeParameters, hintsBag: HintsBag){

  val numOfSecrets = 1
  def secretsFromSeed(seedStr: String): IndexedSeq[BigInteger] = {
    (1 to numOfSecrets).map { i =>
      BigIntegers.fromUnsignedByteArray(Blake2b256.hash(i + seedStr))
    }
  }

  override lazy val secrets = secretsFromSeed(seed).map(DLogProverInput.apply)
}


// Script for 2-out-of 3 spending of foundation box
object FoundationBoxSigner extends App {
  implicit lazy val ircontext: IRContext = new RuntimeIRContext

  type ACTION = Int
  val generateCommitment: ACTION = 0
  val preSign: ACTION = 1
  val sign: ACTION = 2

  val height = 1

  //data which should be MANUALLY changed in order to interact with the program
  val seed = "..."
  val action: ACTION = generateCommitment
  val myIndex = 1 //0, 1, 2
  val cosignerIndex = 0
  // hints provided by a cosigner
  val commitmentStringOpt: Option[String] = None
  val ownRandomnessStringOpt: Option[String] = None
  val partialSignarureStringOpt: Option[String] = None


  val cmtOpt = commitmentStringOpt.map(Base16.decode).map(_.get).map(SigmaSerializer.startReader(_))
                  .map(GroupElementSerializer.parse).map(FirstDLogProverMessage.apply)
  val ownRandomnessOpt = ownRandomnessStringOpt.map(new BigInteger(_))
  val partialSingatureOpt = partialSignarureStringOpt.map(Base16.decode).map(_.get)

  val inactiveIndex = (0 to 2).filter(i => i != myIndex && i != cosignerIndex).head
  val p = new OldProvingInterpreter(seed, LaunchParameters, HintsBag.empty)
  implicit val verifier = new ErgoInterpreter(LaunchParameters)

  val pubKeys = IndexedSeq(
    "039bb5fe52359a64c99a60fd944fc5e388cbdc4d37ff091cc841c3ee79060b8647",
    "031fb52cf6e805f80d97cde289f4f757d49accf0c83fb864b27d2cf982c37f9a8b",
    "0352ac2a471339b0d23b3d2c5ce0db0e81c969f77891b9edf0bda7fd39a78184e7"
  ).map(Base16.decode).map(_.get).map(SigmaSerializer.startReader(_))
    .map(GroupElementSerializer.parse)
      .map(ProveDlog.apply)

  val inputIndex = 0

  //forming a context. Only height matters for the foundation box.
  implicit val vs = VotingSettings(64, 32, 128)
  val parameters = LaunchParameters
  val genesisStateDigest = ADDigest @@ Array.fill(33)(0: Byte)
  val sol = AutolykosSolution(
    CryptoConstants.dlogGroup.generator,
    CryptoConstants.dlogGroup.generator,
    Array.fill(8)(0:Byte),
    0)
  val h = Header(1.toByte, bytesToId(Array.fill(32)(0: Byte)), Digest32 @@ Array.fill(32)(0: Byte),
    ADDigest @@ Array.fill(33)(0: Byte), Digest32 @@ Array.fill(32)(0: Byte), 0L, 0L, height,
    Digest32 @@ Array.fill(32)(0: Byte), sol, Array.fill(3)(0: Byte))
  val stateContext = new ErgoStateContext(Seq(h), None, genesisStateDigest, parameters, ErgoValidationSettings.initial, VotingData.empty)


  //box and message to sign
  val gfBytes = Base16.decode("80d6d0c7cfdad807100e040004c094400580809cde91e7b0010580acc7f03704be944004808948058080c7b7e4992c0580b4c4c32104fe884804c0fd4f0580bcc1960b04befd4f05000400ea03d192c1b2a5730000958fa373019a73029c73037e997304a305958fa373059a73069c73077e997308a305958fa373099c730a7e99730ba305730cd193c2a7c2b2a5730d00d50408000000010e6f98040483030808cd039bb5fe52359a64c99a60fd944fc5e388cbdc4d37ff091cc841c3ee79060b864708cd031fb52cf6e805f80d97cde289f4f757d49accf0c83fb864b27d2cf982c37f9a8b08cd0352ac2a471339b0d23b3d2c5ce0db0e81c969f77891b9edf0bda7fd39a78184e7000000000000000000000000000000000000000000000000000000000000000000").get
  val gfBox = ErgoBoxSerializer.parseBytes(gfBytes)

  val boxToSpend: ErgoBox = gfBox
  val input = new UnsignedInput(boxToSpend.id, ContextExtension.empty)

  ////
  val undersignedTx = UnsignedErgoTransaction(IndexedSeq(input), IndexedSeq(boxToSpend.toCandidate))
  val transactionContext = TransactionContext(IndexedSeq(boxToSpend), IndexedSeq(), undersignedTx, selfIndex = 0)
  val msgToSign = undersignedTx.messageToSign

  val context = new ErgoContext(stateContext, transactionContext, ContextExtension.empty, parameters.maxBlockCost, 0)
  val prop = gfBox.proposition.asSigmaProp

  // doing a requested action
  action match {
    case i: Int if i == generateCommitment =>
      val (r, c) = DLogInteractiveProver.firstMessage(pubKeys(myIndex))
      println("randomness(store it in secret!): " + r)
      println("commitment: " + Base16.encode(GroupElementSerializer.toBytes(c.ecData)))

    case i: Int if i == preSign =>
      val cosignerPubKey = pubKeys(cosignerIndex)
      val hint = OtherCommitment(cosignerPubKey, cmtOpt.get)
      val bag = HintsBag(IndexedSeq(hint))
      val partialProof = p.prove(prop, context, msgToSign, bag).get
      println("Partial proof: " + Base16.encode(partialProof.proof))

    case i: Int if i == sign =>
      val ownRandomness = ownRandomnessOpt.get
      val partialSig = partialSingatureOpt.get

      val bag = p.bagForMultisig(context, prop, partialSig, Seq(pubKeys(cosignerIndex), pubKeys(inactiveIndex)))
        .addHint(OwnCommitment(pubKeys(myIndex), ownRandomness, cmtOpt.get))

      val proof = p.prove(prop, context, msgToSign, bag).get

      val check = verifier.verify(prop, context, proof, msgToSign)
      println("proof is correct: " + check)

      val input = Input(boxToSpend.id, proof)
      val tx = ErgoTransaction(IndexedSeq(input), IndexedSeq(boxToSpend.toCandidate))

      println("tx is valid: " +tx.validateStateful(IndexedSeq(boxToSpend), IndexedSeq(), stateContext, 0).result.isValid)
      println(tx)
  }
}