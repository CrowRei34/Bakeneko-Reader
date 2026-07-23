package io.github.landwarderer.futon.desktop.db

import kotlin.Double
import kotlin.Long
import kotlin.String

public data class Chapter(
  public val id: Long,
  public val mangaId: Long,
  public val url: String,
  public val name: String,
  public val number: Double,
  public val date: Long?,
)
