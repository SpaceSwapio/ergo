package org.ergoplatform.nodeView.history.storage.modifierprocessors.adproofs

import org.ergoplatform.modifiers.ErgoPersistentModifier
import org.ergoplatform.modifiers.history.ADProof
import scorex.core.consensus.History.ProgressInfo

import scala.util.Try

trait ADProofsProcessor {

  /**
    * Root hash only is kept in state
    */
  protected val adState: Boolean

  /**
    * @param m - modifier to process
    * @return ProgressInfo - info required for State to be consistent with History
    */
  protected def process(m: ADProof): ProgressInfo[ErgoPersistentModifier]

  /**
    * @param m - ADProof to validate
    * @return Success() if ADProof is valid from History point of view, Failure(error) otherwise
    */
  protected def validate(m: ADProof): Try[Unit]
}

