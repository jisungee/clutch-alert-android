package com.clutchalarm.app.domain.model

object KboTeams {
    val all = listOf(
        Team("LG", "LG 트윈스"), Team("DOOSAN", "두산 베어스"), Team("KIA", "KIA 타이거즈"),
        Team("SAMSUNG", "삼성 라이온즈"), Team("SSG", "SSG 랜더스"), Team("LOTTE", "롯데 자이언츠"),
        Team("HANWHA", "한화 이글스"), Team("NC", "NC 다이노스"), Team("KT", "KT 위즈"), Team("KIWOOM", "키움 히어로즈"),
    )
    fun find(id: String?) = all.firstOrNull { it.id == id }
}
