# PHASEN Android Speech Enhancement

An Android application for offline microphone recording and speech enhancement using a quantized PHASEN ONNX model.

PHASEN Android Speech Enhancement is a single-module Android app for offline audio testing. It records microphone audio, lists saved recordings, plays recordings back, and can run an experimental ONNX model on recorded WAV files with ONNX Runtime for Android.

## Features

- Record 16 kHz mono PCM WAV audio from the microphone.
- Save recordings in the app-specific external files directory.
- Play, stop, and delete saved recordings.
- Run an experimental local ONNX model with ONNX Runtime for Android.

## Project Structure

```text
app/src/main/java/com/example/headphonecontroller/  Kotlin source
app/src/main/res/                                  Android resources
app/src/main/assets/                               Bundled ONNX demo model
app/src/test/                                      Local JVM tests
gradle/                                           Gradle wrapper files
```

## Requirements

- Android Studio or a compatible Android SDK installation
- JDK 17 or the JDK bundled with Android Studio
- Android Gradle Plugin 8.3.0
- Kotlin Android plugin 1.9.22
- Android SDK 34

Python is not required for building or running the Android app. This repository does not currently include model training, ONNX export, or INT8 quantization code.

## Build and Test

Run commands from the repository root:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

To install on a connected Android device or emulator:

```powershell
.\gradlew.bat installDebug
```

## ONNX Demo Model

This repository includes the current experimental demo model:

```text
app/src/main/assets/model_int8.onnx
```

The model is bundled so the app can demonstrate Android-side ONNX Runtime inference without requiring a separate model download. Its audio enhancement quality is not intended to be state of the art or production-ready; it is mainly for showing the end-to-end Android inference flow.

PHASEN architecture and the upstream PHASEN implementation are not original work of this Android project. The upstream source is:

```text
https://github.com/huyanxin/phasen
```

The bundled `model_int8.onnx` was produced from a local PHASEN checkpoint, exported to ONNX, then converted with dynamic INT8 quantization. It is an experimental demo model for Android ONNX Runtime integration only. Its behavior should not be treated as best achievable performance, production audio quality, or official PHASEN performance.

Current Android inference contract:

- Model file: `app/src/main/assets/model_int8.onnx`
- Runtime: ONNX Runtime for Android
- Execution mode: local on-device inference, loaded from Android assets
- Input audio: 16 kHz mono PCM WAV recorded by the app
- Tensor input name: `input_wav`
- Tensor input shape: `[1, 1, N]`, where `N` is the number of audio samples
- Tensor input type: `float32`, normalized to `[-1.0, 1.0]`
- Tensor output name: `est_wav`
- Tensor output type: `float32` audio samples, written back as 16-bit PCM WAV
- Output file pattern: original recording name plus `_enhanced.wav`

You can train your own model outside this repository and replace:

```text
app/src/main/assets/model_int8.onnx
```

The replacement model must keep the same input/output names and tensor format unless you also update `AudioEnhancer.kt`.

## Model Provenance

Confirmed from local files:

- Source model family: PHASEN. The Android model is not CMGAN.
- Android asset: `app/src/main/assets/model_int8.onnx`
- Android asset SHA-256: `DE232AD6B2430031484B31DBD8DA42E6CF4F02F0CBC3E41E760BEA0AD42095D8`
- The Android asset is byte-identical to the local PHASEN workspace file `model_int8.onnx`.
- Source checkpoint in the local PHASEN workspace: `checkpoints/best_model.pt`
- Source checkpoint SHA-256: `9E6C30AC78625A4E98EEE8ACD02D1DBDE68D70B43ECAF020F5A5C6B8911C9284`
- Checkpoint metadata: dictionary keys `best_pesq`, `epoch`, `model`, and `optimizer`; `epoch=49`; `best_pesq=2.407308952725349`.
- Training report identifies `checkpoints/best_model.pt` as the best-PESQ model from epoch 49.
- Training report lists AiShell-1 as the dataset source, with 500 train utterances, 100 validation utterances, 2-second segments, 977 train pairs, and 201 validation pairs.
- Training report lists PHASEN parameters as `win_len=400`, `win_inc=100`, `fft_len=512`, `win_type=hanning`.
- Training report lists Adam with `lr=1e-3` and `weight_decay=1e-5`, batch size 8, 50 epochs, and Mix loss (`0.5` amplitude plus `0.5` phase).

