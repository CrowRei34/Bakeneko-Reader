package io.github.landwarderer.futon.desktop.db

import kotlin.Long
import kotlin.String

public data class Manga(
  public val id: Long,
  public val url: String,
  public val title: String,
  public val source: String,
  public val coverUrl: String?,
  public val description: String?,
)
