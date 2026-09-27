# 설정 가이드

## 1. 모델 파일 준비

### 1.1 Python 저장소에서 LSTM 모델 변환

원본 저장소 https://github.com/lhchan1/korean-fingerspelling-recognition 에서:

```bash
# Python 환경 설정
cd korean-fingerspelling-recognition
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# LSTM 모델이 training/lstm_v3/fingerspelling_lstm.keras에 있으면 다음 실행
python3 << 'EOF'
import tensorflow as tf

# Keras 모델 로드
model = tf.keras.models.load_model('training/lstm_v3/fingerspelling_lstm.keras')

# TFLite 변환기 생성
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,
]

# 변환
tflite_model = converter.convert()

# 저장
with open('fingerspelling_lstm.tflite', 'wb') as f:
    f.write(tflite_model)
    
print("✓ Model conversion complete")
EOF
```

### 1.2 MediaPipe Hand Landmarker 모델 다운로드

```bash
# 이미 models/ 폴더에 있으면 사용, 없으면 다운로드
mkdir -p models
cd models

# MediaPipe Hand Landmarker (약 7.8MB)
curl -L \
  https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task \
  -o hand_landmarker.task

echo "✓ Models downloaded"
cd ..
```

### 1.3 라벨 파일 준비

```bash
# 기존 labels.txt 복사 또는 생성
cat > labels.txt << 'EOF'
ㄱ
ㄴ
ㄷ
ㄹ
ㅁ
ㅂ
ㅅ
ㅇ
NONE
EOF
```

## 2. Android 프로젝트에 모델 추가

### 2.1 assets 폴더 구조

```
korean-sign-language-android/
└── app/
    └── src/
        └── main/
            └── assets/
                ├── fingerspelling_lstm.tflite
                ├── hand_landmarker.task
                └── labels.txt
```

### 2.2 파일 복사

```bash
# Android 프로젝트 디렉토리에서
mkdir -p app/src/main/assets

# 변환한 모델 파일 복사
cp ../korean-fingerspelling-recognition/fingerspelling_lstm.tflite app/src/main/assets/
cp ../korean-fingerspelling-recognition/models/hand_landmarker.task app/src/main/assets/
cp ../korean-fingerspelling-recognition/labels.txt app/src/main/assets/
```

## 3. 큰 파일 처리 (선택사항)

파일이 100MB를 초과하면 Google Play에 배포할 수 없습니다.

### 3.1 모델 경량화 (권장)

```python
# Python에서 양자화 추가
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]

# 정수 양자화
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS_INT8,
]
converter.inference_input_type = tf.int8
converter.inference_output_type = tf.int8

tflite_model = converter.convert()
```

### 3.2 원격 다운로드 방식

`SignClassifier.kt` 수정:

```kotlin
// 온라인 저장소에서 다운로드
private suspend fun downloadModel() {
    val modelUrl = "https://your-server.com/fingerspelling_lstm.tflite"
    // 다운로드 로직
}
```

## 4. 빌드 및 테스트

```bash
# Android Studio 터미널 또는 명령줄
./gradlew build

# 에뮬레이터 또는 기기에서 실행
./gradlew installDebug
adb shell am start -n com.example.signlanguagerecognition/.MainActivity
```

## 5. 권한 확인

`AndroidManifest.xml`에 다음이 포함되어 있는지 확인:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
```

## 6. 문제 해결

### 6.1 모델 로드 실패
- assets 폴더에 파일이 제대로 복사되었는지 확인
- 파일명이 정확한지 확인 (대소문자 구분)

### 6.2 카메라 권한 거부
- 기기 설정에서 앱 권한 확인
- Android 6.0(API 23) 이상에서는 런타임 권한 필요

### 6.3 성능 문제
- 저사양 기기에서는 프레임 드롭 발생 가능
- `BUFFER_SIZE` 줄이기
- 모델 경량화 시도

## 7. 배포

### 7.1 앱 서명

```bash
keytool -genkey -v -keystore my-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias my-key-alias
```

### 7.2 릴리스 빌드

```bash
./gradlew assembleRelease
```

생성된 APK: `app/build/outputs/apk/release/app-release.apk`

## 참고 사항

- 최소 Android SDK: 24
- 타겟 Android SDK: 34
- 권장 기기: RAM 2GB 이상, 중급 이상 프로세서
