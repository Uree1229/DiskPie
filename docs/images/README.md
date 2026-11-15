# README 화면 이미지

DiskPie v0.4.0의 실제 Compose 화면에 합성 예시 데이터를 넣어 만든 문서용 미리 보기입니다. 실제 사용자 기기의 캡처나 스캔 결과가 아니며, 용량·파일명·앱명·분석 시간도 모두 예시입니다.

| 파일 | 표시 내용 |
| --- | --- |
| [storage-overview.png](storage-overview.png) | 기기 저장공간 요약과 접근 권한 안내 |
| [app-storage.png](app-storage.png) | 앱별 용량 트리맵과 코드·데이터·캐시 구성 |
| [file-treemap.png](file-treemap.png) | 선택한 폴더의 용량 지도와 파일 유형별 구성 |

## 제작 조건

- 제작일: 2026-11-15
- UI 기준: [v0.4.0 소스, 커밋 57b4da7](https://github.com/Uree1229/DiskPie/tree/57b4da707262c00339b9ec6d639f4f0e9ad89569)
- 화면: `DiskPieScreen`과 `DiskPieTheme`, 한국어, API 36 미리 보기
- 렌더링: Android Compose Preview Screenshot Testing 플러그인 `0.0.1-alpha16`
- 뷰포트: 412 × 915 dp, PNG 1082 × 2402 px
- 데이터: 총 128 GiB / 여유 42 GiB인 기기, 앱 6개 26.5 GiB, 파일 10개 16 GiB의 합성 상태

원본 저장소와 별도의 임시 사본에서 화면 상태를 주입하고 초기 스크롤 위치만 조정했습니다. 제품의 UI 구성과 기능은 변경하지 않았습니다. 파일을 읽거나 삭제하지 않았고, 설치된 앱 목록이나 실제 사용자의 데이터를 수집하지 않았습니다.

앱 통계와 파일 분석은 집계 범위가 겹칠 수 있으므로 화면의 두 수치를 합산하지 않습니다. 예시의 분석 시간은 성능 측정 결과가 아닙니다.

렌더링 방법은 [Android 공식 Compose 스크린샷 테스트 문서](https://developer.android.com/studio/preview/compose-screenshot-testing)를 참고했습니다.
