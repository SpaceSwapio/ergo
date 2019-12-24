package org.ergoplatform.modifiers.history.popow

import org.ergoplatform.modifiers.ErgoPersistentModifier
import org.ergoplatform.modifiers.history.Header
import scorex.core.ModifierTypeId
import scorex.core.serialization.ScorexSerializer
import scorex.util.ModifierId
import scorex.util.serialization.{Reader, Writer}
import scorex.util.Extensions._

/**
  * A structure representing NiPoPow proof as a persistent modifier.
  */
final case class PoPowProof(prefix: PoPowProofPrefix, suffix: PoPowProofSuffix)
  extends ErgoPersistentModifier {

  override type M = PoPowProof

  override val modifierTypeId: ModifierTypeId = PoPowProof.modifierTypeId

  override lazy val id: ModifierId = prefix.id

  override val sizeOpt: Option[Int] = None

  override def serializedId: Array[Byte] = prefix.serializedId

  override def serializer: ScorexSerializer[M] = PoPowProofSerializer

  override def parentId: ModifierId = prefix.parentId

  def chain: Seq[PoPowHeader] = prefix.chain ++ suffix.chain

  def headersChain: Seq[Header] = chain.map(_.header)

}

object PoPowProof {

  val modifierTypeId: ModifierTypeId = ModifierTypeId @@ (110: Byte)

  def apply(m: Int, k: Int, prefixChain: Seq[PoPowHeader], suffixChain: Seq[PoPowHeader]): PoPowProof = {
    val suffix = PoPowProofSuffix(k, suffixChain)
    val prefix = PoPowProofPrefix(m, prefixChain, suffix.id)
    new PoPowProof(prefix, suffix)
  }

}

object PoPowProofSerializer extends ScorexSerializer[PoPowProof] {

  override def serialize(obj: PoPowProof, w: Writer): Unit = {
    val prefixBytes = obj.prefix.bytes
    val suffixBytes = obj.suffix.bytes
    w.putUInt(prefixBytes.length)
    w.putBytes(prefixBytes)
    w.putUInt(suffixBytes.length)
    w.putBytes(suffixBytes)
  }

  override def parse(r: Reader): PoPowProof = {
    val prefixSize = r.getUInt().toIntExact
    val prefix = PoPowProofPrefixSerializer.parseBytes(r.getBytes(prefixSize))
    val suffixSize = r.getUInt().toIntExact
    val suffix = PoPowProofSuffixSerializer.parseBytes(r.getBytes(suffixSize))
    PoPowProof(prefix, suffix)
  }

}