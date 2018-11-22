package org.ergoplatform.nodeView.history.modifierprocessors

import org.ergoplatform.modifiers.ErgoPersistentModifier
import org.ergoplatform.modifiers.history.Header
import org.ergoplatform.modifiers.state.{UtxoSnapshot, UtxoSnapshotChunk, UtxoSnapshotManifest}
import org.ergoplatform.nodeView.history.storage.HistoryStorage
import scorex.core.consensus.History.ProgressInfo
import scorex.core.utils.ScorexEncoding
import scorex.util.ScorexLogging

import scala.util.{Failure, Try}

trait UtxoSnapshotManifestProcessor extends ScorexLogging with ScorexEncoding {

  protected val historyStorage: HistoryStorage

  def process(m: UtxoSnapshotManifest): ProgressInfo[ErgoPersistentModifier] = {
    val chunksToRequest = m.chunkRoots.map(UtxoSnapshot.rootDigestToId).map(UtxoSnapshotChunk.modifierTypeId -> _)
    historyStorage.insertObjects(Seq(m))
    ProgressInfo(None, Seq.empty, Seq.empty, chunksToRequest)
  }

  def validate(m: UtxoSnapshotManifest): Try[Unit] = historyStorage.modifierById(m.blockId) match {
    case Some(h: Header) => m.validate(h)
    case _ => Failure(new Exception("Header manifest relates to is not found in history"))
  }

}
