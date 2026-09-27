# 모델 파일 다운로드 가이드

## 📥 필수 파일

Android 앱을 실행하려면 다음 3개 파일이 필요합니다:

### 1️⃣ `fingerspelling_lstm.tflite` (LSTM 모델)
- **크기**: ~0.45MB (변환 후)
- **원본**: `training/lstm_v10_no_double/fingerspelling_lstm.keras`
- **소스**: https://github.com/lhchan1/korean-fingerspelling-recognition
- **생성 방법**: Python에서 변환 (아래 참고)

### 2️⃣ `hand_landmarker.task` (MediaPipe 모델)
- **크기**: ~7.8MB
- **원본**: `models/hand_landmarker.task`
- **소스**: https://github.com/lhchan1/korean-fingerspelling-recognition
- **또는** 공식 Google MediaPipe에서 다운로드

### 3️⃣ `labels.txt` (라벨 파일)
- **이미 포함됨**: `app/src/main/assets/labels.txt`
- 31개 한글 지문자 라벨 포함

---

## 🔄 모델 변환 방법

### 전제 조건
- Python 3.8 이상
- TensorFlow 설치

```bash
pip install tensorflow
```

### Step 1: 변환 스크립트 실행

```bash
# 저장소 루트에서
python convert_model.py /path/to/training/lstm_v10_no_double/fingerspelling_lstm.keras
```

또는 디렉토리 구조가 다른 경우:

```bash
python convert_model.py ./path/fingerspelling_lstm.keras
```

### Step 2: 출력 확인

성공 시 다음 메시지 표시:

```
Loading model from: training/lstm_v10_no_double/fingerspelling_lstm.keras
✓ Model loaded successfully
  Input shape: (None, 12, 63)
  Output shape: (None, 31)
Converting model...
✓ Model converted successfully
Saved to: fingerspelling_lstm.tflite
  File size: 0.45 MB
```

### Step 3: Android 프로젝트에 복사

```bash
# 변환된 모델 복사
cp fingerspelling_lstm.tflite app/src/main/assets/

# MediaPipe 모델 복사 (원본 repo에서)
cp /path/to/korean-fingerspelling-recognition/models/hand_landmarker.task app/src/main/assets/
```

최종 폴더 구조:

```
app/src/main/assets/
├── fingerspelling_lstm.tflite
├── hand_landmarker.task
└── labels.txt
```

---

## 📥 직접 다운로드

원본 저장소에서 직접 받기:

```bash
# 원본 저장소 클론
git clone https://github.com/lhchan1/korean-fingerspelling-recognition.git

# 필요한 파일 복사
cp korean-fingerspelling-recognition/training/lstm_v10_no_double/fingerspelling_lstm.keras ./
cp korean-fingerspelling-recognition/models/hand_landmarker.task ./

# 변환
python convert_model.py ./fingerspelling_lstm.keras

# Android 프로젝트에 복사
cp fingerspelling_lstm.tflite korean-sign-language-android/app/src/main/assets/
cp hand_landmarker.task korean-sign-language-android/app/src/main/assets/
```

---

## ⚙️ 고급 설정

### 모델 경량화 (양자화)

`convert_model.py` 수정:

```python
# 양자화 추가
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS_INT8,
]

# 크기를 ~0.2MB로 줄일 수 있음
```

### 원격 다운로드 (선택사항)

모델을 Firebase 또는 CDN에서 다운로드:

```kotlin
// app/src/main/java/com/example/signlanguagerecognition/ml/ModelDownloader.kt
class ModelDownloader {
    suspend fun downloadModel(): File {
        val url = "https://your-server.com/fingerspelling_lstm.tflite"
        // 다운로드 로직
    }
}
```

---

## ✅ 검증

모델이 제대로 복사되었는지 확인:

```bash
# 파일 크기 확인
ls -lh app/src/main/assets/

# 예상 결과:
# fingerspelling_lstm.tflite  (0.45M)
# hand_landmarker.task        (7.8M)
# labels.txt                  (0.2K)
```

Android Studio에서도 확인:

```
Project > app > src > main > assets
┣ fingerspelling_lstm.tflite
┣ hand_landmarker.task
┗ labels.txt
```

---

## 🆘 문제 해결

### "Model not found" 에러
- **원인**: assets 폴더에 파일이 없음
- **해결**: 위 단계 다시 실행, 파일명 대소문자 확인

### "Conversion failed" 에러
- **원인**: TensorFlow 버전 호환 문제
- **해결**: 최신 TensorFlow 설치
```bash
pip install --upgrade tensorflow
```

### "모델 로드 중 크래시"
- **원인**: 모델 입출력 형태 불일치
- **해결**: `SignClassifier.kt`의 입력 크기 확인 (756 = 12 * 63)

---

## 📊 모델 정보

| 파일 | 크기 | 용도 |
|------|------|------|
| fingerspelling_lstm.tflite | 0.45 MB | 31개 지문자 분류 |
| hand_landmarker.task | 7.8 MB | 손 관절 추출 |
| labels.txt | 0.2 KB | 라벨 맵핑 |
| **합계** | **8.25 MB** | **Android 앱 전체** |

---

## 다음 단계

1. ✅ 모델 파일 준비
2. ⏭️ [README.md](README.md) 참고해서 Android Studio에서 빌드
3. ⏭️ 에뮬레이터 또는 기기에서 실행

---

**최종 확인**: 모든 파일이 `app/src/main/assets/` 에 있는지 확인 후 빌드하세요!
