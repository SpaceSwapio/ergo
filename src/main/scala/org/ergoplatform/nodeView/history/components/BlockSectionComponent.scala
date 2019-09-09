package org.ergoplatform.nodeView.history.components

import org.ergoplatform.modifiers.{BlockSection, ErgoPersistentModifier}
import scorex.core.consensus.History.ProgressInfo
import scorex.core.utils.ScorexEncoding

import scala.util.Try

/**
  * Trait that declares interfaces for validation and processing of various
  * block sections: BlockTransactions, ADProofs, etc.
  */
trait BlockSectionComponent extends ScorexEncoding {
  self: Configuration =>

  /**
    * Whether state requires to download adProofs before full block application
    */
  protected def requireProofs: Boolean = settings.nodeSettings.stateType.requireProofs

  /**
    * @param m - modifier to process
    * @return ProgressInfo - info required for State to be consistent with History
    */
  protected def process(m: BlockSection): ProgressInfo[ErgoPersistentModifier]

  /**
    * @param m - modifier to validate
    * @return Success() if modifier is valid from History point of view, Failure(error) otherwise
    */
  protected def validate(m: BlockSection): Try[Unit]

}
