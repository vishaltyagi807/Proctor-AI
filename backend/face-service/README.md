# face-service

Reactive Spring Boot (WebFlux) service that detects faces in a submitted image, matches
them against enrolled users, and returns the image annotated with bounding boxes plus
the matched user data (or "not found" for unrecognized faces).

## Architecture

- **Detection**: [SCRFD](https://github.com/deepinsight/insightface/tree/master/detection/scrfd)
  run locally via [ONNX Runtime](https://onnxruntime.ai/) (`com.microsoft.onnxruntime:onnxruntime`).
- **Recognition**: [ArcFace](https://github.com/deepinsight/insightface/tree/master/recognition/arcface_torch)
  (`w600k_r50`), also via ONNX Runtime, producing a 512-d L2-normalized embedding per face.
- **Enrolled-face index**: Spring AI's `VectorStore` backed by `PgVectorStore` (pgvector on the
  same Postgres instance), used for storing and cosine-similarity-searching embeddings. Since
  Spring AI's `VectorStore` is built around *text* embeddings, this service plugs in a small
  `PassthroughEmbeddingModel` that serializes/deserializes the ONNX-computed `float[512]`
  embedding as the document's "text" instead of calling a real embedding API — this is what lets
  Spring AI's vector-store abstraction genuinely do the storage/search work here.
- **Authorization**: gated by a new `face_recognition` permission entity (`read` = recognize,
  `write`/`update`/`delete` = enroll/manage), checked explicitly against the same `can_access(...)`
  SQL function the rest of the platform's RLS policies use. Only `system_admin` and `admin` hold it
  by default; grant it to other roles from the Roles & access screen like any other permission. The
  vector index table itself (`face_embeddings`, auto-created by Spring AI on first boot) is a
  JDBC-only, service-internal table and is not RLS-scoped per row — access control happens at the
  API layer instead, by passing the target user's id as the row context into `can_access(...)`.
  - `read` (recognize) only has an `all`-scope permission — anyone granted it can submit a photo to
    be matched. Per-match visibility of *who* it resolves to is still governed by the caller's
    `users` table read scope (an out-of-scope match falls back to "not matched" instead of leaking
    the identity), so recognize doesn't need its own own/department distinction.
  - `write`/`update`/`delete` (enroll, delete-enrollment) support all three scopes, matching how
    `users` is scoped elsewhere in the platform:
    - `own` — a user can only enroll/remove **themselves** (`userId` in the URL must equal their
      own id).
    - `department` — can enroll/remove anyone who shares at least one department with the caller.
    - `all` — can enroll/remove any user.

  In other words: `all` lets someone manage any user (the web UI groups the picker by department
  for this tier); `department` restricts management to people who share a department with the
  caller; `own`-only means the caller can only ever enroll themselves.

- **Self-enrollment lock**: enrolling is cheap to abuse if a user can keep overwriting their own
  face indefinitely, so a self-service enroll (one that only succeeded via the caller's `own`
  scope on `face_recognition:write` — checked via `has_scope(..., array['department','all'], ...)`
  to tell privileged from self-service calls) is capped at `FACE_SELF_ENROLL_LIMIT` (default `1`)
  attempts, tracked per user in the enrollment's `selfEnrollCount` metadata. Once the limit is hit,
  further self-enrolls are rejected with a 403 until someone holding `department`/`all` scope
  re-enrolls that user — which always succeeds regardless of the counter and resets it back to `0`,
  giving the user a fresh self-enroll cycle. `GET /faces/enrollments` reports `selfEnrollCount` and
  `selfEnrollLocked` per user so the UI can show lock state.

## Required model files (not bundled)

Download these two ONNX files from InsightFace's public **buffalo_l** model pack and place them
where `FACE_DETECTION_MODEL_PATH` / `FACE_EMBEDDING_MODEL_PATH` point (default: `./models/`,
relative to the service's working directory):

| File (as shipped in buffalo_l) | Purpose |
|---|---|
| `det_10g.onnx` | SCRFD-10G face detection + 5-point landmarks |
| `w600k_r50.onnx` | ArcFace 512-d face embedding |

Easiest way to get them:

```bash
pip install insightface
python -c "from insightface.app import FaceAnalysis; FaceAnalysis(name='buffalo_l').prepare(ctx_id=-1)"
```

This downloads and unzips the pack to `~/.insightface/models/buffalo_l/`. Copy (or point the env
vars directly at) `det_10g.onnx` and `w600k_r50.onnx` from there.

Manual alternative — download `https://github.com/deepinsight/insightface/releases/download/v0.7/buffalo_l.zip`
directly and unzip it (mirror if needed: `https://sourceforge.net/projects/insightface.mirror/files/v0.7/buffalo_l.zip/download`).

Both files together are roughly 190MB.

### Expected model contracts

The Java code assumes:

- **SCRFD**: single input `[1, 3, H, W]` (H=W=`FACE_DETECTION_INPUT_SIZE`, default 640), RGB,
  normalized as `(pixel - 127.5) / 128`. Nine outputs in declaration order: scores for strides
  `[8, 16, 32]`, then bbox distance predictions for the same three strides, then 5-point keypoint
  distance predictions for the same three strides (this is the standard SCRFD "bnkps" export
  layout). If you use a different SCRFD variant (different stride count, no keypoints), you will
  need to adjust `ScrfdFaceDetector`.
- **ArcFace**: single input `[1, 3, 112, 112]`, RGB, normalized as `(pixel - 127.5) / 127.5`,
  single output `[1, 512]`.

If detections look wrong once you plug in a model (boxes in the wrong place, no faces found),
the most likely mismatch is the stride list, the number of outputs, or the RGB/BGR channel order
for that specific export — check those first.

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `FACE_SERVER_PORT` | `7059` | HTTP port |
| `FACE_DETECTION_MODEL_PATH` | `./models/det_10g.onnx` | Path to the detector ONNX file |
| `FACE_EMBEDDING_MODEL_PATH` | `./models/w600k_r50.onnx` | Path to the recognizer ONNX file |
| `FACE_DETECTION_INPUT_SIZE` | `640` | Square input size fed to the detector |
| `FACE_DETECTION_SCORE_THRESHOLD` | `0.5` | Minimum detector confidence to keep a box |
| `FACE_DETECTION_NMS_THRESHOLD` | `0.4` | IoU threshold for non-max suppression |
| `FACE_MATCH_THRESHOLD` | `0.45` | Minimum cosine similarity to call a face "matched" |
| `FACE_MAX_IMAGE_BYTES` | `8388608` (8MB) | Rejects larger uploads |
| `FACE_EMBEDDING_DIMENSIONS` | `512` | Must match the recognizer's output size |
| `FACE_SELF_ENROLL_LIMIT` | `1` | How many times a user can enroll/re-enroll *themselves* before being locked out (a `department`/`all`-scope re-enroll always works and resets this) |

The detector/embedder ONNX sessions load lazily on first use, so the service still starts and
answers `/actuator/health` even before the model files are in place — only `/faces/enroll/**` and
`/faces/recognize` will fail (with a clear error) until they're supplied.

## API

All endpoints are behind the API gateway at `/faces/**` and require the same JWT bearer auth as
every other service.

- `POST /faces/enroll/{userId}` — `{ "image": "<base64>" }` → detects the largest face, aligns it,
  stores its embedding against that user, and returns
  `{ "userId", "name", "email", "enrolledAt", "selfEnrollCount", "selfEnrollLocked" }`. Requires
  `face_recognition:write`; self-enrolling beyond `FACE_SELF_ENROLL_LIMIT` returns 403 (see above).
- `POST /faces/recognize` — `{ "image": "<base64>" }` → detects every face, matches each against
  enrolled users, and returns:
  ```json
  {
    "image": "<base64 PNG, boxes drawn: each matched user gets their own stable color (derived from their user ID, so the same person is always the same color across images) with their name; unmatched faces are red+\"Unknown\">",
    "faces": [
      { "box": {"x":10,"y":20,"width":80,"height":90}, "confidence": 0.87, "matched": true,
        "user": {"id":"...","name":"Aarav Mehta","email":"..."} },
      { "box": {"x":200,"y":40,"width":75,"height":85}, "confidence": 0.0, "matched": false,
        "user": null }
    ]
  }
  ```
  Requires `face_recognition:read`.
- `GET /faces/enrollments` — lists enrolled users. Requires `face_recognition:read`.
- `DELETE /faces/enrollments/{userId}` — removes an enrollment. Requires `face_recognition:write`.

## Local infra changes this feature required

- Postgres image switched from `postgres:16` to `pgvector/pgvector:pg16` (drop-in compatible,
  just adds the `vector` extension's shared library).
- `vector` extension is created by the privileged init role in `init/02_extensions.sql` (a
  non-superuser `proctor` role cannot `CREATE EXTENSION` even with `IF NOT EXISTS` unless it's
  already installed).
- `proctor` was granted `CREATE` on the `public` schema so Spring AI can create/own its
  `face_embeddings` table on first boot (`spring.ai.vectorstore.pgvector.initialize-schema: true`).
