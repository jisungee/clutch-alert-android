# 구단·서비스 이미지 적용 완료

2026-11-24: 제공 리소스를 모두 적용했다. 추가로 필요한 파일은 없다.

- 원본: `design-assets/teams/`의 SVG 10개, `design-assets/branding/`의 PNG 3개.
- 런타임: `drawable/team_logo_{doosan,hanwha,kia,kiwoom,kt,lg,lotte,nc,samsung,ssg}.xml`.
- 공통 표시: `TeamIdentity.kt`의 `TeamLogoResources`·`TeamCrest`. 무착색, 비율 유지, 흰색 받침.
- 서비스 로고: `app_logo.png`, 1536×1024 RGBA, 로그인·회원가입.
- 시작 이미지: `splash_logo.png`, 2172×724 RGBA, 3:1 비율로 네이티브·세션 확인 화면.
- 설치 아이콘: `app_icon.png`, 1254×1254 RGB, 해상도별 mipmap 및 적응형 아이콘. 원본에는 알파 채널이 없다.
- PNG 런타임 복사본은 원본 바이트와 동일하다. 설치 아이콘 크기별 파일만 파생했다.

변환 재현은 `python tools/prepare_assets.py`(JDK 17·Pillow·로컬 Gradle 캐시 필요). `tools/ConvertSvg.java`가 Android Gradle의 SVG 변환기·벡터 미리보기를 사용한다. 앱 런타임 의존성을 추가하지 않는다.

원본 SVG(librsvg)와 VectorDrawable(VdPreview)을 1024px로 비교해 모두 벡터로 적용했다. PNG 대체가 필요한 손상은 관찰하지 않았다. 롯데 stroke scaling approximate 경고는 남아 있어 원본 교체 시 재검토한다. 평균 채널 차이 255 기준 0.050~0.975에는 렌더러 안티앨리어싱 차이가 포함되며 픽셀 일치를 의미하지 않는다.

[전체 적용·빌드·실기기 검증](team-assets-report.md) · [원본 해시](asset-source-hashes.json) · [색상·대비](team-palette.json)
