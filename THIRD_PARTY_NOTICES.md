# Third-Party Notices

## PHASEN

This Android project uses a PHASEN-family speech enhancement model for an experimental on-device ONNX Runtime demo.

- Upstream project: https://github.com/huyanxin/phasen
- PHASEN architecture and upstream implementation are not original work of this Android project.
- The bundled model file `app/src/main/assets/model_int8.onnx` was produced from a local PHASEN checkpoint, exported to ONNX, and converted with dynamic INT8 quantization.
- The bundled model is provided only to demonstrate Android-side ONNX Runtime inference. Its results do not represent best possible performance or official PHASEN performance.

The exact upstream commit or archive revision and license terms for the local PHASEN source used during conversion were not confirmed from files in this Android repository. No license name or authorization terms are asserted here.
