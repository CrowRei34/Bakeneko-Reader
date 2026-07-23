package io.github.landwarderer.futon.desktop.db

import kotlin.Long
import kotlin.String

public data class GetHistoryList(
  public val id: Long,
  public val url: String,
  public val title: String,
  public val source: String,
  public val coverUrl: String?,
  public val description: String?,
  public val chapterIndex: Long,
  public val pageIndex: Long,
  public val updatedAt: Long,
)
