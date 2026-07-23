package io.github.landwarderer.futon.desktop.db

import kotlin.Long

public data class History(
  public val mangaId: Long,
  public val chapterIndex: Long,
  public val pageIndex: Long,
  public val updatedAt: Long,
)