Confirmed export flow from local `export_onnx.py`:

- Model class: `PHASEN` from `model.phasen`.
- Default checkpoint: `checkpoints/best_model.pt`.
- Default ONNX output: `model.onnx`.
- Dummy input: `torch.randn(1, 1, 16000)`.
- ONNX opset: 17.
- Input name: `input_wav`.
- Output names: `est_spec`, `est_wav`.
- Dynamic axes:
  - `input_wav`: axis `0` = `batch`, axis `2` = `time`
  - `est_spec`: axis `0` = `batch`, axis `2` = `frames`
  - `est_wav`: axis `0` = `batch`, axis `1` = `time_out`
- Constant folding is enabled.
- The script can optionally verify ONNX Runtime `est_wav` output against PyTorch output.

Confirmed INT8 quantization flow from local `convert_int8.py`:

- Tool: `onnxruntime.quantization.quantize_dynamic`.
- Weight type: `QuantType.QInt8`.
- Default input: `model.onnx`.
- Default output: `model_int8.onnx`.
- The script first runs `onnx.shape_inference.infer_shapes`, writes an inferred temporary ONNX file, and overrides the temporary directory to the local workspace to avoid Windows non-ASCII path issues.
- The quantized model reports ONNX producer `onnx.quantize` version `0.1.0`.

Confirmed ONNX graph metadata for the bundled `model_int8.onnx`:

- IR version: 8.
- Opset imports: default domain opset 17.
- Input: `input_wav`, FLOAT, shape `[batch, 1, time]`.
- Outputs:
  - `est_spec`, FLOAT, shape `[batch, 514, frames]`
  - `est_wav`, FLOAT, shape `[batch, time_out]`
- Node types include `ConvInteger`, `MatMulInteger`, `DynamicQuantizeLinear`, `GRU`, `ConvTranspose`, and standard tensor ops.
- The graph contains exported STFT and ISTFT subgraphs. `input_wav` feeds the exported STFT/PHASEN graph; `est_spec` feeds the exported ISTFT path; `est_wav` is produced by the final squeeze after ISTFT normalization. This confirms the ONNX model contains a complete waveform-to-waveform path for `input_wav -> est_wav`.

Reliable reproduction status:

- The local PHASEN workspace contains `prepare_data.py`, `train.py`, `export_onnx.py`, `convert_int8.py`, checkpoints, `model.onnx`, `model_int8.onnx`, and `training_report.txt`.
- A reliable training/export/quantization process can be reconstructed from those local PHASEN workspace files.
- This Android repository does not currently include those Python training, ONNX export, or INT8 quantization scripts. Reproducing the model from this repository alone is therefore not confirmed.

Unconfirmed:

- The exact upstream commit or archive revision of the PHASEN source is not confirmed from the Android repository.
- The exact command history used to create the final Android asset is not recorded in this repository.
- The exact versions of Python, PyTorch, ONNX, and ONNX Runtime used during the original export and quantization are not fully confirmed from this repository.

## Privacy and Repository Hygiene

The repository is prepared for public GitHub upload with local IDE state, build outputs, signing keys, datasets, model weights, caches, and local SDK paths excluded by `.gitignore`.

Do not commit:

- `local.properties`
- `.idea/` or `.claude/`
- `app/build/`, `.gradle/`, or other build outputs
- signing keys or credential files
- datasets, checkpoints, training outputs, and model weights other than `app/src/main/assets/model_int8.onnx`

## Notes

This project requests microphone permission for offline audio recording. Bluetooth-related code is currently scaffolded for future headset features, but the manifest only declares microphone recording permission.
